package com.debanshu.xcalendar.data.google

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM encrypted session blob in Android's app-private, backup-excluded directory. */
internal class AndroidGoogleSecureStorage(context: Context) : GoogleSecureStorage {
    private val file = File(context.applicationContext.noBackupFilesDir, "google-sessions.enc")
    private val tempFile = File(file.parentFile, "google-sessions.enc.tmp")

    override suspend fun read(): String? = withContext(Dispatchers.IO) {
        try {
            if (!file.exists()) return@withContext null
            val blob = Base64.decode(file.readBytes(), Base64.NO_WRAP)
            require(blob.size > IV_SIZE) { "Invalid encrypted session data" }
            val iv = blob.copyOfRange(0, IV_SIZE)
            val ciphertext = blob.copyOfRange(IV_SIZE, blob.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(ciphertext).decodeToString()
        } catch (e: CancellationException) {
            throw e
        } catch (e: GoogleCalendarException) {
            throw e
        } catch (_: Exception) {
            throw GoogleCalendarException("Secure Google account storage is unavailable. Unlock the device and retry.")
        }
    }

    override suspend fun write(value: String) {
        withContext(Dispatchers.IO) {
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, key())
                val encrypted = cipher.doFinal(value.encodeToByteArray())
                val blob = cipher.iv + encrypted
                file.parentFile?.mkdirs()
                FileOutputStream(tempFile).use { output ->
                    output.write(Base64.encode(blob, Base64.NO_WRAP))
                    output.fd.sync()
                }
                Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (e: CancellationException) {
                throw e
            } catch (e: GoogleCalendarException) {
                tempFile.delete()
                throw e
            } catch (_: Exception) {
                tempFile.delete()
                throw GoogleCalendarException("Secure Google account storage is unavailable. Unlock the device and retry.")
            }
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        if (file.exists()) {
            throw GoogleCalendarException("Secure Google account storage is unavailable. Unlock the device and retry.")
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "xcalendar.google.sessions.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }
}
