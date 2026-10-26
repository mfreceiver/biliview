// 用 buildscript classpath 覆盖 AGP 9 内置 KGP 到 Kotlin 2.4（AGENTS.md 纪律：
// 不得应用 org.jetbrains.kotlin.android，也不得用 builtInKotlin/newDsl 逃生口；
// 抬内置 Kotlin 只能走这条路径，catalog kotlin 版本须与这里一致）。
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${gradleLibs.versions.kotlin.get()}")
    }
}

plugins {
    alias(gradleLibs.plugins.android.application) apply false
    alias(gradleLibs.plugins.android.library) apply false
    alias(gradleLibs.plugins.compose.compiler) apply false
    alias(gradleLibs.plugins.google.ksp) apply false
    alias(gradleLibs.plugins.kotlin.jvm) apply false
    alias(gradleLibs.plugins.kotlin.serialization) apply false
    alias(gradleLibs.plugins.versions)
}