# 17 — Sync and Key Management

**Status:** Implementation contract. Fulfils the open decision recorded in `00-current-state-review.md` §3 item 3 (key recovery), which had never been made.
**Depends on:** `01` ADR-001, `02` (schema and envelope), `04` §5–6 (crypto and RLS), `06` (test gates).
**Interface already available:** `SyncAdapter` exists in `core/domain/.../Repositories.kt` and is currently implemented by `NoOpSyncAdapter` returning `"Sync is not enabled in v1."`. The interface does not change; only its implementation does.

## 1. Scope and sequencing

Sync is in scope. It ships **after** the local algorithm, design system, screens and notifications are stable, and only once every gate in §15 passes.

This ordering is not negotiable. Sync adds authentication, key management, a network dependency, conflict resolution and a second failure mode. Landing it first would mean debugging two problems at once, and would delay the visible improvements that matter most.

If sync slips, the app must remain complete and fully functional without it. Local-only is a valid shipping state, not a degraded one.

## 2. The decision in one paragraph

Room remains the operational source of truth on every device. Supabase is an optional transport and encrypted backup replica, never an authority. Health payloads are encrypted on the device before they leave it. The server cannot read them. The consequence — which must be stated to users in plain language at the point of opt-in — is that losing the vault key means losing the data permanently, with no server-side recovery possible.

## 3. Preconditions

Sync must not ship until all of these are true:

1. The v3 Room schema with audit columns, tombstones and an outbox exists and its migration is tested (`02` delta).
2. The encrypted envelope and key lifecycle below are implemented and tested.
3. Two-user RLS isolation tests pass against a non-production Supabase project.
4. Account deletion is verified against real remote rows, not merely a UI affordance.
5. The privacy policy and Play Data safety declarations are updated to describe sync accurately.
6. The local-first invariants in §12 are proven by test.

## 4. Environment and migration control

- Three isolated Supabase projects: **development**, **staging**, **production**. Credentials strictly separated; staging is the only environment used for automated tests.
- All schema changes via version-controlled SQL migrations applied through CI. **No dashboard-only changes.** A change made by hand in the dashboard is an incident.
- **Never** run destructive tests against production.
- The client uses only the publishable/anon key. The `service_role` key must never appear in the Android binary, remote config, CI artefacts or logs (`04` §6). Add a CI secret scan that fails the build on detection.
- Run Supabase Security Advisor before each production release and track findings to zero.

## 5. Authentication

Email + password is acceptable; **email magic link is preferred** because it eliminates password storage and reset flows entirely, which is a meaningful reduction in credential-handling risk for a health app.

- Rate-limit and monitor auth attempts; configure allowed redirects and deep links carefully.
- Session lifetime is bounded; refresh handled transparently.
- **Session expiry must never discard local data.** The app continues working locally and queues changes. Losing a session means losing sync, not losing records.
- Sign-out clears the auth session and cached credentials but **does not** delete local records. Account deletion is a separate, explicit, confirmed action (§11).

## 6. Vault key lifecycle

### 6.1 Two-tier wrapping

Android Keystore keys are non-exportable and device-bound, so a single key cannot support a second device. The design therefore maintains one vault key with two independent wrappings.

```kotlin
interface VaultKeyStore {
    suspend fun generate(): VaultKey                  // 256-bit, CSPRNG
    suspend fun deviceWrappedKey(): ByteArray         // under a Keystore-held key
    suspend fun recoveryWrappedKey(passphrase: CharArray): ByteArray
    suspend fun unwrapWithDevice(): VaultKey
    suspend fun unwrapWithPassphrase(passphrase: CharArray): VaultKey
}
```

| Wrapping | Protects | Lifetime |
|---|---|---|
| **Device** — vault key encrypted under a Keystore key, optionally bound to user authentication (biometric/device credential) | Silent use on the enrolled device without re-entering a passphrase | Device-local; dies with the device |
| **Recovery** — vault key encrypted under a key derived from the user's passphrase | Migration to a new device, and Keystore invalidation (device reset, OS reinstall, biometric enrolment change) | Portable; the user's responsibility |

Both wrappings protect the **same** vault key. Losing one wrapping is an inconvenience; losing both is data loss.

### 6.2 Passphrase derivation

| Property | Value |
|---|---|
| KDF | Argon2id |
| Salt | 16 random bytes per vault, stored with the wrapped key |
| Memory cost | 64 MiB (tuned down only if a low-end-device benchmark demands it) |
| Iterations | 3 |
| Parallelism | 2 |
| Output length | 32 bytes |
| Passphrase | Minimum 12 characters; rejected with a specific, helpful message if weaker |

**Never derive the vault key directly from a user PIN.** A four-digit PIN has 10,000 possibilities; even Argon2id cannot make that safe. The app lock PIN is a convenience gate and is cryptographically separate from the vault key.

