object AppConfiguration {
    // 全量迁移已完成：Kotlin 源码包与各模块 namespace 均由本常量派生（上游 bv 包名 → cn.vectory.biliview），R/BuildConfig 随之生成于新包
    const val appId = "cn.vectory.biliview"
    // 对外应用标识（applicationId）
    const val applicationId = "cn.vectory.biliview"
    const val compileSdk = 36
    const val minSdk = 24
    const val targetSdk = 36
    const val jdk = 21
    private const val major = 0
    private const val minor = 2
    private const val patch = 0
    private const val hotFix = 0

    @Suppress("KotlinConstantConditions")
    val versionName: String by lazy {
        "$major.$minor.$patch${".$hotFix".takeIf { hotFix != 0 } ?: ""}" +
                ".r${versionCode}.${"git rev-list HEAD --abbrev-commit --max-count=1".exec()}"
    }
    val versionCode: Int by lazy { "git rev-list --count HEAD".exec().toInt() }
    const val libVLCVersion = "3.0.18"
}

fun String.exec() = String(Runtime.getRuntime().exec(this).inputStream.readBytes()).trim()
