package dev.aaa1115910.biliapi.entity.live

import dev.aaa1115910.biliapi.http.entity.live.LiveParentAreaData

/**
 * 直播二级分区（`room/v1/Area/getList` 的 list 项）
 */
data class LiveArea(
    val id: Int,
    val name: String,
    val pic: String = "",
    val hot: Boolean = false
)

/**
 * 直播主分区（parent area），如 1 娱乐、2 网游、3 手游、5 电台、9 虚拟主播
 */
data class LiveParentArea(
    val id: Int,
    val name: String,
    val areas: List<LiveArea> = emptyList()
) {
    companion object {
        fun fromParentAreaData(data: LiveParentAreaData) = LiveParentArea(
            id = data.id,
            name = data.name,
            areas = data.list.mapNotNull { area ->
                val areaId = area.id.toIntOrNull() ?: return@mapNotNull null
                LiveArea(
                    id = areaId,
                    name = area.name,
                    pic = area.pic,
                    hot = area.hotStatus != 0
                )
            }
        )
    }
}
