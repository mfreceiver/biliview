@file:Suppress("UnstableApiUsage")

import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(gradleLibs.plugins.android.application)
    alias(gradleLibs.plugins.compose.compiler)
    alias(gradleLibs.plugins.google.ksp)
    alias(gradleLibs.plugins.kotlin.android)
    alias(gradleLibs.plugins.kotlin.serialization)
}


val signingProp = rootProject.file("signing.properties")
val signingProperties = Properties().apply {
    if (signingProp.exists()) {
        FileInputStream(signingProp).use(::load)
    } else {
        logger.lifecycle("signing.properties not found; release APKs will be unsigned")
    }
}

android {
    signingConfigs {
        if (signingProp.exists()) {
            create("release") {
                storeFile = rootProject.file(signingProperties.getProperty("storeFile"))
                storePassword = signingProperties.getProperty("storePassword")
                keyAlias = signingProperties.getProperty("keyAlias")
                keyPassword = signingProperties.getProperty("storePassword")
            }
        }
    }

    namespace = AppConfiguration.appId
    compileSdk = AppConfiguration.compileSdk

    defaultConfig {
        applicationId = AppConfiguration.applicationId
        minSdk = AppConfiguration.minSdk
        targetSdk = AppConfiguration.targetSdk
        versionCode = AppConfiguration.versionCode
        versionName = AppConfiguration.versionName
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    flavorDimensions.add("channel")

    productFlavors {
        create("default") {
            dimension = "channel"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (signingProp.exists()) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        compose = true
        //buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "**/*.proto"
        }
    }

    // 发版 ABI 纪律（见 docs/TODO.md 与 README）：
    // debug 仅含 x86（本机模拟器）；release 为 armeabi-v7a + arm64-v8a 合并单包
    buildTypes.configureEach {
        ndk {
            if (name == "debug") {
                abiFilters.add("x86")
            } else {
                abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
            }
        }
    }

    applicationVariants.configureEach {
        val variant = this
        outputs.configureEach {
            (this as ApkVariantOutputImpl).apply {
                val abi = this.filters.find { it.filterType == "ABI" }?.identifier ?: "universal"
                outputFileName =
                    "BiliView_${AppConfiguration.versionCode}_${AppConfiguration.versionName}.${variant.buildType.name}_${variant.flavorName}_$abi.apk"
                versionNameOverride =
                    "${variant.versionName}.${variant.buildType.name}"
            }
        }
    }
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_build_reports")
    stabilityConfigurationFiles.addAll(
        layout.projectDirectory.file("compose_compiler_config.conf")
    )
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(AppConfiguration.jdk))
    }
}

dependencies {
    implementation(project(":app:mobile"))
    implementation(project(":app:tv"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}