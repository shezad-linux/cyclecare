package com.cyclecare.core.security

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

const val DEFAULT_PBKDF2_ITERATIONS = 100_000

class VaultException(
    val failure: VaultFailure,
    message: String = failure.toString(),
    cause: Throwable? = null,
) : Exception(message, cause)

interface PassphraseKeyDeriver {
    fun deriveVaultKey(
        passphrase: CharArray,
        salt: ByteArray,
        iterations: Int = DEFAULT_PBKDF2_ITERATIONS,
    ): VaultKey
}

class Pbkdf2PassphraseKeyDeriver : PassphraseKeyDeriver {
    override fun deriveVaultKey(
        passphrase: CharArray,
        salt: ByteArray,
        iterations: Int,
    ): VaultKey {
        require(salt.size >= KDF_SALT_BYTES) {
            "Salt must be at least $KDF_SALT_BYTES bytes, was ${salt.size}"
        }
        val keySpec = PBEKeySpec(passphrase, salt, iterations, VAULT_KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val derivedBytes = factory.generateSecret(keySpec).encoded
        keySpec.clearPassword()
        return VaultKey(derivedBytes)
    }
}

class AesGcmPayloadCipher(
    private val secureRandom: SecureRandom = SecureRandom(),
) : PayloadCipher {

    override suspend fun encrypt(
        plaintext: ByteArray,
        recordId: String,
        schemaVersion: Int,
        keyVersion: Int,
        key: VaultKey,
    ): SyncEnvelope {
        val nonce = ByteArray(GCM_NONCE_BYTES)
        secureRandom.nextBytes(nonce)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_BITS, nonce)
        val keySpec = SecretKeySpec(key.bytes, "AES")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec)
        cipher.updateAAD(buildAad(recordId, schemaVersion, keyVersion))
        val ciphertext = cipher.doFinal(plaintext)
        return SyncEnvelope(
            schemaVersion = schemaVersion,
            keyVersion = keyVersion,
            nonce = nonce,
            ciphertext = ciphertext,
        )
    }

    override suspend fun decrypt(
        envelope: SyncEnvelope,
        recordId: String,
        key: VaultKey,
    ): Result<ByteArray> {
        return runCatching {
            if (envelope.nonce.size != GCM_NONCE_BYTES) {
                throw VaultException(VaultFailure.AuthenticationFailed, "Invalid nonce length")
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(GCM_TAG_BITS, envelope.nonce)
            val keySpec = SecretKeySpec(key.bytes, "AES")
            cipher.init(Cipher.DECRYPT_MODE, keySpec, spec)
            cipher.updateAAD(buildAad(recordId, envelope.schemaVersion, envelope.keyVersion))
            cipher.doFinal(envelope.ciphertext)
        }.recoverCatching { error ->
            if (error is GeneralSecurityException) {
                throw VaultException(VaultFailure.AuthenticationFailed, "Authentication failed", error)
            }
            throw error
        }
    }

    private fun buildAad(recordId: String, schemaVersion: Int, keyVersion: Int): ByteArray {
        return "$recordId:$schemaVersion:$keyVersion".toByteArray(Charsets.UTF_8)
    }
}
