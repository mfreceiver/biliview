package cn.vectory.biliview.entity.live

import cn.vectory.biliview.entity.carddata.LiveCardData

/**
 * 直播列表的一页数据
 *
 * [hasMore] 由数据层判断（接口返回空列表 / 已到最后一页时置 false），
 * ViewModel 不再自己推算。
 */
data class LiveListPage(
    val items: List<LiveCardData> = emptyList(),
    val hasMore: Boolean = false,
)
