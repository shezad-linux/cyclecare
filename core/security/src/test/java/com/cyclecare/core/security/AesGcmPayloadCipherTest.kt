package com.cyclecare.core.security

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test

class AesGcmPayloadCipherTest {

    private val cipher = AesGcmPayloadCipher()
    private val keyDeriver = Pbkdf2PassphraseKeyDeriver()
    private val testKey = VaultKey(ByteArray(VAULT_KEY_BYTES) { 0x42 })

    @Test
    fun roundTripEncryptionDecryptionSucceeds() = runBlocking {
        val plaintext = "Sensitive health record with symptoms and notes".toByteArray(Charsets.UTF_8)
        val envelope = cipher.encrypt(
            plaintext = plaintext,
            recordId = "record-123",
            schemaVersion = 1,
            keyVersion = 1,
            key = testKey,
        )

        assertThat(envelope.nonce).hasLength(GCM_NONCE_BYTES)
        assertThat(envelope.ciphertext).isNotEqualTo(plaintext)

        val decrypted = cipher.decrypt(
            envelope = envelope,
            recordId = "record-123",
            key = testKey,
        ).getOrThrow()

        assertThat(decrypted).isEqualTo(plaintext)
        assertThat(String(decrypted, Charsets.UTF_8)).isEqualTo("Sensitive health record with symptoms and notes")
    }

    @Test
    fun aadRejectsTamperedRecordId() = runBlocking {
        val plaintext = "Cycle start date 2026-10-18".toByteArray(Charsets.UTF_8)
        val envelope = cipher.encrypt(
            plaintext = plaintext,
            recordId = "record-user-a",
            schemaVersion = 1,
            keyVersion = 1,
            key = testKey,
        )

        val result = cipher.decrypt(
            envelope = envelope,
            recordId = "record-user-b", // Tampered record identity
            key = testKey,
        )

        assertThat(result.isFailure).isTrue()
        val exception = result.exceptionOrNull()
        assertThat(exception).isInstanceOf(VaultException::class.java)
        assertThat((exception as VaultException).failure).isEqualTo(VaultFailure.AuthenticationFailed)
    }

    @Test
    fun aadRejectsTamperedSchemaVersion() = runBlocking {
        val plaintext = "Pain level and mood log".toByteArray(Charsets.UTF_8)
        val envelope = cipher.encrypt(
            plaintext = plaintext,
            recordId = "record-1",
            schemaVersion = 1,
            keyVersion = 1,
            key = testKey,
        )

        val tamperedEnvelope = envelope.copy(schemaVersion = 2)

        val result = cipher.decrypt(
            envelope = tamperedEnvelope,
            recordId = "record-1",
            key = testKey,
        )

        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as VaultException).failure).isEqualTo(VaultFailure.AuthenticationFailed)
    }

    @Test
    fun aadRejectsTamperedKeyVersion() = runBlocking {
        val plaintext = "Notes data".toByteArray(Charsets.UTF_8)
        val envelope = cipher.encrypt(
            plaintext = plaintext,
            recordId = "record-1",
            schemaVersion = 1,
            keyVersion = 1,
            key = testKey,
        )

        val tamperedEnvelope = envelope.copy(keyVersion = 2)

        val result = cipher.decrypt(
            envelope = tamperedEnvelope,
            recordId = "record-1",
            key = testKey,
        )

        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as VaultException).failure).isEqualTo(VaultFailure.AuthenticationFailed)
    }

    @Test
    fun ciphertextTamperingIsDetectedAndFailsClosed() = runBlocking {
        val plaintext = "Medication tracking".toByteArray(Charsets.UTF_8)
        val envelope = cipher.encrypt(
            plaintext = plaintext,
            recordId = "record-1",
            schemaVersion = 1,
            keyVersion = 1,
            key = testKey,
        )

        val tamperedCiphertext = envelope.ciphertext.copyOf()
        tamperedCiphertext[0] = (tamperedCiphertext[0].toInt() xor 0x01).toByte()

        val result = cipher.decrypt(
            envelope = envelope.copy(ciphertext = tamperedCiphertext),
            recordId = "record-1",
            key = testKey,
        )

        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as VaultException).failure).isEqualTo(VaultFailure.AuthenticationFailed)
    }

    @Test
    fun nonceTamperingIsDetectedAndFailsClosed() = runBlocking {
        val plaintext = "Secret payload".toByteArray(Charsets.UTF_8)
        val envelope = cipher.encrypt(
            plaintext = plaintext,
            recordId = "record-1",
            schemaVersion = 1,
            keyVersion = 1,
            key = testKey,
        )

        val tamperedNonce = envelope.nonce.copyOf()
        tamperedNonce[0] = (tamperedNonce[0].toInt() xor 0x01).toByte()

        val result = cipher.decrypt(
            envelope = envelope.copy(nonce = tamperedNonce),
            recordId = "record-1",
            key = testKey,
        )

        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as VaultException).failure).isEqualTo(VaultFailure.AuthenticationFailed)
    }

    @Test
    fun wrongKeyFailsClosed() = runBlocking {
        val plaintext = "Secret health data".toByteArray(Charsets.UTF_8)
        val envelope = cipher.encrypt(
            plaintext = plaintext,
            recordId = "record-1",
            schemaVersion = 1,
            keyVersion = 1,
            key = testKey,
        )

        val wrongKey = VaultKey(ByteArray(VAULT_KEY_BYTES) { 0x99.toByte() })
        val result = cipher.decrypt(
            envelope = envelope,
            recordId = "record-1",
            key = wrongKey,
        )

        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as VaultException).failure).isEqualTo(VaultFailure.AuthenticationFailed)
    }

    @Test
    fun noncesAreUniqueAcrossMultipleEncryptions() = runBlocking {
        val plaintext = "test".toByteArray()
        val nonces = HashSet<String>()
        val count = 1_000

        for (i in 0 until count) {
            val env = cipher.encrypt(plaintext, "id", 1, 1, testKey)
            val hex = env.nonce.joinToString("") { "%02x".format(it) }
            nonces.add(hex)
        }

        assertThat(nonces).hasSize(count)
    }

    @Test
    fun pbkdf2KeyDeriverProducesDeterministic256BitKeys() {
        val passphrase = "correct-horse-battery-staple".toCharArray()
        val salt = ByteArray(KDF_SALT_BYTES) { (it + 1).toByte() }

        val key1 = keyDeriver.deriveVaultKey(passphrase, salt, iterations = 10_000)
        val key2 = keyDeriver.deriveVaultKey(passphrase, salt, iterations = 10_000)

        assertThat(key1.bytes).hasLength(VAULT_KEY_BYTES)
        assertThat(key1).isEqualTo(key2)

        val diffPassphrase = "different-horse-battery-staple".toCharArray()
        val keyDiff = keyDeriver.deriveVaultKey(diffPassphrase, salt, iterations = 10_000)
        assertThat(key1).isNotEqualTo(keyDiff)

        val diffSalt = ByteArray(KDF_SALT_BYTES) { (it + 2).toByte() }
        val keyDiffSalt = keyDeriver.deriveVaultKey(passphrase, diffSalt, iterations = 10_000)
        assertThat(key1).isNotEqualTo(keyDiffSalt)
    }

    @Test
    fun pbkdf2KeyDeriverRejectsShortSalt() {
        val shortSalt = ByteArray(8)
        assertThrows(IllegalArgumentException::class.java) {
            keyDeriver.deriveVaultKey("passphrase".toCharArray(), shortSalt)
        }
    }
}
