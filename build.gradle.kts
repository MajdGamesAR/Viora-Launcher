import java.util.Properties

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
}

group = "com.viora.launcher"
version = "1.0.0-alpha"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    // ===== Compose Desktop =====
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // ===== Ktor (HTTP) =====
    implementation("io.ktor:ktor-client-core:3.0.0")
    implementation("io.ktor:ktor-client-cio:3.0.0")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.0")

    // ===== Kotlinx =====
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")

    // ===== Logging =====
    implementation("org.slf4j:slf4j-simple:2.0.16")

    // ===== Testing =====
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-opt-in=kotlin.RequiresOptIn"
        )
    }
}

tasks.test {
    useJUnitPlatform()
}

// ============================================================
//  ✅ قراءة مفاتيح API من local.properties (اختياري)
// ============================================================
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
    println("✅ Loaded local.properties")
} else {
    println("⚠️  local.properties not found — CurseForge will be disabled")
}

// ============================================================
//  ✅ تمرير المفاتيح إلى التطبيق
// ============================================================
tasks.withType<JavaExec>().configureEach {
    val apiKey = localProperties.getProperty("CURSEFORGE_API_KEY") ?: ""
    if (apiKey.isNotBlank()) {
        systemProperty("CURSEFORGE_API_KEY", apiKey)
        println("✅ CurseForge API key loaded")
    } else {
        systemProperty("CURSEFORGE_API_KEY", "")
        println("⚠️  CurseForge API key is empty")
    }
}

// ============================================================
//  ✅ Compose Desktop Configuration
// ============================================================
compose.desktop {
    application {
        mainClass = "com.viora.launcher.MainKt"

        // ✅ JVM args للتطبيق
        jvmArgs += listOf(
            "-Xmx2g",
            "-Dfile.encoding=UTF-8",
            "-Dapple.awt.application.appearance=system"
        )

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg
            )
            packageName = "Viora Launcher"
            packageVersion = "1.0.0"

            description = "Modern Minecraft Launcher with Arabic Support"
            copyright = "© 2026 MajdGamesAR. All rights reserved."
            vendor = "MajdGamesAR"

            windows {
                menu = true
                shortcut = true
                perUserInstall = true
                dirChooser = true
                upgradeUuid = "b8f9d6a4-1234-5678-9abc-def012345678"
                iconFile.set(project.file("src/main/resources/icons/viora.ico"))
            }

            linux {
                packageName = "viora-launcher"
                debMaintainer = "majdgamesar@example.com"
                menuGroup = "Games"
                appCategory = "Game"
                iconFile.set(project.file("src/main/resources/icons/viora.png"))
            }

            macOS {
                bundleID = "com.viora.launcher"
                iconFile.set(project.file("src/main/resources/icons/viora.icns"))
                dockName = "Viora Launcher"
            }
        }
    }
}