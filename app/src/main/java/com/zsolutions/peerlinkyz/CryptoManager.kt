package com.zsolutions.peerlinkyz

import android.content.Context
import android.util.Log
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECParameterSpec
import java.nio.charset.StandardCharsets
import java.security.*
import javax.crypto.KeyAgreement
import javax.crypto.spec.SecretKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec

class CryptoManager(context: Context) {

    private val keysetName = "master_keyset"
    private val preferenceFile = "master_key_preference"
    private val masterKeyUri = "android-keystore://_androidx_security_master_key_"

    private lateinit var aead: Aead

    init {
        // Add Bouncy Castle as a security provider
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }

        AeadConfig.register()
        try {
            aead = AndroidKeysetManager.Builder()
                .withSharedPref(context, keysetName, preferenceFile)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri(masterKeyUri)
                .build()
                .keysetHandle
                .getPrimitive(Aead::class.java)
            Log.d("CryptoManager", "Existing keyset loaded.")
        } catch (e: Exception) {
            Log.e("CryptoManager", "Failed to load existing keyset, generating new one: ${e.message}")
            aead = AndroidKeysetManager.Builder()
                .withSharedPref(context, keysetName, preferenceFile)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri(masterKeyUri)
                .build()
                .keysetHandle
                .getPrimitive(Aead::class.java)
            Log.d("CryptoManager", "New keyset generated.")
        }
    }

    // ECDH Key Generation
    fun generateECDHKeyPair(): KeyPair {
        val ecSpec: ECParameterSpec = ECNamedCurveTable.getParameterSpec("secp256r1")
        val gskf = KeyFactory.getInstance("ECDH", "BC")
        val kg = KeyPairGenerator.getInstance("ECDH", "BC")
        kg.initialize(ecSpec, SecureRandom())
        return kg.generateKeyPair()
    }

    // Shared Secret Derivation
    fun deriveSharedSecret(privateKey: PrivateKey, publicKey: PublicKey): ByteArray {
        val keyAgreement = KeyAgreement.getInstance("ECDH", "BC")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(publicKey, true)
        return keyAgreement.generateSecret()
    }

    // Symmetric Encryption (AES/GCM) - Commented out for testing
    fun encrypt(data: ByteArray, secretKey: ByteArray): ByteArray {
        // Commented out for testing - return plain data
        // val cipher = Cipher.getInstance("AES/GCM/NoPadding", "BC")
        // val secretKeySpec = SecretKeySpec(secretKey, "AES")
        // val iv = ByteArray(12) // GCM recommended IV size is 12 bytes
        // SecureRandom().nextBytes(iv)
        // val ivParameterSpec = IvParameterSpec(iv)
        // cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec)
        // val encryptedData = cipher.doFinal(data)
        // return iv + encryptedData // Prepend IV to ciphertext
        return data
    }

    // Symmetric Decryption (AES/GCM) - Commented out for testing
    fun decrypt(encryptedData: ByteArray, secretKey: ByteArray): ByteArray {
        // Commented out for testing - return plain data
        // val cipher = Cipher.getInstance("AES/GCM/NoPadding", "BC")
        // val secretKeySpec = SecretKeySpec(secretKey, "AES")
        // val iv = encryptedData.copyOfRange(0, 12) // Extract IV from ciphertext
        // val actualEncryptedData = encryptedData.copyOfRange(12, encryptedData.size)
        // val ivParameterSpec = IvParameterSpec(iv)
        // cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec)
        // return cipher.doFinal(actualEncryptedData)
        return encryptedData
    }
}