### 6.3 What the app lock does and does not protect

Stated precisely, because the current app overstates this:

- **App lock** prevents casual access to the UI. It is a screen gate.
- **Device wrapping** protects the vault key at rest in app storage.
- **Database encryption at rest** is a separate control and is currently absent — there is no SQLCipher in the project. Adding it requires validating key lifecycle, migration behaviour, backup interaction and performance before adoption (`04` §4).
- **Remote ciphertext** is protected by the vault key, which the server never holds.

These must never be conflated in copy or in documentation.

### 6.4 Rotation

`key_version` is stored per envelope. Rotation re-wraps the vault key under new wrapping material and re-encrypts records lazily on next write. Rotation must be tested for: interrupted rotation, rotation with a pending outbox, and rotation after device loss.

## 7. Envelope format and encryption

```kotlin
data class SyncEnvelope(
    val schemaVersion: Int,      // payload shape version
    val keyVersion: Int,         // vault key version
    val nonce: ByteArray,        // 12 bytes, unique per encryption
    val ciphertext: ByteArray,   // AES-GCM ciphertext with tag
)
```

| Property | Value |
|---|---|
| Algorithm | AES-256-GCM (authenticated) |
| Nonce | 96 bits, cryptographically random, **never reused under the same key** |
| AAD | `record_id`, `schema_version`, `key_version` — bound so an envelope cannot be replayed onto a different record or a downgraded version |

AAD binding is what prevents an attacker with database access from swapping ciphertext between records or downgrading a payload version to exploit an older parser.

**Payload versioning.** The encrypted payload is versioned independently of the SQL schema. An unknown or future `schema_version` must **fail closed** — skip the record, surface a user-visible "some records from another device couldn't be read", and never partially parse it. A wrong key must never be reported as "you have no data", which would be worse than an error.

**Implementation rule:** use a vetted library for the AEAD construction and the KDF. Do not hand-roll cryptographic primitives.

## 8. Remote schema and RLS

```sql
create table public.vault_sync_records (
    user_id        uuid        not null references auth.users(id) on delete cascade,
    record_id      uuid        not null,
    schema_version smallint    not null,
    key_version    smallint    not null,
    nonce          bytea       not null,
    ciphertext     bytea       not null,
    revision       bigint      not null default 1,
    updated_at     timestamptz not null default now(),
    deleted_at     timestamptz,
    primary key (user_id, record_id)
);

create index vault_sync_records_user_updated_idx
    on public.vault_sync_records (user_id, updated_at desc);

alter table public.vault_sync_records enable row level security;

create policy own_records_select on public.vault_sync_records
    for select to authenticated using ((select auth.uid()) = user_id);
create policy own_records_insert on public.vault_sync_records
    for insert to authenticated with check ((select auth.uid()) = user_id);
create policy own_records_update on public.vault_sync_records
    for update to authenticated
    using ((select auth.uid()) = user_id)
    with check ((select auth.uid()) = user_id);
create policy own_records_delete on public.vault_sync_records
    for delete to authenticated using ((select auth.uid()) = user_id);
```

Notes that matter:

- `(select auth.uid())` is wrapped deliberately — the bare call is re-evaluated per row and is a documented Supabase performance footgun.
- RLS is **authorization, not confidentiality**. It prevents cross-user access; it does not hide plaintext from a database operator, which is exactly why the payload is encrypted client-side.
- Verify **grants** as well as policies — a policy does not revoke an existing grant.
- Revoke all access from `anon`.

## 9. What the server can still see

Disclose this plainly at opt-in. The server observes:

- That an account exists and has records.
- Record count, record IDs (random UUIDs, not sequential).
- Approximate payload sizes.
- Sync timing — when the device last connected.
- IP address and device characteristics of the API call.

It cannot see cycle dates, symptoms, medication, notes or any other field value. Encryption limits content visibility; it does not eliminate metadata. The opt-in screen must say both halves of this.

## 10. Sync mechanics

### 10.1 Outbox

Every syncable mutation writes an outbox row **in the same Room transaction** as the local change. If the transaction commits, the change is queued; if it rolls back, nothing is queued. There is no window in which a record exists locally but will never sync.

```kotlin
enum class OutboxState { PENDING, IN_FLIGHT, SYNCED, RETRYABLE_ERROR, NEEDS_USER_RESOLUTION }
```

A mutation is marked `SYNCED` only after the server confirms the write. Retrying the same mutation is idempotent: `(user_id, record_id)` is the primary key and the write is an upsert guarded by revision.

### 10.2 Conflicts

Blind last-write-wins erases a user's edit from another device. Not acceptable.

