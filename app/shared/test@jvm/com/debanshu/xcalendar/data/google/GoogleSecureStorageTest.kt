package com.debanshu.xcalendar.data.google

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GoogleSecureStorageTest {
    @Test
    fun encryptedArchiveCanBeReadByANewStorageInstance() = runTest {
        withTempDirectory { directory ->
            val file = directory.resolve("sessions.enc")
            val keyStore = FakeKeyStore()
            val firstProcess = DesktopGoogleSecureStorage(file, keyStore)

            firstProcess.write("account-a:refresh-secret")

            assertEquals("account-a:refresh-secret", DesktopGoogleSecureStorage(file, keyStore).read())
            assertNotNull(keyStore.value)
        }
    }

    @Test
    fun encryptedFileDoesNotContainSessionPlaintext() = runTest {
        withTempDirectory { directory ->
            val file = directory.resolve("sessions.enc")
            val plaintext = "account-a:refresh-token-should-not-appear"
            val storage = DesktopGoogleSecureStorage(file, FakeKeyStore())

            storage.write(plaintext)

            val stored = Files.readAllBytes(file).decodeToString()
            assertFalse(stored.contains(plaintext))
            assertFalse(stored.contains("refresh-token-should-not-appear"))
        }
    }

    @Test
    fun missingKeyWithExistingFileFailsWithoutReplacingEncryptedData() = runTest {
        withTempDirectory { directory ->
            val file = directory.resolve("sessions.enc")
            val keyStore = FakeKeyStore()
            DesktopGoogleSecureStorage(file, keyStore).write("original-session")
            val originalBlob = Files.readAllBytes(file)
            keyStore.value = null
            val storage = DesktopGoogleSecureStorage(file, keyStore)

            assertFailsWith<GoogleCalendarException> { storage.read() }
            assertFailsWith<GoogleCalendarException> { storage.write("replacement-session") }
            assertContentEquals(originalBlob, Files.readAllBytes(file))
            assertEquals(1, keyStore.writeCount)
        }
    }

    @Test
    fun orphanedKeyIsReusedWhenCreatingArchive() = runTest {
        withTempDirectory { directory ->
            val file = directory.resolve("sessions.enc")
            val existingKey = base64Key(7)
            val keyStore = FakeKeyStore(existingKey)
            val storage = DesktopGoogleSecureStorage(file, keyStore)

            assertNull(storage.read())
            storage.write("account-b:refresh-secret")

            assertEquals(existingKey, keyStore.value)
            assertEquals(0, keyStore.writeCount)
            assertEquals("account-b:refresh-secret", DesktopGoogleSecureStorage(file, keyStore).read())
        }
    }

    @Test
    fun corruptCiphertextFailsClosedAndPreservesTheFile() = runTest {
        withTempDirectory { directory ->
            val file = directory.resolve("sessions.enc")
            val keyStore = FakeKeyStore()
            val storage = DesktopGoogleSecureStorage(file, keyStore)
            storage.write("valid-session-data")
            val corrupted = Files.readAllBytes(file)
            corrupted[corrupted.lastIndex] = (corrupted.last().toInt() xor 0x01).toByte()
            Files.write(file, corrupted)
            val corruptBlob = Files.readAllBytes(file)

            assertFailsWith<GoogleCalendarException> { storage.read() }
            assertContentEquals(corruptBlob, Files.readAllBytes(file))
        }
    }

    private suspend fun withTempDirectory(block: suspend (Path) -> Unit) {
        val directory = Files.createTempDirectory("google-secure-storage-test-")
        try {
            block(directory)
        } finally {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder<Path>()).forEach { path -> Files.deleteIfExists(path) }
            }
        }
    }

    private fun base64Key(seed: Int) = Base64.getEncoder().encodeToString(ByteArray(32) { (it + seed).toByte() })

    private class FakeKeyStore(initialValue: String? = null) : GoogleSessionEncryptionKeyStore {
        var value: String? = initialValue
        var writeCount = 0

        override fun readKey(): String? = value

        override fun writeKey(value: String) {
            writeCount++
            this.value = value
        }
    }
}
