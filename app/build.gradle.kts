@file:Suppress("UnstableApiUsage")

import com.android.build.api.variant.FilterConfiguration
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(gradleLibs.plugins.android.application)
    alias(gradleLibs.plugins.compose.compiler)
    alias(gradleLibs.plugins.google.ksp)
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
// Read keys defensively: an incomplete signing.properties must not crash configuration
// (which would drag debug builds down with it); only release builds fail closed on it.
val signingStoreFile = signingProperties.getProperty("storeFile")?.takeIf { it.isNotBlank() }
val signingStorePassword = signingProperties.getProperty("storePassword")?.takeIf { it.isNotBlank() }
val signingKeyAlias = signingProperties.getProperty("keyAlias")?.takeIf { it.isNotBlank() }
val signingReady =
    signingStoreFile != null && signingStorePassword != null && signingKeyAlias != null &&
        rootProject.file(signingStoreFile).isFile

android {
    signingConfigs {
        if (signingStoreFile != null && signingStorePassword != null && signingKeyAlias != null) {
            create("release") {
                storeFile = rootProject.file(signingStoreFile)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingStorePassword
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
            if (signingReady) {
                signingConfig = signingConfigs.getByName("release")
            } else if (signingProp.exists()) {
                // signing.properties present but incomplete/invalid: fail release builds
                // (fail-closed) only; debug builds keep their default signing config.
                gradle.taskGraph.whenReady {
                    if (allTasks.any { it.name.contains("Release", ignoreCase = true) }) {
                        throw GradleException(
                            "signing.properties is incomplete (need storeFile/storePassword/keyAlias): refusing to build an unsigned/under-configured release"
                        )
                    }
                }
            }
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
    // debug 仅含 x86（本机模拟器）；release 仅 armeabi-v7a（2026-10-24 用户裁定：电视实机验证通过，收敛 32 位）
    buildTypes.configureEach {
        ndk {
            if (name == "debug") {
                abiFilters.add("x86")
            } else {
                abiFilters.add("armeabi-v7a")
            }
        }
    }
}

// 发版产物命名契约（scripts/release_build.sh 的 find 模式与 release_assert_apk.sh 的断言逐字依赖，勿改格式）：
//   BiliView_<versionCode>_<versionName>.<buildType>_<flavor>_<abi>.apk
// AGP 9 已移除旧 Variant API（applicationVariants / ApkVariantOutputImpl），此处使用 androidComponents 公共 API。
// ABI 取该 output 自身 filters 中的 ABI 过滤器标识；本工程不做 ABI split，ABI 仅由 ndk.abiFilters 收窄后打入
// 同一个 APK，故 output.filters 为空 → 回退 "universal"（与旧实现的实际产物一致，发版脚本按此匹配/断言；
// 若将来启用 splits.abi，filter 出现即自动取到真实 ABI，无需改本块）。
// versionName 覆盖：AGP 把主 split 的 VariantOutput.versionName 写入 manifest（等价于旧 versionNameOverride），
// 发版脚本断言 badging 的 versionName 为 "<versionName>.release"，故此项不可省略。
androidComponents {
    onVariants { variant ->
        val variantName = variant.name
        val buildType = checkNotNull(variant.buildType) { "variant.buildType is null for $variantName" }
        val flavorName = checkNotNull(variant.flavorName) { "variant.flavorName is null for $variantName" }
        variant.outputs.forEach { output ->
            val abi = output.filters
                .firstOrNull { it.filterType == FilterConfiguration.FilterType.ABI }
                ?.identifier
                ?: "universal"
            output.outputFileName.set(
                "BiliView_${AppConfiguration.versionCode}_${AppConfiguration.versionName}.${buildType}_${flavorName}_$abi.apk"
            )
            output.versionName.set("${AppConfiguration.versionName}.$buildType")
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