| Case | Policy |
|---|---|
| Different records | Merge by UUID. No conflict. |
| Same record, one deletion | **Deletion wins**, via tombstone with a higher revision |
| Same record, both edited | Revision comparison; on divergence → `NEEDS_USER_RESOLUTION` |
| Cycle start or end date edited on both devices | **Always** `NEEDS_USER_RESOLUTION` — never silently combine date fields |
| Symptom tag set edited on both devices | Merge as a set **only if** deletions are represented explicitly; otherwise surface |

Server `updated_at` is a synchronisation aid and must never be treated as the user's event date.

### 10.3 Stale clients and resurrection

A tombstone must beat a stale in-flight update, or a deleted record reappears on a device that was offline. Enforce this server-side by rejecting any upsert whose revision is lower than the stored revision, and test it explicitly with a device that has been offline across a deletion.

### 10.4 Sync status UI

Five states, never conflated with local save state: **Sync off · Waiting for connection · Syncing · Up to date · Action required**, with last successful sync time. A successful local save must always be presented as saved regardless of sync outcome.

## 11. Deletion and account lifecycle

Four distinct operations, each separately testable, each with a visible outcome:

| Operation | Effect |
|---|---|
| **Delete local data** | Clears local database, app-owned files and caches; cancels scheduled work; destroys local key material. Leaves the remote account untouched. |
| **Disable sync** | Stops future uploads. Explains whether existing remote ciphertext remains. Local data untouched. |
| **Delete cloud data** | Deletes all rows for the user. Local data retained, so a re-enable re-uploads it. |
| **Delete account** | Deletes remote rows, then the auth identity, then all local data and keys, in that order. |

Each reports real success or failure. **A partial failure must remain visible and retryable** — never report success for a step that did not complete.

**Backup caveat:** deletion from the live database does not mean immediate removal from every provider backup. Confirm Supabase's actual retention model and disclose it accurately rather than claiming erasure.

**Lost key:** state plainly that encrypted remote data becomes permanently unrecoverable. Never silently generate a replacement key and imply old data is readable.

## 12. Local-first invariants

These hold regardless of sync, and are proven by test:

1. The app is fully usable with no account and no network.
2. No logging, calendar or insight flow ever blocks on network state.
3. Sync failure never blocks or fails a local write.
4. Disabling sync never loses local data.
5. Session expiry never loses local data.
6. Process death never loses an outbox entry.

## 13. Testing plan

| Layer | Coverage |
|---|---|
| **Crypto unit** | Known-answer tests from the vetted library; encrypt/decrypt round-trip; AAD binding rejects tampered record ID or version; wrong key fails closed; nonce uniqueness across 10,000 encryptions |
| **KDF** | Passphrase verification; wrong passphrase rejected; cost parameter change does not break existing wrapped keys; weak passphrase rejected |
| **Envelope** | Payload version mismatch rejected without partial parse; unknown version skipped, not guessed |
| **Outbox** | Written in the same transaction as the mutation; survives process death; marked SYNCED only on server confirmation; retry is idempotent |
| **Conflict** | Per the §10.2 table; concurrent same-field edits on two devices; date edits always surface |
| **Tombstone** | Stale client cannot resurrect a deleted record; deletion beats stale update |
| **RLS** | **Two-user isolation across SELECT, INSERT, UPDATE, DELETE**; unauthenticated access denied; anon role denied; verified against the staging project on every CI run |
| **Account deletion** | Remote rows gone, identity revoked, local data and keys cleared; partial failure visible and retryable |
| **Invariant** | All six in §12, each as an explicit test |
| **Manual** | Fresh device sign-up, second-device restore via passphrase, Keystore invalidation after OS update, device-loss recovery |

The two-user RLS test is the single most important test in this document. It runs against staging in CI, never production.

## 14. Release gates

Sync does not ship unless all of these hold:

1. No plaintext health field exists anywhere in the remote path — verified by inspecting actual stored rows, not by reading the code.
2. Two-user RLS isolation passes for every operation.
3. No `service_role` credential exists in the APK or the repository; secret scanning runs in CI.
4. Key generation, wrapping, unwrapping, recovery and loss are all tested, including Keystore invalidation.
5. Loss-of-key consequence is stated in-app before opt-in, in language a non-technical user understands.
6. All six local-first invariants pass.
7. Account deletion is verified against real remote rows.
8. Metadata disclosure (§9) is shown at opt-in and matches observed server behaviour.
9. The privacy policy and Play Data safety forms describe sync accurately.
10. Deactivation is fully functional and the app is complete without sync.

## 15. What must never be claimed

- "End-to-end encrypted" without noting that metadata remains visible.
- "Zero data leaves your device" — untrue with sync enabled, and untrue for ad data regardless (`16` §4).
- "Recoverable" without stating the recovery conditions.
- "Secure against a compromised device or forensic access" — out of scope, as `04` §3 states.
- Any numerical security rating.