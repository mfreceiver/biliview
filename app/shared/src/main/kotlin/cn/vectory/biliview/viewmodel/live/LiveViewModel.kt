package cn.vectory.biliview.viewmodel.live

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.vectory.biliview.BVApp
import cn.vectory.biliview.entity.carddata.LiveCardData
import cn.vectory.biliview.repository.LiveRepository
import cn.vectory.biliview.util.addAllWithMainContext
import cn.vectory.biliview.util.fError
import cn.vectory.biliview.util.fInfo
import cn.vectory.biliview.util.toast
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel
import java.util.concurrent.ConcurrentHashMap

/**
 * 单个直播列表（一个 tab）的分页状态
 *
 * 每个分区一份，互相独立；只由 [LiveViewModel] 写入，UI 只读。
 */
@Stable
class LiveListState {
    internal val mutex = Mutex()

    val items = mutableStateListOf<LiveCardData>()

    var loading by mutableStateOf(false)
        internal set

    var hasMore by mutableStateOf(true)
        internal set

    /** 已加载到的页码，下一页是 loadedPage + 1 */
    var loadedPage by mutableStateOf(0)
        internal set

    /** 是否已经完成过一次请求（含失败），用于区分“空数据”和“还没加载” */
    var initialized by mutableStateOf(false)
        internal set
}

/**
 * 直播列表 ViewModel
 *
 * 风控纪律：不做任何自动轮询/定时刷新，只有 UI 侧“进入 tab、翻页、重新点击 tab”
 * 三类用户动作会触发请求（见 [loadMore] / [refresh] 的调用点）。
 */
@KoinViewModel
class LiveViewModel(
    private val liveRepository: LiveRepository
) : ViewModel() {
    private val logger = KotlinLogging.logger("LiveViewModel")

    /**
     * 按分区 id 保存各自的分页状态。
     *
     * 用普通线程安全 Map 而不是 SnapshotStateMap：状态的“增删”不需要触发重组，
     * 列表内容本身是 SnapshotStateList，读到它就会订阅。
     */
    private val listStates = ConcurrentHashMap<Int, LiveListState>()

    fun state(areaId: Int): LiveListState = listStates.computeIfAbsent(areaId) { LiveListState() }

    /**
     * 加载下一页
     *
     * 由 UI 在 Dispatchers.IO 上调用；同一个列表的并发调用会被 [LiveListState.mutex] 挡住，
     * 避免“首次进入 + 焦点触发加载”叠在一起重复请求。
     */
    suspend fun loadMore(areaId: Int) {
        val state = state(areaId)
        if (!state.mutex.tryLock()) return
        try {
            if (!state.hasMore) return
            state.loading = true
            val page = state.loadedPage + 1
            logger.fInfo { "load live list, areaId: $areaId, page: $page" }
            runCatching {
                liveRepository.getLiveList(areaId = areaId, page = page)
            }.onSuccess { data ->
                state.items.addAllWithMainContext(data.items)
                state.loadedPage = page
                state.hasMore = data.hasMore && data.items.isNotEmpty()
            }.onFailure {
                logger.fError { "load live list failed, areaId: $areaId, page: $page: ${it.stackTraceToString()}" }
                withContext(Dispatchers.Main) {
                    "加载直播列表失败: ${it.localizedMessage}".toast(BVApp.context)
                }
            }
            state.initialized = true
        } finally {
            state.loading = false
            state.mutex.unlock()
        }
    }

    /**
     * 首刷 / 重新点击当前 tab：清空后从第一页重新加载
     */
    fun refresh(areaId: Int) {
        logger.fInfo { "refresh live list, areaId: $areaId" }
        viewModelScope.launch(Dispatchers.IO) {
            val state = state(areaId)
            state.mutex.withLock {
                state.items.clear()
                state.loadedPage = 0
                state.hasMore = true
                state.initialized = false
            }
            loadMore(areaId)
        }
    }
}
