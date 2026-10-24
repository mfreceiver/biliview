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
    // 0.2.0 的首个热修复：R8 规则收尾（删除空转的 LibVLC keep、清理未接线的 consumer-rules.pro）
    private const val hotFix = 1

    // 版本号必须每次构建现算：buildSrc 的类在 Gradle daemon 里跨构建复用 classloader，
    // 用 by lazy 缓存会把第一次算出的 versionName/versionCode 冻结在整个 daemon 生命周期里
    // （实测：daemon 存活期间新提交后构建仍产出旧 versionName，release_assert_apk.sh fail-closed）。
    @Suppress("KotlinConstantConditions")
    val versionName: String
        get() = "$major.$minor.$patch${".$hotFix".takeIf { hotFix != 0 } ?: ""}" +
                ".r${versionCode}.${"git rev-list HEAD --abbrev-commit --max-count=1".exec()}"

    val versionCode: Int get() = "git rev-list --count HEAD".exec().toInt()
}

fun String.exec() = String(Runtime.getRuntime().exec(this).inputStream.readBytes()).trim()
