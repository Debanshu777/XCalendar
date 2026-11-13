package com.debanshu.xcalendar.build

import java.util.Properties
import kotlin.io.path.*
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AppConfigTest {
    @Test
    fun missingPropertiesGenerateAnEmptyKey() {
        val root = createTempDirectory("xcalendar-config-test")
        try {
            generateAppConfig(root / "local.properties", root / "generated")
            assertContains((root / "generated/com/debanshu/xcalendar/BuildKonfig.kt").readText(), "const val GOOGLE_IOS_CLIENT_ID: String = \"\"")
            assertContains((root / "generated/com/debanshu/xcalendar/BuildKonfig.kt").readText(), "const val GOOGLE_DESKTOP_CLIENT_ID: String = \"\"")
            assertContains(
                (root / "generated/com/debanshu/xcalendar/BuildKonfig.kt").readText(),
                "const val API_KEY: String = \"\"",
            )
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun changedAndRemovedKeysReplaceThePreviousGeneratedValue() {
        val root = createTempDirectory("xcalendar-config-test")
        try {
            val propertiesFile = root / "local.properties"
            val generated = root / "generated/com/debanshu/xcalendar/BuildKonfig.kt"
            for (key in listOf("first-test-key", "replacement-test-key")) {
                val properties = Properties().apply { setProperty("API_KEY", key) }
                propertiesFile.outputStream().use { properties.store(it, null) }
                generateAppConfig(propertiesFile, root / "generated")
                assertContains(generated.readText(), "const val API_KEY: String = \"$key\"")
            }
            propertiesFile.deleteExisting()
            generateAppConfig(propertiesFile, root / "generated")
            assertFalse(generated.readText().contains("replacement-test-key"))
            assertContains(generated.readText(), "const val API_KEY: String = \"\"")
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun valuesCannotInjectKotlinCodeOrStringTemplates() {
        assertEquals("\"quote\\\" slash\\\\ template\\\$name newline\\n tab\\t return\\r null\\u0000\"",
            kotlinStringLiteral("quote\" slash\\ template\$name newline\n tab\t return\r null\u0000"))
    }
}
