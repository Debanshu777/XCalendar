package com.debanshu.xcalendar.build

import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.*

/** Keep local.properties optional, just as it was with BuildKonfig. */
@TaskAction
fun generateAppConfig(@Input propertiesFile: Path, @Output outputDir: Path) {
    val properties = Properties()
    if (propertiesFile.exists()) propertiesFile.inputStream().use(properties::load)
    val apiKey = properties.getProperty("API_KEY", "")
    val destination = outputDir / "com/debanshu/xcalendar/BuildKonfig.kt"
    destination.createParentDirectories()
    destination.writeText("""
        package com.debanshu.xcalendar

        // Generated from local.properties; do not edit or commit.
        internal object BuildKonfig {
            const val GOOGLE_IOS_CLIENT_ID: String = ${kotlinStringLiteral(properties.getProperty("GOOGLE_IOS_CLIENT_ID", ""))}
            const val GOOGLE_DESKTOP_CLIENT_ID: String = ${kotlinStringLiteral(properties.getProperty("GOOGLE_DESKTOP_CLIENT_ID", ""))}
            const val API_KEY: String = ${kotlinStringLiteral(apiKey)}
        }
    """.trimIndent() + "\n")
}

internal fun kotlinStringLiteral(value: String): String = buildString {
    append('"')
    for (char in value) {
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '$' -> append("\\$")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (char.code < 32) append("\\u%04x".format(char.code)) else append(char)
        }
    }
    append('"')
}
