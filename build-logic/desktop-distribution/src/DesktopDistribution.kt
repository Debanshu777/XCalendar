package com.debanshu.xcalendar.build

import org.jetbrains.amper.plugins.Classpath
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import java.net.URI
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.io.path.*

/** Preserve the full classpath, including AndroidX/JetBrains JARs with identical names. */
@TaskAction
fun packageDesktop(@Input appClasspath: Classpath, @Output outputDir: Path) {
    assembleDesktopDistribution(appClasspath.resolvedFiles, outputDir)
    println("Desktop distribution: $outputDir")
    println("Run with: java --enable-native-access=ALL-UNNAMED -jar \"${outputDir / "XCalendar.jar"}\"")
}

@OptIn(ExperimentalPathApi::class)
internal fun assembleDesktopDistribution(classpath: List<Path>, outputDir: Path) {
    require(classpath.isNotEmpty()) { "The desktop runtime classpath is empty" }
    require(classpath.all { it.isRegularFile() && it.extension == "jar" }) {
        "Desktop distribution requires a JAR runtime classpath"
    }
    // This directory belongs exclusively to the task; remove obsolete dependencies on rebuild.
    if (outputDir.exists()) outputDir.deleteRecursively()
    (outputDir / "lib").createDirectories()
    val entries = classpath.mapIndexed { index, source ->
        val relativePath = "lib/${index.toString().padStart(4, '0')}-${source.name}"
        source.copyTo(outputDir.resolve(relativePath))
        URI(null, null, relativePath, null).toASCIIString()
    }
    val manifest = Manifest().apply {
        mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        mainAttributes[Attributes.Name.MAIN_CLASS] = "com.debanshu.xcalendar.MainKt"
        mainAttributes[Attributes.Name.CLASS_PATH] = entries.joinToString(" ")
    }
    JarOutputStream((outputDir / "XCalendar.jar").outputStream(), manifest).use { }
}
