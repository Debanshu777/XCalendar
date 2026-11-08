package com.debanshu.xcalendar.build

import java.net.URLClassLoader
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.io.path.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DesktopDistributionTest {
    @Test
    fun sameNamedJarsFromDifferentGroupsRemainAvailableThroughTheLauncher() {
        val root = createTempDirectory("xcalendar-distribution-test")
        try {
            val jars = listOf("androidx", "jetbrains").map { group ->
                val jar = root / group / "same library.jar"
                jar.createParentDirectories()
                JarOutputStream(jar.outputStream()).use {
                    it.putNextEntry(JarEntry("$group.txt"))
                    it.write(group.toByteArray())
                    it.closeEntry()
                }
                jar
            }
            val output = root / "distribution"
            assembleDesktopDistribution(jars, output)
            // Java's manifest Class-Path must resolve both files, including escaped spaces.
            URLClassLoader(arrayOf((output / "XCalendar.jar").toUri().toURL()), null).use { loader ->
                for (group in listOf("androidx", "jetbrains")) {
                    val text = loader.getResourceAsStream("$group.txt")?.bufferedReader()?.use { it.readText() }
                    assertEquals(group, text)
                }
            }
            assertEquals(2, (output / "lib").listDirectoryEntries().size)
            assembleDesktopDistribution(jars.take(1), output)
            assertEquals(1, (output / "lib").listDirectoryEntries().size)
            assertFalse((output / "lib/0001-same library.jar").exists())
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
