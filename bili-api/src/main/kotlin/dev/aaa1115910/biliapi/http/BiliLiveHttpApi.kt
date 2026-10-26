package dev.aaa1115910.biliapi.http

import dev.aaa1115910.biliapi.http.entity.BiliResponse
import dev.aaa1115910.biliapi.http.entity.live.DanmuInfoData
import dev.aaa1115910.biliapi.http.entity.live.HistoryDanmaku
import dev.aaa1115910.biliapi.http.entity.live.LiveIndexListData
import dev.aaa1115910.biliapi.http.entity.live.LiveParentAreaData
import dev.aaa1115910.biliapi.http.entity.live.LiveRecommendListData
import dev.aaa1115910.biliapi.http.entity.live.LiveSecondListData
import dev.aaa1115910.biliapi.http.entity.live.RoomPlayInfoData
import dev.aaa1115910.biliapi.http.plugins.BiliUserAgent
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.http.URLProtocol
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object BiliLiveHttpApi {
    private var endPoint: String = ""
    private lateinit var client: HttpClient
    private val logger = KotlinLogging.logger { }

    /**
     * 直播接口结构化诊断日志：统一前缀 `LiveAPI`，logcat / LogCatcherUtil 导出文件里
     * 都能 `grep LiveAPI`。记录 端点、关键参数、HTTP 状态、业务 code/message、异常。
     */
    private val liveApiLogger = KotlinLogging.logger("LiveAPI")

    init {
        createClient()
    }

    private fun createClient() {        client = HttpClient(OkHttp) {
            BiliUserAgent()
            install(ContentNegotiation) {
                json(Json {
                    coerceInputValues = true
                    ignoreUnknownKeys = true
                    prettyPrint = true
                })
            }
            install(ContentEncoding) {
                deflate(1.0F)
                gzip(0.9F)
            }
            defaultRequest {
                url {
                    host = "api.live.bilibili.com"
                    protocol = URLProtocol.HTTPS
                }
            }
        }
    }

    /**
     * 带 `LiveAPI` 结构化日志的 GET：请求异常 / 解析异常 / 业务 code≠0 全部记录后再抛出/返回，
     * 不改变任何既有行为（-352 等仍由 `getResponseData()` 转异常）。
     */
    private suspend inline fun <reified T> loggedGet(
        endpoint: String,
        params: String,
        crossinline build: HttpRequestBuilder.() -> Unit
    ): BiliResponse<T> {
        val response: HttpResponse = try {
            client.get(endpoint) { build() }
        } catch (t: Throwable) {
            liveApiLogger.warn(t) {
                "LiveAPI http failed: endpoint=$endpoint params=[$params] error=${t.javaClass.name}: ${t.message}"
            }
            throw t
        }
        val body: BiliResponse<T> = try {
            response.body()
        } catch (t: Throwable) {
            liveApiLogger.warn(t) {
                "LiveAPI parse failed: endpoint=$endpoint params=[$params] http=${response.status.value} error=${t.javaClass.name}: ${t.message}"
            }
            throw t
        }
        if (body.code == 0) {
            liveApiLogger.info {
                "LiveAPI ok: endpoint=$endpoint params=[$params] http=${response.status.value} code=0 message=${body.message}"
            }
        } else {
            liveApiLogger.warn {
                "LiveAPI biz error: endpoint=$endpoint params=[$params] http=${response.status.value} code=${body.code} message=${body.message} dataNull=${body.data == null}"
            }
        }
        return body
    }

    /**
     * 获取直播间[roomId]的弹幕连接地址等信息，例如 token
     */
    suspend fun getLiveDanmuInfo(roomId: Int): BiliResponse<DanmuInfoData> =
        client.get("/xlive/web-room/v1/index/getDanmuInfo") {
            parameter("id", roomId)
        }.body()

    /**
     * 获取直播间[roomId]的信息
     */
    suspend fun getLiveRoomPlayInfo(roomId: Int): BiliResponse<RoomPlayInfoData> =
        client.get("/xlive/web-room/v1/index/getRoomPlayInfo") {
            parameter("room_id", roomId)
        }.body()

    /**
     * 获取直播间[roomId]的信息，包含 `playurl_info` 里的直播流地址
     *
     * 使用 v2 接口：v1 的 `play_url` 在直播场景恒为 null，只有 v2 返回 `playurl_info`。
     *
     * @param protocol 0=http_stream(FLV)、1=http_hls，可逗号多选
     * @param format 0=flv、1=ts、2=fmp4，可逗号多选
     * @param codec 0=AVC、1=HEVC，可逗号多选
     * @param qn 请求的画质，默认 10000（原画）；匿名用户实际会回落到可用画质，
     *           真实画质看 `PlayCodec.currentQn`
     * @param platform 固定 `web`
     * @param ptype 网页端固定 8
     * @param buvid3 可选，来自 `AuthRepository`；实测裸请求可用，携带仅用于降低风控概率
     */
    suspend fun getLiveRoomPlayInfo(
        roomId: Int,
        protocol: String = "0,1",
        format: String = "0,1,2",
        codec: String = "0,1",
        qn: Int = 10000,
        platform: String = "web",
        ptype: Int = 8,
        buvid3: String? = null
    ): BiliResponse<RoomPlayInfoData> = loggedGet<RoomPlayInfoData>(
        endpoint = "/xlive/web-room/v2/index/getRoomPlayInfo",
        params = "room_id=$roomId, qn=$qn, protocol=$protocol, format=$format, codec=$codec, buvid3=${!buvid3.isNullOrBlank()}"
    ) {
        parameter("room_id", roomId)
            parameter("protocol", protocol)
            parameter("format", format)
            parameter("codec", codec)
            parameter("qn", qn)
            parameter("platform", platform)
            parameter("ptype", ptype)
            if (!buvid3.isNullOrBlank()) header("Cookie", "buvid3=$buvid3;")
        }

    /**
     * 获取推荐/热门直播列表（网页端直播首页的「推荐直播」）
     *
     * 参考社区文档 `webMain/getMoreRecList`；风控失败时由 `getResponseData()` 抛
     * `RiskControlException`（-352），调用方 toast。
     */
    suspend fun getLiveRecommendList(
        platform: String = "web",
        buvid3: String? = null
    ): BiliResponse<LiveRecommendListData> = loggedGet<LiveRecommendListData>(
        endpoint = "/xlive/web-interface/v1/webMain/getMoreRecList",
        params = "platform=$platform, buvid3=${!buvid3.isNullOrBlank()}"
    ) {
        parameter("platform", platform)
            if (!buvid3.isNullOrBlank()) header("Cookie", "buvid3=$buvid3;")
        }

    /**
     * 获取直播频道页（按主分区聚合的模块化房间列表）
     *
     * 端点 `/xlive/web-interface/v1/index/getList` 未被社区文档收录，字段按 2026-10-25 实际响应建模；
     * `page` 有效（不同页返回不同房间），但响应没有 `has_more`。
     *
     * @param parentAreaId 主分区 id，如 1 娱乐、2 网游、3 手游、5 电台、9 虚拟主播
     * @param areaId 二级分区 id，0 表示不筛选
     */
    suspend fun getLiveIndexList(
        parentAreaId: Int,
        areaId: Int = 0,
        page: Int = 1,
        platform: String = "web",
        buvid3: String? = null
    ): BiliResponse<LiveIndexListData> = loggedGet<LiveIndexListData>(
        endpoint = "/xlive/web-interface/v1/index/getList",
        params = "parent_area_id=$parentAreaId, area_id=$areaId, page=$page, platform=$platform, buvid3=${!buvid3.isNullOrBlank()}"
    ) {
        parameter("platform", platform)
            parameter("parent_area_id", parentAreaId)
            parameter("page", page)
            if (areaId > 0) parameter("area_id", areaId)
            if (!buvid3.isNullOrBlank()) header("Cookie", "buvid3=$buvid3;")
        }

    /**
     * 按分区获取平铺的直播列表（参考社区文档 `second/getList` 语义）
     *
     * 实测警告：2026-10-25 本机裸请求（无 Cookie / 带 buvid3 / 完整 Referer）均返回
     * `{"code":-352}`，疑似需要 WBI 签名或受 IP 风控限制。这里按文档建模并通过
     * `getResponseData()` 把 -352 转成 `RiskControlException`，不静默吞掉；
     * TODO 待可用环境校准字段（尤其 `has_more`、`totalPage`）。
     */
    suspend fun getLiveListByArea(
        parentAreaId: Int,
        areaId: Int = 0,
        page: Int = 1,
        pageSize: Int = 30,
        sortType: String = "online",
        platform: String = "web",
        buvid3: String? = null
    ): BiliResponse<LiveSecondListData> = loggedGet<LiveSecondListData>(
        endpoint = "/xlive/web-interface/v1/second/getList",
        params = "parent_area_id=$parentAreaId, area_id=$areaId, page=$page, page_size=$pageSize, sort_type=$sortType, platform=$platform, buvid3=${!buvid3.isNullOrBlank()}"
    ) {
        parameter("platform", platform)
            parameter("parent_area_id", parentAreaId)
            parameter("area_id", areaId)
            parameter("sort_type", sortType)
            parameter("page", page)
            parameter("page_size", pageSize)
            if (!buvid3.isNullOrBlank()) header("Cookie", "buvid3=$buvid3;")
        }

    /**
     * 获取直播分区字典（父分区 + 二级分区）
     *
     * 该接口的 `data` 直接是父分区数组。
     */
    suspend fun getLiveAreaList(): BiliResponse<List<LiveParentAreaData>> =
        client.get("/room/v1/Area/getList").body()

    /**
     * 获取直播间[roomId]的历史弹幕
     */
    suspend fun getLiveDanmuHistory(roomId: Int): BiliResponse<HistoryDanmaku> =
        client.get("/xlive/web-room/v1/dM/gethistory") {
            parameter("roomid", roomId)
        }.body()

}