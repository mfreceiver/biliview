package dev.aaa1115910.biliapi.http.entity.live

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 直播流地址信息，对应 `playurl_info`
 *
 * 参考 bilibili-API-collect `docs/live/info.md`（v2 getRoomPlayInfo）与实际响应建模。
 * 层级关系：`playurl_info -> playurl -> stream(protocol) -> format -> codec -> url_info(CDN)`
 */
@Serializable
data class PlayUrlInfo(
    @SerialName("conf_json")
    val confJson: String = "",
    val playurl: PlayUrl? = null
)

@Serializable
data class PlayUrl(
    val cid: Long = 0,
    @SerialName("g_qn_desc")
    val qualityDescriptions: List<QualityDescription> = emptyList(),
    val stream: List<PlayStream> = emptyList()
)

@Serializable
data class QualityDescription(
    val qn: Int = 0,
    val desc: String = "",
    @SerialName("hdr_desc")
    val hdrDesc: String = "",
    @SerialName("hdr_type")
    val hdrType: Int = 0
)

@Serializable
data class PlayStream(
    @SerialName("protocol_name")
    val protocolName: String = "",
    val format: List<PlayFormat> = emptyList()
)

@Serializable
data class PlayFormat(
    @SerialName("format_name")
    val formatName: String = "",
    val codec: List<PlayCodec> = emptyList()
)

@Serializable
data class PlayCodec(
    @SerialName("codec_name")
    val codecName: String = "",
    /**
     * 当前实际下发的画质（匿名用户会低于请求的 qn，例如请求 10000 实际给 250）
     */
    @SerialName("current_qn")
    val currentQn: Int = 0,
    @SerialName("accept_qn")
    val acceptQn: List<Int> = emptyList(),
    @SerialName("base_url")
    val baseUrl: String = "",
    @SerialName("url_info")
    val urlInfo: List<PlayUrlHost> = emptyList(),
    /**
     * 注意：实际响应是数组（如 `[]`），不是文档里写的整型
     */
    @SerialName("hdr_qn")
    val hdrQn: List<Int> = emptyList(),
    @SerialName("dolby_type")
    val dolbyType: Int = 0,
    @SerialName("attr_name")
    val attrName: String = "",
    @SerialName("hdr_type")
    val hdrType: Int = 0
)

@Serializable
data class PlayUrlHost(
    val host: String = "",
    val extra: String = "",
    @SerialName("stream_ttl")
    val streamTtl: Int = 0
)
