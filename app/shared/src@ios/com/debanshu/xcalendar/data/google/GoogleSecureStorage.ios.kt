@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.debanshu.xcalendar.data.google

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecMissingEntitlement
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/** Keychain-backed opaque session storage, restricted to this device after first unlock. */
internal class IosGoogleSecureStorage(
    private val service: String = PRODUCTION_SERVICE,
) : GoogleSecureStorage {
    override suspend fun read(): String? = withContext(Dispatchers.Default) {
        withBaseQuery { query ->
            CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
            CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
            memScoped {
                val result = alloc<CFTypeRefVar>()
                val status = SecItemCopyMatching(query, result.ptr)
                when (status) {
                    errSecItemNotFound -> null
                    errSecSuccess -> {
                        try {
                            val data: CFDataRef = result.value?.reinterpret()
                                ?: throw GoogleCalendarException("Secure Google account storage is unavailable. Unlock the device and retry.")
                            val length = CFDataGetLength(data).toInt()
                            val bytes = if (length == 0) ByteArray(0) else {
                                CFDataGetBytePtr(data)?.reinterpret<ByteVar>()?.readBytes(length)
                                    ?: throw GoogleCalendarException("Secure Google account storage is unavailable. Unlock the device and retry.")
                            }
                            bytes.decodeToString()
                        } finally {
                            result.value?.let(::CFRelease)
                        }
                    }
                    else -> throw GoogleCalendarException("Secure Google account storage is unavailable. Unlock the device and retry.")
                }
            }
        }
    }

    override suspend fun write(value: String) {
        withContext(Dispatchers.Default) {
            val bytes = value.encodeToByteArray()
            val data = bytes.usePinned {
                CFDataCreate(kCFAllocatorDefault, it.addressOf(0).reinterpret<UByteVar>(), bytes.size.convert())
            } ?: throw GoogleCalendarException("Secure Google account storage is unavailable. Unlock the device and retry.")

            try {
                val status = withBaseQuery { query ->
                    val attributes = newDictionary()
                    try {
                        CFDictionarySetValue(attributes, kSecValueData, data)
                        var result = SecItemUpdate(query, attributes)
                        if (result == errSecItemNotFound) {
                            result = withBaseQuery { addQuery ->
                                CFDictionarySetValue(addQuery, kSecValueData, data)
                                CFDictionarySetValue(addQuery, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
                                SecItemAdd(addQuery, null)
                            }
                            if (result == errSecDuplicateItem) result = SecItemUpdate(query, attributes)
                        }
                        result
                    } finally {
                        CFRelease(attributes)
                    }
                }
                if (status != errSecSuccess) {
                    throw GoogleCalendarException("Secure Google account storage is unavailable. Unlock the device and retry.")
                }
            } finally {
                CFRelease(data)
            }
        }
    }

    /** The guard makes test cleanup incapable of deleting production session data. */
    internal suspend fun hasTestKeychainEntitlement(): Boolean {
        check(service == ROUNDTRIP_TEST_SERVICE)
        return withContext(Dispatchers.Default) {
            withBaseQuery { query ->
                CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
                CFDictionarySetValue(query, kSecMatchLimit, kSecMatchLimitOne)
                memScoped {
                    val result = alloc<CFTypeRefVar>()
                    val status = SecItemCopyMatching(query, result.ptr)
                    if (status == errSecSuccess) result.value?.let(::CFRelease)
                    when (status) {
                        errSecMissingEntitlement -> false
                        errSecItemNotFound, errSecSuccess -> true
                        else -> throw GoogleCalendarException("Secure Google test storage check failed (OSStatus $status).")
                    }
                }
            }
        }
    }

    /** The guard makes test cleanup incapable of deleting production session data. */
    internal suspend fun clearTestItem() {
        check(service == ROUNDTRIP_TEST_SERVICE)
        withContext(Dispatchers.Default) {
            withBaseQuery { query ->
                val status = SecItemDelete(query)
                if (status != errSecSuccess && status != errSecItemNotFound) {
                    throw GoogleCalendarException("Secure Google test storage could not be cleared (OSStatus $status).")
                }
            }
        }
    }

    private fun newDictionary(): CFMutableDictionaryRef =
        CFDictionaryCreateMutable(kCFAllocatorDefault, 0, null, null)
            ?: throw GoogleCalendarException("Secure Google account storage is unavailable. Unlock the device and retry.")

    private inline fun <T> withBaseQuery(block: (CFMutableDictionaryRef) -> T): T = memScoped {
        val serviceRef = cfString(service)
        val accountRef = cfString(ACCOUNT)
        val query = newDictionary()
        try {
            CFDictionarySetValue(query, kSecClass, kSecClassGenericPassword)
            CFDictionarySetValue(query, kSecAttrService, serviceRef)
            CFDictionarySetValue(query, kSecAttrAccount, accountRef)
            block(query)
        } finally {
            CFRelease(query)
            CFRelease(serviceRef)
            CFRelease(accountRef)
        }
    }

    private fun cfString(value: String): CFStringRef = memScoped {
        CFStringCreateWithCString(kCFAllocatorDefault, value.cstr.getPointer(this), kCFStringEncodingUTF8)
            ?: throw GoogleCalendarException("Secure Google account storage is unavailable. Unlock the device and retry.")
    }

    internal companion object {
        const val PRODUCTION_SERVICE = "com.debanshu.xcalendar.google.sessions.v1"
        const val ROUNDTRIP_TEST_SERVICE = "com.debanshu.xcalendar.google.sessions.test.roundtrip.v1"
        const val ISOLATION_TEST_SERVICE = "com.debanshu.xcalendar.google.sessions.test.isolation.v1"
        const val ACCOUNT = "sessions"
    }
}
