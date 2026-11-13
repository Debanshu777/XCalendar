package com.debanshu.xcalendar.data.google

import com.github.javakeyring.Keyring
import com.github.javakeyring.KeyringStorageType
import com.github.javakeyring.PasswordAccessException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Native key store seam so encryption behavior can be tested with fake secrets. */
internal interface GoogleSessionEncryptionKeyStore {
    fun readKey(): String?
    fun writeKey(value: String)
}

/** Stores an AES-GCM encrypted session archive beside the desktop database. */
internal class DesktopGoogleSecureStorage(
    private val file: Path = googleSessionFile(),
    private val keyStore: GoogleSessionEncryptionKeyStore = NativeGoogleSessionEncryptionKeyStore(),
) : GoogleSecureStorage {
    override suspend fun read(): String? = withContext(Dispatchers.IO) {
        if (!Files.exists(file)) return@withContext null
        try {
            val storedKey = keyStore.readKey()
                ?: throw GoogleCalendarException("Saved Google sessions are encrypted, but their OS key is unavailable. Unlock the OS credential store and retry. If the key was deleted, XCalendar session recovery is required before reconnecting.")
            decrypt(Files.readAllBytes(file), decodeKey(storedKey))
        } catch (e: CancellationException) {
            throw e
        } catch (e: GoogleCalendarException) {
            throw e
        } catch (e: Exception) {
            throw GoogleCalendarException("Could not read saved Google sessions. Check XCalendar app-data permissions, unlock your OS credential store, and retry.")
        }
    }

    override suspend fun write(value: String) = withContext(Dispatchers.IO) {
        try {
            val parent = file.parent ?: throw IOException("Missing session directory")
            Files.createDirectories(parent)
            setPrivatePermissions(parent, directory = true)

            val keyBytes = if (Files.exists(file)) {
                val storedKey = keyStore.readKey()
                    ?: throw GoogleCalendarException("Saved Google sessions are encrypted, but their OS key is unavailable. Unlock the OS credential store and retry. If the key was deleted, XCalendar session recovery is required before reconnecting.")
                decodeKey(storedKey)
            } else {
                keyStore.readKey()?.let(::decodeKey) ?: newEncryptionKey()
            }
            try {
                val encrypted = encrypt(value, keyBytes)
                try { writeAtomically(file, encrypted) } finally { encrypted.fill(0) }
            } finally {
                keyBytes.fill(0)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GoogleCalendarException) {
            throw e
        } catch (e: Exception) {
            throw GoogleCalendarException("Could not securely save Google sessions. Check XCalendar app-data permissions, unlock your OS credential store, and retry.")
        }
    }

    private fun encrypt(value: String, key: ByteArray): ByteArray {
        val nonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(MAGIC)
        val plaintext = value.encodeToByteArray()
        val encrypted = try { cipher.doFinal(plaintext) } finally { plaintext.fill(0) }
        return ByteBuffer.allocate(MAGIC.size + nonce.size + encrypted.size)
            .put(MAGIC).put(nonce).put(encrypted).array()
    }

    private fun newEncryptionKey(): ByteArray {
        val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
        try {
            keyStore.writeKey(Base64.getEncoder().encodeToString(bytes))
            return bytes
        } catch (e: Exception) {
            bytes.fill(0)
            throw e
        }
    }

    private fun decrypt(blob: ByteArray, key: ByteArray): String {
        try {
            if (blob.size < MAGIC.size + NONCE_BYTES + TAG_BYTES || !blob.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
                throw GoogleCalendarException("Saved Google sessions have an unsupported or damaged format. Unlock your OS credential store and retry. If this persists, XCalendar session recovery is required before reconnecting.")
            }
            val nonce = blob.copyOfRange(MAGIC.size, MAGIC.size + NONCE_BYTES)
            val ciphertext = blob.copyOfRange(MAGIC.size + NONCE_BYTES, blob.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(MAGIC)
            return cipher.doFinal(ciphertext).decodeToString()
        } catch (e: AEADBadTagException) {
            throw GoogleCalendarException("Saved Google sessions could not be authenticated. Unlock the OS credential store and retry. If this persists, XCalendar session recovery is required before reconnecting.")
        } finally {
            key.fill(0)
        }
    }

    private fun decodeKey(encoded: String): ByteArray {
        val key = runCatching { Base64.getDecoder().decode(encoded) }.getOrElse {
            throw GoogleCalendarException("The OS credential store contains an invalid Google session key. If this persists, XCalendar session recovery is required before reconnecting.")
        }
        if (key.size != 32) {
            key.fill(0)
            throw GoogleCalendarException("The OS credential store contains an invalid Google session key. If this persists, XCalendar session recovery is required before reconnecting.")
        }
        return key
    }

    private fun writeAtomically(target: Path, bytes: ByteArray) {
        val temporary = Files.createTempFile(target.parent, "google-session-", ".tmp")
        try {
            setPrivatePermissions(temporary, directory = false)
            FileChannel.open(temporary, WRITE).use { channel ->
                var buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            try {
                Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (e: AtomicMoveNotSupportedException) {
                throw IOException("Atomic session file replacement is not supported", e)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun setPrivatePermissions(path: Path, directory: Boolean) {
        val permissions = if (directory) {
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)
        } else {
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
        }
        try {
            Files.setPosixFilePermissions(path, permissions)
        } catch (_: UnsupportedOperationException) {
            // Windows ACLs inherit from the per-user app-data directory.
        }
    }

    private companion object {
        val MAGIC = "XCGS1".encodeToByteArray()
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
        const val TAG_BYTES = TAG_BITS / 8
    }
}

private fun googleSessionFile(): Path {
    val os = System.getProperty("os.name", "").lowercase()
    val userHome = Path.of(System.getProperty("user.home"))
    val appData = when {
        os.contains("win") -> Path.of(System.getenv("APPDATA")?.takeIf { it.isNotBlank() } ?: userHome.resolve("AppData/Roaming").toString()).resolve("XCalendar")
        os.contains("mac") -> userHome.resolve("Library/Application Support/XCalendar")
        else -> userHome.resolve(".local/share/XCalendar")
    }
    return appData.resolve("google-sessions").resolve("sessions.enc")
}

private class NativeGoogleSessionEncryptionKeyStore : GoogleSessionEncryptionKeyStore {
    override fun readKey(): String? = withKeyring { keyring ->
        try {
            keyring.getPassword(SERVICE, ACCOUNT)
        } catch (e: PasswordAccessException) {
            if (isMissingCredential(e)) {
                null
            } else {
                // A locked/denied keychain must never be mistaken for an absent key:
                // if the archive is also absent, that could replace the key silently.
                throw GoogleCalendarException("Could not access the OS credential store. Unlock your system keychain or Secret Service and retry.")
            }
        }
    }

    override fun writeKey(value: String) = withKeyring { keyring -> keyring.setPassword(SERVICE, ACCOUNT, value) }

    private fun <T> withKeyring(block: (Keyring) -> T): T {
        val os = System.getProperty("os.name", "").lowercase()
        val storage = when {
            os.contains("mac") -> KeyringStorageType.OSX_KEYCHAIN
            os.contains("win") -> KeyringStorageType.WINDOWS_CREDENTIAL_STORE
            os.contains("linux") -> KeyringStorageType.GNOME_KEYRING
            else -> throw GoogleCalendarException("Secure Google sign-in is not supported on this desktop platform.")
        }
        return try {
            Keyring.create(storage).use(block)
        } catch (e: CancellationException) {
            throw e
        } catch (_: GoogleCalendarException) {
            throw GoogleCalendarException("Could not access the OS credential store. Unlock your system keychain or Secret Service and retry.")
        } catch (_: Exception) {
            throw GoogleCalendarException("Could not access the OS credential store. Unlock your system keychain or Secret Service and retry.")
        }
    }

    private fun isMissingCredential(error: PasswordAccessException): Boolean {
        val message = error.message ?: return false
        return message == "No stored credentials match $SERVICE account: $ACCOUNT" ||
            message == "Error code 1168" // Windows ERROR_NOT_FOUND
    }

    private companion object {
        const val SERVICE = "com.debanshu.xcalendar.google-sessions"
        const val ACCOUNT = "aes-gcm-key-v1"
    }
}
