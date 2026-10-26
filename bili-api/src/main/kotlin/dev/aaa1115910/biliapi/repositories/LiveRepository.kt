package dev.aaa1115910.biliapi.repositories

import dev.aaa1115910.biliapi.entity.live.LiveChannelData
import dev.aaa1115910.biliapi.entity.live.LiveParentArea
import dev.aaa1115910.biliapi.entity.live.LiveRoom
import dev.aaa1115910.biliapi.entity.live.LiveRoomList
import dev.aaa1115910.biliapi.entity.live.LiveRoomPage
import dev.aaa1115910.biliapi.entity.live.LiveStreamSelector
import dev.aaa1115910.biliapi.entity.live.LiveStreamUrl
import dev.aaa1115910.biliapi.http.BiliLiveHttpApi
import dev.aaa1115910.biliapi.http.entity.live.RoomPlayInfoData
import org.koin.core.annotation.Single

/**
 * 直播列表默认每页条数
 *
 * 保守值：B 站直播接口风控偏紧，避免一次拉取过多。
 */
const val LIVE_DEFAULT_PAGE_SIZE = 30

/**
 * 默认请求画质：10000（原画）
 *
 * 未登录/无权限时服务端会回落到可用画质，真实画质看 [LiveStreamUrl.qn]。
 */
const val LIVE_DEFAULT_QN = 10000

/**
 * 首页接口 `room_list` 中「推荐直播」模块的 module_info.id
 */
const val HOME_RECOMMEND_MODULE_ID = 3

/**
 * 直播数据仓库：房间详情、播放地址、列表三组能力
 *
 * 风控约定：所有 HTTP 响应统一走 `getResponseData()`，
 * -352 会抛 `RiskControlException`，不会被静默吞成空列表，由调用方 toast 提示。
 * 本仓库不含任何轮询/重试/批量请求逻辑。
 */
