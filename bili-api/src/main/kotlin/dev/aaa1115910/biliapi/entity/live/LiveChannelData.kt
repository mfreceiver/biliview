package dev.aaa1115910.biliapi.entity.live

import dev.aaa1115910.biliapi.http.entity.live.LiveAreaEntranceItem
import dev.aaa1115910.biliapi.http.entity.live.LiveBannerData
import dev.aaa1115910.biliapi.http.entity.live.LiveIndexListData
import dev.aaa1115910.biliapi.http.entity.live.LiveModuleData

/**
 * 频道页的一个模块（Recommend 页的一行），如「推荐直播」「聊天室」「单机游戏」
 */
data class LiveRoomModule(
    val id: Int = 0,
    val title: String,
    val link: String = "",
    val count: Int = 0,
    val rooms: List<LiveRoom> = emptyList()
) {
    companion object {
        fun fromModuleData(data: LiveModuleData) = LiveRoomModule(
            id = data.moduleInfo.id,
            title = data.moduleInfo.title,
            link = data.moduleInfo.link,
            count = data.moduleInfo.count,
            rooms = data.list.map(LiveRoom::fromBrief)
        )
    }
}

/**
 * 频道页顶部的分区入口（`area_entrance_v2`），用于二级分区筛选
 */
data class LiveAreaTag(
    val id: Int,
    val title: String,
    val pic: String,
    val link: String,
    val areaId: Int,
    val parentAreaId: Int
) {
    companion object {
        fun fromEntranceItem(item: LiveAreaEntranceItem) = LiveAreaTag(
            id = item.id,
            title = item.title,
            pic = item.pic,
            link = item.link,
            areaId = item.areaId,
            parentAreaId = item.parentAreaId
        )
    }
}

/**
 * 频道页轮播 banner（`banner`）
 */
data class LiveBanner(
    val id: Int,
    val title: String,
    val pic: String,
    val link: String,
    val roomId: Long,
    val parentAreaId: Int,
    val areaId: Int
) {
    companion object {
        fun fromBannerData(data: LiveBannerData) = LiveBanner(
            id = data.id,
            title = data.title,
            pic = data.pic,
            link = data.link,
            roomId = data.roomId,
            parentAreaId = data.parentAreaId,
            areaId = data.areaId
        )
    }
}

/**
 * 直播频道页数据（`index/getList` 的一页）
 *
 * 该接口没有 `has_more`，[nextPage] 是否还有数据只能由调用方按返回房间是否为空判断。
 */
data class LiveChannelData(
    val parentAreaId: Int,
    val page: Int,
    val modules: List<LiveRoomModule> = emptyList(),
    val recommendRooms: List<LiveRoom> = emptyList(),
    val areaTags: List<LiveAreaTag> = emptyList(),
    val banners: List<LiveBanner> = emptyList()
) {
    val nextPage: Int get() = page + 1

    /**
     * 所有模块拍平后的房间（按模块顺序）
     */
    val rooms: List<LiveRoom> get() = modules.flatMap { it.rooms }

    companion object {
        fun fromIndexListData(parentAreaId: Int, page: Int, data: LiveIndexListData) = LiveChannelData(
            parentAreaId = parentAreaId,
            page = page,
            modules = data.roomList.map { LiveRoomModule.fromModuleData(it) },
            recommendRooms = data.recommendRoomList.map { LiveRoom.fromBrief(it) },
            areaTags = data.areaEntrance.list.map { LiveAreaTag.fromEntranceItem(it) },
            banners = data.banner.map { LiveBanner.fromBannerData(it) }
        )
    }
}
