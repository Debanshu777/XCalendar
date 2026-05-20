
import com.codingfeline.buildkonfig.compiler.FieldSpec
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.buildkonfig)
}

kotlin {
    android {
        namespace = "com.debanshu.xcalendar"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources.enable = true
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_23)
        }
    }
    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_23)
        }
    }
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    composeCompiler {
        reportsDestination = layout.buildDirectory.dir("compose_compiler")
        metricsDestination = layout.buildDirectory.dir("compose_compiler")
        stabilityConfigurationFiles =
            listOf(
                rootProject.layout.projectDirectory.file("stability_config.conf"),
            )
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }

    sourceSets {
        val desktopMain by getting

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.koin.android)
        }
        commonMain.dependencies {
            implementation(libs.jetbrains.material3)
            implementation(libs.components.resources)
            implementation(libs.kotlinx.collections.immutable)

            implementation(libs.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.room.runtime)
            implementation(libs.sqlite.bundled)
            implementation(project.dependencies.platform(libs.ktor))

            implementation(libs.landscapist.coil3)
            implementation(libs.kotlinx.datetime)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
            api(libs.koin.annotations)

            implementation(libs.materialKolor)
            implementation(libs.store)
            implementation(libs.kermit)
            implementation(libs.androidx.adaptive.layout)
            implementation(libs.androidx.adaptive.navigation)
            implementation(libs.navigation3.compose.ui)
            implementation(libs.navigation3.viewmodel)

            implementation(libs.material3.adaptive)
            implementation(libs.shaderx)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.ktor.client.okhttp)
        }
        nativeMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }

    sourceSets.named("commonMain").configure {
        kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
    }
}

dependencies {
    add("kspCommonMainMetadata", libs.koin.ksp.compiler)
}

// KSP creates per-target / per-source-set configurations like 'kspAndroid',
// 'kspKotlinDesktop', 'kspKotlinIosArm64', etc. Add Room and Koin processors
// to those — but skip the metadata config (only Koin runs there) and skip
// non-source-set configs like the bare 'ksp' or 'kspPluginClasspath'.
val kspSourceSetConfigs = setOf(
    "kspAndroid", "kspAndroidMain",
    "kspKotlinDesktop", "kspDesktop",
    "kspKotlinIosArm64", "kspKotlinIosSimulatorArm64",
    "kspIosArm64", "kspIosSimulatorArm64",
)
configurations.matching { it.name in kspSourceSetConfigs }.configureEach {
    val cfgName = name
    dependencies.add(project.dependencies.create(libs.room.compiler.get()))
    dependencies.add(project.dependencies.create(libs.koin.ksp.compiler.get()))
    logger.info("Added Room + Koin KSP processors to configuration $cfgName")
}

ksp {
    arg("KOIN_USE_COMPOSE_VIEWMODEL", "true")
    arg("KOIN_CONFIG_CHECK", "false") // Disabled for now due to ComponentScan compatibility
    arg("KOIN_LOG_TIMES", "true")
    arg("KOIN_DEFAULT_MODULE", "false")
}

project.tasks.withType(KotlinCompilationTask::class.java).configureEach {
    if (name != "kspCommonMainKotlinMetadata") {
        dependsOn("kspCommonMainKotlinMetadata")
    }
}

// Explicitly declare KSP task dependencies
tasks.matching { it.name.startsWith("ksp") && it.name != "kspCommonMainKotlinMetadata" }.configureEach {
    dependsOn("kspCommonMainKotlinMetadata")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.freeCompilerArgs.addAll(
        "-P",
        "plugin:androidx.compose.compiler.plugins.kotlin:featureFlag=StrongSkipping",
    )
}

buildkonfig {
    packageName = "com.debanshu.xcalendar"

    val localProperties =
        Properties().apply {
            val propsFile = rootProject.file("local.properties")
            if (propsFile.exists()) {
                load(propsFile.inputStream())
            }
        }

    defaultConfigs {
        buildConfigField(
            FieldSpec.Type.STRING,
            "API_KEY",
            localProperties["API_KEY"]?.toString() ?: "",
        )
    }
}

compose.desktop {
    application {
        mainClass = "com.debanshu.xcalendar.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.debanshu.xcalendar"
            packageVersion = "1.0.0"
        }
    }
}