@Single
class LiveRepository(
    private val authRepository: AuthRepository
) {
    /**
     * 获取直播间详情（含 `playurl_info`）
     *
     * @param qn 请求画质，见 [LIVE_DEFAULT_QN]
     */
    suspend fun getRoomInfo(
        roomId: Int,
        qn: Int = LIVE_DEFAULT_QN
    ): RoomPlayInfoData =
        BiliLiveHttpApi.getLiveRoomPlayInfo(
            roomId = roomId,
            qn = qn,
            buvid3 = authRepository.buvid3
        ).getResponseData()

    /**
     * 获取一条可播放的 HTTP-FLV 地址
     *
     * 封装了 `url_info.host + base_url + url_info.extra` 的拼接与编码/画质选择策略，
     * 详见 [LiveStreamSelector]。当前播放器只有 ProgressiveMediaSource，故只取 FLV。
     *
     * 注意：本方法内部会请求一次 `getRoomPlayInfo`；调用方若已经拿到 [RoomPlayInfoData]
     * （例如播放页要先读 `live_status` / `g_qn_desc`），应改用 [getLiveStreamUrl] 的
     * `RoomPlayInfoData` 重载复用同一份数据，避免同参数接口打两次。
     *
     * @param codecPriority 编码优先级，默认 AVC > HEVC
     * @param urlIndex 选第几个 CDN，越界回退第一个
     * @throws dev.aaa1115910.biliapi.http.entity.RiskControlException 命中 -352 风控
     * @throws IllegalStateException 未开播或没有可用的 FLV 流
     */
    suspend fun getLiveStreamUrl(
        roomId: Int,
        qn: Int = LIVE_DEFAULT_QN,
        codecPriority: List<String> = LiveStreamSelector.DEFAULT_CODEC_PRIORITY,
        urlIndex: Int = 0
    ): LiveStreamUrl = getLiveStreamUrl(
        roomInfo = getRoomInfo(roomId = roomId, qn = qn),
        codecPriority = codecPriority,
        urlIndex = urlIndex
    )

    /**
     * 基于已取到的房间详情选一条可播放的 HTTP-FLV 地址（纯函数，不发请求）
     *
     * 供「先读房间详情、再选流」的调用方复用同一份 `playurl_info`：
     * `getRoomPlayInfo` 同参数连打两次既浪费又更容易触发风控。
     *
     * @param codecPriority 编码优先级，默认 AVC > HEVC
     * @param urlIndex 选第几个 CDN，越界回退第一个
     * @throws IllegalStateException 未开播或没有可用的 FLV 流
     */
    fun getLiveStreamUrl(
        roomInfo: RoomPlayInfoData,
        codecPriority: List<String> = LiveStreamSelector.DEFAULT_CODEC_PRIORITY,
        urlIndex: Int = 0
    ): LiveStreamUrl =
        LiveStreamSelector.selectFlvStream(
            data = roomInfo,
            codecPriority = codecPriority,
            urlIndex = urlIndex
        ) ?: throw IllegalStateException(
            "直播间 ${roomInfo.roomId} 没有可用的 FLV 流，liveStatus=${roomInfo.liveStatus}"
        )

    /**
     * 推荐/热门直播列表（`webMain/getMoreRecList`）
     */
    suspend fun getRecommendRooms(): List<LiveRoom> =
        BiliLiveHttpApi.getLiveRecommendList(buvid3 = authRepository.buvid3)
            .getResponseData()
            .recommendRoomList
            .map { LiveRoom.fromBrief(it) }

    /**
     * 首页个性化推荐直播列表（`index/getList` 无 parent_area_id 形态）
     *
     * 与 [getRecommendRooms]（getMoreRecList 分页端点）不同：这是网页直播首页首屏接口，
     * 带 SESSDATA 预期个性化推荐（服务器行为）；无分页语义，`hasMore` 恒 false 由调用方处理。
     *
     * 条数：响应顶层 `recommend_room_list` 仅约 5 条，同响应 `room_list` 中
     * `module_info.id == 3`「推荐直播」模块约 10 条（其前 5 条即 recommend_room_list），
     * 两者合并后按 roomId 去重，约 10 条、零额外请求；仍可能少于 getMoreRecList 旧路径
     * 的 12 条，接受此差异。
     *
     * 注：首页接口的房间 item 无 `uid` 字段，[LiveRoom.uid] 恒为 0（列表卡片不使用该字段）。
     */
    suspend fun getHomeRecommendRooms(): List<LiveRoom> {
        val data = BiliLiveHttpApi.getLiveHomeList(
            sessdata = authRepository.sessionData,
            buvid3 = authRepository.buvid3
        ).getResponseData()
        return (data.recommendRoomList.asSequence() + data.roomList
            .asSequence()
            .filter { it.moduleInfo.id == HOME_RECOMMEND_MODULE_ID }
            .flatMap { it.list.asSequence() })
            .distinctBy { it.roomId }
            .map { LiveRoom.fromBrief(it) }
            .toList()
    }

    /**
     * 直播频道页（`index/getList`，按主分区模块化聚合）
     *
     * 这是当前唯一实测可用的「按分区」列表：`page` 有效，但没有 `has_more`，
     * 调用方按 [LiveChannelData.rooms] 是否为空判断是否继续翻页。
     *
     * @param parentAreaId 1 娱乐、2 网游、3 手游、5 电台、9 虚拟主播
     * @param areaId 二级分区 id，0 不筛选
     */
    suspend fun getChannelData(
        parentAreaId: Int,
        page: Int = 1,
        areaId: Int = 0
    ): LiveChannelData {
        val data = BiliLiveHttpApi.getLiveIndexList(
            parentAreaId = parentAreaId,
            areaId = areaId,
            page = page,
            buvid3 = authRepository.buvid3
        ).getResponseData()
        return LiveChannelData.fromIndexListData(
            parentAreaId = parentAreaId,
            page = page,
            data = data
        )
    }

    /**
     * 按分区获取平铺直播列表（`second/getList`）
     *
     * 实测警告：2026-10-25 该端点恒返回 -352（见 [BiliLiveHttpApi.getLiveListByArea]），
     * 会抛 `RiskControlException`；频道页场景请优先用 [getChannelData]。
     * [noMore] 按「本页条数 < 服务端 pageSize」推断（响应里没有可用的 has_more）。
     */
    suspend fun getRoomsByArea(
        parentAreaId: Int,
        areaId: Int = 0,
        page: Int = 1,
        pageSize: Int = LIVE_DEFAULT_PAGE_SIZE
    ): LiveRoomList {
        val data = BiliLiveHttpApi.getLiveListByArea(
            parentAreaId = parentAreaId,
            areaId = areaId,
            page = page,
            pageSize = pageSize,
            buvid3 = authRepository.buvid3
        ).getResponseData()
        val effectivePageSize = data.pageSize.takeIf { it > 0 } ?: pageSize
        return LiveRoomList(
            rooms = data.list.map { LiveRoom.fromBrief(it) },
            page = LiveRoomPage(
                page = data.page.takeIf { it > 0 } ?: page,
                pageSize = effectivePageSize
            ),
            noMore = data.list.size < effectivePageSize
        )
    }

    /**
     * 直播分区字典（父分区 + 二级分区）
     */
    suspend fun getAreaList(): List<LiveParentArea> =
        BiliLiveHttpApi.getLiveAreaList()
            .getResponseData()
            .map { LiveParentArea.fromParentAreaData(it) }
}
