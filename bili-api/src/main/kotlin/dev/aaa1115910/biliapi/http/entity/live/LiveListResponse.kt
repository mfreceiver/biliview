package dev.aaa1115910.biliapi.http.entity.live

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 直播间列表项
 *
 * 推荐列表（`webMain/getMoreRecList`）、频道页（`index/getList`）、分区列表（`second/getList`）
 * 的房间项字段基本同构，统一用这个 DTO 承接；未建模的字段由 Ktor 的 `ignoreUnknownKeys` 忽略。
 *
 * 字段语义以实际响应为准（2026-10-25 校准）。
 */
@Serializable
data class LiveRoomBrief(
    @SerialName("roomid")
    val roomId: Long = 0,
    val title: String = "",
    val uname: String = "",
    val uid: Long = 0,
    val cover: String = "",
    val face: String = "",
    @SerialName("online")
    val online: Int = 0,
    /**
     * 直播间相对链接，如 `/1968488810?hotRank=0`
     */
    val link: String = "",
    @SerialName("area_v2_id")
    val areaId: Int = 0,
    @SerialName("area_v2_name")
    val areaName: String = "",
    @SerialName("area_v2_parent_id")
    val parentAreaId: Int = 0,
    @SerialName("area_v2_parent_name")
    val parentAreaName: String = "",
    @SerialName("broadcast_type")
    val broadcastType: Int = 0,
    @SerialName("watched_show")
    val watchedShow: WatchedShow? = null,
    @SerialName("is_ad")
    val isAd: Boolean = false,
    val keyframe: String = ""
)

/**
 * 观看人数展示信息（`watched_show`），列表页不一定使用，保留给 UI 取人气文案
 */
@Serializable
data class WatchedShow(
    @SerialName("switch")
    val switch: Boolean = false,
    val num: Long = 0,
    @SerialName("text_small")
    val textSmall: String = "",
    @SerialName("text_large")
    val textLarge: String = "",
    val icon: String = "",
    @SerialName("icon_location")
    val iconLocation: Int = 0
)

/**
 * 推荐/热门直播列表（`/xlive/web-interface/v1/webMain/getMoreRecList`）
 */
@Serializable
data class LiveRecommendListData(
    @SerialName("recommend_room_list")
    val recommendRoomList: List<LiveRoomBrief> = emptyList(),
    @SerialName("top_room_id")
    val topRoomId: Long = 0
)

/**
 * 频道页数据（`/xlive/web-interface/v1/index/getList`）
 *
 * 社区文档未收录该端点，字段按实际响应建模；`page` 有效（不同页码返回不同房间），
 * 但响应里没有 `has_more`，调用方只能按“本页非空”判断是否继续翻页。
 */
@Serializable
data class LiveIndexListData(
    @SerialName("area_entrance_v2")
    val areaEntrance: LiveAreaEntrance = LiveAreaEntrance(),
    /**
     * 按模块（推荐直播/聊天室/单机游戏/…）分组的房间列表，每页约 11 个模块 × 10 个房间
     */
    @SerialName("room_list")
    val roomList: List<LiveModuleData> = emptyList(),
    @SerialName("recommend_room_list")
    val recommendRoomList: List<LiveRoomBrief> = emptyList(),
    val banner: List<LiveBannerData> = emptyList(),
    @SerialName("cover_source")
    val coverSource: String = ""
)

@Serializable
data class LiveModuleData(
    @SerialName("module_info")
    val moduleInfo: LiveModuleInfo = LiveModuleInfo(),
    val list: List<LiveRoomBrief> = emptyList()
)

@Serializable
data class LiveModuleInfo(
    val id: Int = 0,
    val title: String = "",
    val link: String = "",
    val pic: String = "",
    val type: Int = 0,
    val sort: Int = 0,
    val count: Int = 0
)

@Serializable
data class LiveAreaEntrance(
    val list: List<LiveAreaEntranceItem> = emptyList()
)

@Serializable
data class LiveAreaEntranceItem(
    val id: Int = 0,
    val link: String = "",
    val pic: String = "",
    val title: String = "",
    @SerialName("area_v2_id")
    val areaId: Int = 0,
    @SerialName("area_v2_parent_id")
    val parentAreaId: Int = 0,
    @SerialName("tag_type")
    val tagType: Int = 0,
    @SerialName("is_hot")
    val isHot: Int = 0
)

@Serializable
data class LiveBannerData(
    val id: Int = 0,
    val title: String = "",
    val pic: String = "",
    val link: String = "",
    val position: Int = 0,
    val location: String = "",
    val weight: Int = 0,
    @SerialName("room_id")
    val roomId: Long = 0,
    @SerialName("parent_area_id")
    val parentAreaId: Int = 0,
    @SerialName("area_id")
    val areaId: Int = 0,
    @SerialName("live_status")
    val liveStatus: Int = 0,
    @SerialName("is_ad")
    val isAd: Boolean = false
)

/**
 * 按分区平铺列表（`/xlive/web-interface/v1/second/getList`）
 *
 * TODO 校准：本机实测该端点恒返回 `-352`（疑似需要 WBI 签名 / IP 风控），
 * 这里按社区文档建模，仅保留确定会用到的字段；`has_more` 在文档里是整型，
 * 为避免响应类型变化导致反序列化崩溃，暂不建模，改由调用方按“本页条数 < pageSize”判断。
 */
@Serializable
data class LiveSecondListData(
    val list: List<LiveRoomBrief> = emptyList(),
    val count: Int = 0,
    val page: Int = 1,
    @SerialName("page_size")
    val pageSize: Int = 30
)

/**
 * 分区字典（`/room/v1/Area/getList` 的 data，直接是父分区数组）
 */
@Serializable
data class LiveParentAreaData(
    val id: Int = 0,
    val name: String = "",
    val list: List<LiveAreaData> = emptyList()
)

@Serializable
data class LiveAreaData(
    val id: String = "",
    @SerialName("parent_id")
    val parentId: String = "",
    val name: String = "",
    val pic: String = "",
    @SerialName("hot_status")
    val hotStatus: Int = 0,
    @SerialName("lock_status")
    val lockStatus: String = "",
    @SerialName("parent_name")
    val parentName: String = "",
    @SerialName("area_type")
    val areaType: Int = 0
)
