package cn.vectory.biliview.entity.carddata

/**
 * 直播卡片 UI 模型
 *
 * 刻意与 bili-api 的直播 DTO 解耦：数据层（Lane A）的实体换名/换字段时，
 * 只需要在 [cn.vectory.biliview.repository.LiveRepository] 的实现里做一次映射，
 * UI 与 ViewModel 不受影响。
 */
data class LiveCardData(
    val roomId: Long,
    val title: String,
    val cover: String,
    val uname: String,
    val online: Int? = null,
    val areaName: String = "",
    val liveStatus: Boolean = true,
) {
    /** 在线人气，与 [VideoCardData.playString] 保持同一种“万”的写法 */
    val onlineString: String = when {
        online == null || online < 0 -> ""
        online >= 10000 -> "${online / 10000}万"
        else -> "$online"
    }
}
