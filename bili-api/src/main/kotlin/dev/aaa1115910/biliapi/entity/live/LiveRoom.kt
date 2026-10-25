package dev.aaa1115910.biliapi.entity.live

import dev.aaa1115910.biliapi.http.entity.live.LiveRoomBrief

/**
 * 直播间（列表项）领域模型
 *
 * 与 http DTO 解耦：接口字段变动时只需要改 [Companion.fromBrief]。
 */
data class LiveRoom(
    val roomId: Long,
    val title: String,
    val uname: String,
    val uid: Long,
    val cover: String,
    val face: String,
    val online: Int,
    val areaId: Int,
    val areaName: String,
    val parentAreaId: Int,
    val parentAreaName: String,
    val liveStatus: Boolean = true
) {
    companion object {
        fun fromBrief(item: LiveRoomBrief) = LiveRoom(
            roomId = item.roomId,
            title = item.title,
            uname = item.uname,
            uid = item.uid,
            cover = item.cover,
            face = item.face,
            online = item.online,
            areaId = item.areaId,
            areaName = item.areaName,
            parentAreaId = item.parentAreaId,
            parentAreaName = item.parentAreaName
        )
    }
}

/**
 * 直播列表分页信息
 *
 * [page] 从 1 开始，[nextPageNumber] 即下一页页码。
 */
data class LiveRoomPage(
    val page: Int = 1,
    val pageSize: Int = 30
) {
    val nextPageNumber: Int get() = page + 1
}

/**
 * 一页直播列表
 *
 * [noMore] 由数据层判断（本页条数不足 pageSize / 接口给了 has_more=false），UI 不再自行推算。
 */
data class LiveRoomList(
    val rooms: List<LiveRoom> = emptyList(),
    val page: LiveRoomPage = LiveRoomPage(),
    val noMore: Boolean = false
)
