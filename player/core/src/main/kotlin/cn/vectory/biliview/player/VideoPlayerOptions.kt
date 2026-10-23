package cn.vectory.biliview.player

data class VideoPlayerOptions(
    val userAgent: String? = null,
    val referer: String? = null,
    val enableFfmpegAudioRenderer: Boolean = false
)