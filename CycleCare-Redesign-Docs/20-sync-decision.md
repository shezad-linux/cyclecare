# Decision: cloud sync is deferred

**Decision.** Cloud synchronisation is not implemented. CycleCare ships
local-first.

**Date.** 20 October 2026.

## Why

Spec `08` §8 describes a full sync design: Supabase projects, a vault key
lifecycle, envelope encryption with AAD binding, a passphrase recovery
flow, an outbox with tombstones and per-type merge, RLS with two-user
isolation tests, four distinct deletion operations and a sync status
surface. The roadmap itself calls that work deferrable, on the grounds
that the app is complete and shippable without it and that it must not
delay the user-visible improvements in phases 4 to 7.

That reasoning still holds. The work in phases 4 to 7 is the work that
changes what the product does for the person using it; sync changes where
the bytes live. Doing sync first would delay the part that matters for
far longer than it would accelerate the part that does not.

## What ships instead

The app is local-first and the data stays on the device:

- Cycle records, symptom logs, profile and reminder rules are held in a
  local Room database, `cyclecare.db`.
- Cloud backup is excluded in full by `data_extraction_rules.xml`, so
  nothing is written to Google's cloud backup.
- Device-to-device transfer is enabled on Android 12 and above, so
  replacing a phone does not lose cycle history.
- Where a user wants to move data themselves, the encrypted export and
  import in `core/security` does it: a passphrase-derived key, AES-GCM
  with AAD binding to the record id, schema version and key version, and
  a fail-closed import that verifies the payload before it writes.

So there is a supported path between two devices, and it is one the user
controls with a passphrase they choose, rather than a server that holds
the key.

## What is not true

Sync is not implemented, and this decision does not paper over that.
`SyncAdapter` has one implementation, `NoOpSyncAdapter`, which returns
"Sync is not enabled in v1." There is no outbox, no merge policy, no
conflict resolution, no tombstone handling across devices, no RLS, no
two-user isolation, and no sync status surface. The schema has the
outbox and tombstone tables ready, but nothing writes to them.

The cost is real: a user with two phones has no shared view, and there
is no way to recover a lost phone's history except from a backup they
made. The export and import path covers a deliberate transfer but not
continuous multi-device use.

## If it is taken up later

The design in `17` is the starting point, and it should be read before
any code is written. Three things in particular are not optional:

- The key lifecycle. A vault key that the server cannot read, with the
  passphrase held by the user, is what makes the local-first claim
  survive the introduction of a server.
- The recovery flow. The consequence of losing the passphrase has to be
  stated plainly at the point of opting in, not discovered afterwards.
- The two-user isolation tests, against staging, in CI. A sync feature
  that leaks one user's cycle history to another is worse than no sync
  at all, and it is the failure that is hardest to notice.
