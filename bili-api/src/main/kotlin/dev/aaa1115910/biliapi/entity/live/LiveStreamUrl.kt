package dev.aaa1115910.biliapi.entity.live

import dev.aaa1115910.biliapi.http.entity.live.PlayCodec
import dev.aaa1115910.biliapi.http.entity.live.PlayUrlInfo
import dev.aaa1115910.biliapi.http.entity.live.RoomPlayInfoData

/**
 * 一条可直接交给播放器的直播流地址
 *
 * 当前播放器（ExoMediaPlayer）只挂 ProgressiveMediaSource，因此只支持 HTTP-FLV；
 * HLS 虽在接口里返回，但播放器没有 HlsMediaSource，这里不选取。
 */
data class LiveStreamUrl(
    val url: String,
    val protocolName: String,
    val formatName: String,
    val codecName: String,
    /**
     * 服务端实际下发的画质（`current_qn`，不是请求的 qn）
     */
    val qn: Int,
    /**
     * 画质文案，如「原画」「超清」，来自 `g_qn_desc`
     */
    val qualityDescription: String = ""
)

/**
 * 直播流地址拼接与选择策略
 *
 * B 站返回的地址是分段的：`url_info[].host + base_url + url_info[].extra`，
 * 其中 `base_url` 通常已经以 `?` 结尾，`extra` 是 CDN 签名查询串。
 * 因为拼接规则容易写错（重复 `?` / 丢失 `&`），这里做成纯函数并单测覆盖。
 */
object LiveStreamSelector {
    const val PROTOCOL_HTTP_STREAM = "http_stream"
    const val PROTOCOL_HTTP_HLS = "http_hls"
    const val FORMAT_FLV = "flv"
    const val CODEC_AVC = "avc"
    const val CODEC_HEVC = "hevc"

    /**
     * 默认编码优先级：AVC 在前
     *
     * 电视盒子的 HEVC 硬解支持参差不齐，且本项目 release 只出 armeabi-v7a；
     * 优先给兼容性最好的 AVC，HEVC 作为备选（同一 codec 内取 qn 更高者）。
     */
    val DEFAULT_CODEC_PRIORITY: List<String> = listOf(CODEC_AVC, CODEC_HEVC)

    /**
     * 拼接 CDN 播放地址
     *
     * - [host] 缺协议时补 `https://`，末尾 `/` 去掉
     * - [baseUrl] 不以 `/` 开头时补上
     * - [extra] 为空直接返回；`baseUrl` 已以 `?`/`&` 结尾则直接追加；否则按是否已有 query 用 `?`/`&` 连接
     */
    fun buildUrl(host: String, baseUrl: String, extra: String): String {
        val normalizedHost = when {
            host.isBlank() -> ""
            host.startsWith("http://") || host.startsWith("https://") -> host.trimEnd('/')
            else -> "https://" + host.trimEnd('/')
        }
        val normalizedBaseUrl = when {
            baseUrl.isBlank() -> "/"
            baseUrl.startsWith("/") -> baseUrl
            else -> "/$baseUrl"
        }
        val url = normalizedHost + normalizedBaseUrl
        val normalizedExtra = extra.trimStart('?', '&')
        if (normalizedExtra.isEmpty()) return url
        return when {
            url.endsWith("?") || url.endsWith("&") -> url + normalizedExtra
            '?' in url -> "$url&$normalizedExtra"
            else -> "$url?$normalizedExtra"
        }
    }

    /**
     * 从直播间信息里选一条 HTTP-FLV 流
     *
     * @param codecPriority 编码优先级，默认 AVC > HEVC；同编码内取 `current_qn` 最高的一条
     * @param urlIndex 选取第几个 CDN（`url_info` 顺序），越界时回退到第一个
     * @return 无可播放流（未开播 / 只有 HLS / 结构异常）时返回 null
     */
    fun selectFlvStream(
        data: RoomPlayInfoData,
        codecPriority: List<String> = DEFAULT_CODEC_PRIORITY,
        urlIndex: Int = 0
    ): LiveStreamUrl? = selectStream(
        playUrlInfo = data.playUrlInfo,
        protocolName = PROTOCOL_HTTP_STREAM,
        formatName = FORMAT_FLV,
        codecPriority = codecPriority,
        urlIndex = urlIndex
    )

    /**
     * 按协议/格式/编码优先级选择一条流，规则见 [selectFlvStream]
     */
    fun selectStream(
        playUrlInfo: PlayUrlInfo?,
        protocolName: String,
        formatName: String,
        codecPriority: List<String> = DEFAULT_CODEC_PRIORITY,
        urlIndex: Int = 0
    ): LiveStreamUrl? {
        val playUrl = playUrlInfo?.playurl ?: return null
        val candidates = playUrl.stream
            .filter { it.protocolName == protocolName }
            .flatMap { it.format }
            .filter { it.formatName == formatName }
            .flatMap { it.codec }
            .filter { it.baseUrl.isNotBlank() && it.urlInfo.isNotEmpty() }
        val codec = candidates.sortedWith(
            compareBy<PlayCodec> {
                val priority = codecPriority.indexOf(it.codecName)
                if (priority < 0) codecPriority.size else priority
            }.thenByDescending { it.currentQn }
        ).firstOrNull() ?: return null
        val urlInfo = codec.urlInfo.getOrNull(urlIndex) ?: codec.urlInfo.first()
        val quality = playUrl.qualityDescriptions
            .firstOrNull { it.qn == codec.currentQn && it.hdrType == codec.hdrType }
            ?: playUrl.qualityDescriptions.firstOrNull { it.qn == codec.currentQn }
        return LiveStreamUrl(
            url = buildUrl(urlInfo.host, codec.baseUrl, urlInfo.extra),
            protocolName = protocolName,
            formatName = formatName,
            codecName = codec.codecName,
            qn = codec.currentQn,
            qualityDescription = quality?.desc.orEmpty()
        )
    }
}
