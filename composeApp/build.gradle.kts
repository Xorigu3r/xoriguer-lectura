import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.androidApplication)
}

// La versió de l'app es canvia només aquí (Android, Windows i l'avís de versions noves la llegeixen d'aquí)
val versioApp = "0.5.1"
val codiVersio = 7 // +1 a cada versió (si no, Android no deixa actualitzar)

/** Genera Versio.kt amb la versió, perquè l'app sàpiga quina és sense escriure-la dues vegades. */
val generaVersio by tasks.registering {
    val dir = layout.buildDirectory.dir("generated/versio/kotlin")
    val v = versioApp
    inputs.property("versio", v)
    outputs.dir(dir)
    doLast {
        val f = dir.get().file("cat/xoriguer/lectura/Versio.kt").asFile
        f.parentFile.mkdirs()
        f.writeText(
            listOf(
                "package cat.xoriguer.lectura",
                "",
                "/** Versió de l'app (generat per Gradle des de composeApp/build.gradle.kts). */",
                "const val VERSIO_APP = \"$v\"",
                "",
            ).joinToString("\n"),
        )
    }
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
        commonMain { kotlin.srcDir(generaVersio) }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.coroutines.core)
        }
        // Codi compartit per Android i Windows (baixades, fitxers): totes dues són JVM
        val jvmComu by creating { dependsOn(commonMain.get()) }
        androidMain.get().dependsOn(jvmComu)
        androidMain.dependencies {
            implementation(libs.activity.compose)
        }
        val desktopTest by getting {
            dependencies { implementation(kotlin("test")) }
        }
        val desktopMain by getting {
            dependsOn(jvmComu)
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
        versionCode = codiVersio
        versionName = versioApp
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
    // Clau pròpia per signar l'APK. Es guarda FORA del projecte (no es puja mai a GitHub):
    // %USERPROFILE%\.lectura-xoriguer\signatura.properties + lectura-xoriguer.jks. Si es perd, les
    // actualitzacions ja no s'instal·larien a sobre: cal tenir-ne una còpia de seguretat.
    val propsSignatura = File(System.getProperty("user.home"), ".lectura-xoriguer/signatura.properties")
    signingConfigs {
        if (propsSignatura.exists()) create("xoriguer") {
            val p = Properties().apply { propsSignatura.inputStream().use { load(it) } }
            storeFile = file(p.getProperty("storeFile"))
            storePassword = p.getProperty("storePassword")
            keyAlias = p.getProperty("keyAlias")
            keyPassword = p.getProperty("keyPassword")
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("xoriguer")
        }
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
            packageVersion = versioApp
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
