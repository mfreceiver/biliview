package cn.vectory.biliview.repository

import cn.vectory.biliview.entity.carddata.LiveCardData
import cn.vectory.biliview.entity.live.LiveListPage
import dev.aaa1115910.biliapi.entity.live.LiveRoom
import dev.aaa1115910.biliapi.repositories.LiveRepository as BiliApiLiveRepository
import org.koin.core.annotation.Single

/**
 * 推荐 tab 使用的分区 id 占位值。
 *
 * 真实分区 id 见 [cn.vectory.biliview.tv.component.LiveTopNavItem]，数据层遇到
 * [LIVE_RECOMMEND_AREA_ID] 时应走“首页推荐直播”接口（xlive/web-interface/v1/index/getRecommendLive），
 * 遇到其他值走分区列表接口（xlive/web-interface/v1/second/getList，parent_area_id = areaId）。
 */
const val LIVE_RECOMMEND_AREA_ID = 0

/**
 * 直播列表每页条数
 *
 * 保持保守值：B 站直播接口风控偏紧，避免一次拉取过多。
 */
const val LIVE_PAGE_SIZE = 30

/**
 * 直播列表数据源契约（Lane C 侧定义，Lane A 实现）
 *
 * 仅暴露列表读取，不做任何自动轮询：ViewModel 只在“用户进入 tab / 翻页 / 手动重刷”时调用。
 * [page] 从 1 开始。
 */
interface LiveRepository {

    /**
     * 获取一页直播列表
     *
     * @param areaId [LIVE_RECOMMEND_AREA_ID] 表示推荐，其余为 B 站直播主分区 id
     * @param page 页码，从 1 开始
     */
    suspend fun getLiveList(
        areaId: Int,
        page: Int,
        pageSize: Int = LIVE_PAGE_SIZE
    ): LiveListPage
}

/**
 * 直播列表的真实数据源，内部委托 bili-api 的 [BiliApiLiveRepository]。
 *
 * - [LIVE_RECOMMEND_AREA_ID]：走推荐接口（webMain/getMoreRecList），只有一页，`hasMore = false`
 * - 其他 areaId：走分区接口（index/getList，`parent_area_id = areaId`），
 *   `hasMore = rooms.isNotEmpty()`（该接口不返回 has_more）
 *
 * 错误处理：RiskControlException / IllegalStateException / IO 异常一律向上抛出，
 * 由 [cn.vectory.biliview.viewmodel.live.LiveViewModel.loadMore] 现有的失败路径统一 toast
 * （避免仓库层与 ViewModel 重复提示）。
 *
 * 注：bili-api 侧接口固定每页 30 条，[pageSize] 仅保留在契约里，当前实现不透传。
 */
@Single(binds = [LiveRepository::class])
class BiliLiveRepository(
    private val liveRepository: BiliApiLiveRepository
) : LiveRepository {

    override suspend fun getLiveList(
        areaId: Int,
        page: Int,
        pageSize: Int
    ): LiveListPage {
        if (areaId == LIVE_RECOMMEND_AREA_ID) {
            val rooms = liveRepository.getRecommendRooms()
            return LiveListPage(
                items = rooms.map { it.toCardData() },
                hasMore = false
            )
        }

        val rooms = liveRepository
            .getChannelData(parentAreaId = areaId, page = page)
            .rooms
            // 分区接口按模块（如“热门主播”“正在直播”）返回，同一房间可能出现在多个模块里
            .distinctBy { it.roomId }
        return LiveListPage(
            items = rooms.map { it.toCardData() },
            hasMore = rooms.isNotEmpty()
        )
    }

    private fun LiveRoom.toCardData() = LiveCardData(
        roomId = roomId,
        title = title,
        cover = cover,
        uname = uname,
        online = online,
        areaName = areaName,
        liveStatus = liveStatus
    )
}
