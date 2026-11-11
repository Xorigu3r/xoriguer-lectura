import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.androidApplication)
}

kotlin {
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop")
    listOf(iosArm64(), iosSimulatorArm64()).forEach {
        it.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.activity.compose)
        }
        val desktopTest by getting {
            dependencies { implementation(kotlin("test")) }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.coroutines.swing)
                implementation(libs.pdfbox)
            }
        }
    }
}

compose.resources {
    packageOfResClass = "cat.xoriguer.lectura.res"
}

android {
    namespace = "cat.xoriguer.lectura"
    compileSdk = 36
    defaultConfig {
        applicationId = "cat.xoriguer.lectura"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
    buildTypes {
        getByName("release") { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.desktop {
    application {
        mainClass = "cat.xoriguer.lectura.MainKt"
        jvmArgs += listOf("--add-opens=java.base/java.nio=ALL-UNNAMED", "--add-opens=java.base/jdk.internal.ref=ALL-UNNAMED")
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "Lectura Xoriguer"
            packageVersion = "0.1.0"
            description = "Lector de llibres electrònics"
            vendor = "Xoriguer"
            windows {
                iconFile.set(project.file("icons/lectura.ico"))
                menuGroup = "Xoriguer"
                shortcut = true
                dirChooser = true
                upgradeUuid = "6f2b8e2a-4c1d-4b7e-9a51-3e0c7d1a9b42"
            }
        }
    }
}
