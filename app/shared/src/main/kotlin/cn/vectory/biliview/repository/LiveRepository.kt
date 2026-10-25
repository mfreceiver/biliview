package cn.vectory.biliview.repository

import cn.vectory.biliview.entity.live.LiveListPage
import cn.vectory.biliview.util.fInfo
import io.github.oshai.kotlinlogging.KotlinLogging
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
 * 临时实现：返回空列表。
 *
 * bili-api（Lane A）的直播列表仓库合入后，把 Koin 里的这个绑定换成
 * `BiliLiveRepository : LiveRepository`（内部委托 Lane A 的仓库并做一次
 * `LiveItem -> LiveCardData` 映射）即可，UI 与 [cn.vectory.biliview.viewmodel.live.LiveViewModel] 无需改动。
 */
@Single(binds = [LiveRepository::class])
class EmptyLiveRepository : LiveRepository {
    private val logger = KotlinLogging.logger("EmptyLiveRepository")

    override suspend fun getLiveList(areaId: Int, page: Int, pageSize: Int): LiveListPage {
        logger.fInfo { "live repository not integrated yet, areaId: $areaId, page: $page" }
        return LiveListPage(items = emptyList(), hasMore = false)
    }
}
