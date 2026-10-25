package cn.vectory.biliview.viewmodel.live

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.vectory.biliview.BVApp
import cn.vectory.biliview.player.VideoPlayerListener
import cn.vectory.biliview.player.VideoPlayerOptions
import cn.vectory.biliview.player.impl.exo.ExoMediaPlayer
import cn.vectory.biliview.player.impl.exo.ExoPlayerFactory
import cn.vectory.biliview.util.Prefs
import cn.vectory.biliview.util.fError
import cn.vectory.biliview.util.fInfo
import cn.vectory.biliview.util.toast
import com.kuaishou.akdanmaku.DanmakuConfig
import com.kuaishou.akdanmaku.data.DanmakuItemData
import com.kuaishou.akdanmaku.render.SimpleRenderer
import com.kuaishou.akdanmaku.ui.DanmakuPlayer
import dev.aaa1115910.biliapi.entity.live.LiveStreamUrl
import dev.aaa1115910.biliapi.http.entity.RiskControlException
import dev.aaa1115910.biliapi.http.plugins.BiliUserAgentConfig
import dev.aaa1115910.biliapi.repositories.AuthRepository
import dev.aaa1115910.biliapi.repositories.LIVE_DEFAULT_QN
import dev.aaa1115910.biliapi.repositories.LiveRepository
import dev.aaa1115910.biliapi.websocket.BiliLiveRoomInfoProvider
import dev.aaa1115910.biliapi.websocket.LiveDataWebSocket
import dev.aaa1115910.biliapi.websocket.LiveEvent
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel

/** 直播清晰度选项（qn 与接口 `g_qn_desc` 一一对应） */
data class LiveQuality(
    val qn: Int,
    val description: String
)

/**
 * 直播播放页 ViewModel
 *
 * 与 [cn.vectory.biliview.viewmodel.VideoPlayerV3ViewModel] 的差异：直播没有进度条 / 倍速 / 选集，
 * 因此这里只保留「进房取流 → 播放」「弹幕 WebSocket」「清晰度切换」三件事，控制器由
 * [cn.vectory.biliview.tv.screens.live.LivePlayerScreen] 自己画。
 *
 * 风控纪律：除进房与用户主动切换清晰度 / 重试外，不发起任何请求（不轮询人气，人气靠弹幕心跳回包）。
 *
 * 生命周期（由 Activity 转发）：
 * - [start]：创建播放器 / 弹幕引擎并加载直播流（仅一次）；取流成功后按当前生命周期状态自动起播
 * - [onStart] / [onStop]：建立 / 断开弹幕 WebSocket
 * - [onResume] / [onPause]：恢复 / 暂停播放
 * - [onCleared]：释放播放器与弹幕引擎
 *
 * 生命周期门控：取流是异步的，成功回调可能落在后台（用户按 HOME）。因此这里独立维护
 * Activity 的 started / resumed 标志（不能用一次性的 [started] 代替），
 * 取流成功后的「建 WS」「起播」都只在这两个标志为真时发生。
 */
@KoinViewModel
class LivePlayerViewModel(
    private val liveRepository: LiveRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    private val logger = KotlinLogging.logger("LivePlayerViewModel")

    /** 直播间号（真实房间号，由调用方传入） */
    var roomId: Int = 0
        private set

    /** 房间标题（来自列表卡片，`ROOM_CHANGE` 事件会覆盖） */
    var title by mutableStateOf("")

    /** 主播昵称 */
    var uname by mutableStateOf("")

    /** 分区名（来自列表卡片，`ROOM_CHANGE` 事件会覆盖） */
    var areaName by mutableStateOf("")

    var videoPlayer: ExoMediaPlayer? by mutableStateOf(null)
        private set
    var danmakuPlayer: DanmakuPlayer? by mutableStateOf(null)
        private set

    /** 首次取流中（显示全屏 loading） */
    var loading by mutableStateOf(true)
        private set

    /** 缓冲中（显示小转圈） */
    var showBuffering by mutableStateOf(false)
        private set

    /** 致命错误（未开播 / 风控 / 网络），非 null 时显示错误层 */
    var errorMessage by mutableStateOf<String?>(null)
        private set

    var qualityOptions by mutableStateOf<List<LiveQuality>>(emptyList())
        private set

    /** 当前实际下发的画质（服务端 `current_qn`） */
    var currentQn by mutableStateOf(LIVE_DEFAULT_QN)
        private set

    /** 当前画质文案，如「原画」 */
    var currentQualityDescription by mutableStateOf("")
        private set

    var switchingQuality by mutableStateOf(false)
        private set

    var danmakuEnabled by mutableStateOf(Prefs.defaultDanmakuEnabled)
        private set

    /** 在线人气，来自弹幕心跳回包（`HeartbeatReply`） */
    var popularity by mutableStateOf<Long?>(null)
        private set

    /** 弹幕连接状态提示，连上时为 null */
    var connectionHint by mutableStateOf<String?>(null)
        private set

    /** 开播状态提示（下播 / 准备中） */
    var liveStatusHint by mutableStateOf<String?>(null)
        private set

    private var started = false

    /** Activity 已 onStart（可见）：弹幕 WS 只在这个状态下建立 */
    private var activityStarted = false

    /** Activity 已 onResume（前台可交互）：播放只在这个状态下起播 */
    private var activityResumed = false

    /** 已成功取到播放地址并 prepare（含重试 / 切清晰度成功），决定回前台是否恢复播放 */
    private var streamLoaded = false

    /** 弹幕命中风控（-352）后熔断：本次播放会话不再尝试连接弹幕 */
    private var danmakuRiskControlBlocked = false
    private var liveDataWebSocket: LiveDataWebSocket? = null
    private var socketJob: Job? = null
    private var eventJob: Job? = null
    private var danmakuIdSeed = 0L
    private val danmakuConfig = DanmakuConfig().copy(textSizeScale = Prefs.defaultDanmakuScale)

    /** 播放器事件：直播场景只需驱动缓冲态与弹幕引擎启停 */
    val playerListener = object : VideoPlayerListener {
        override fun onError(error: Exception) {
            logger.fError { "直播播放失败: ${error.stackTraceToString()}" }
            showBuffering = false
            errorMessage = "播放失败：${error.localizedMessage ?: "未知错误"}"
            danmakuPlayer?.pause()
        }

        override fun onReady() {
            showBuffering = false
        }

        override fun onPlay() {
            showBuffering = false
            if (danmakuEnabled) danmakuPlayer?.start(danmakuConfig)
        }

        override fun onPause() {
            danmakuPlayer?.pause()
        }

        override fun onBuffering() {
            showBuffering = true
            danmakuPlayer?.pause()
        }

        override fun onEnd() {
            danmakuPlayer?.pause()
        }

        override fun onIdle() {
            danmakuPlayer?.pause()
        }

        override fun onSeekBack(seekBackIncrementMs: Long) = Unit

        override fun onSeekForward(seekForwardIncrementMs: Long) = Unit
    }

    /** 由 Activity 从 intent 里取出的房间信息 */
    fun setRoom(roomId: Int, title: String, uname: String, areaName: String) {
        this.roomId = roomId
        this.title = title
        this.uname = uname
        this.areaName = areaName
    }

    /** 进房：创建播放器 / 弹幕引擎并加载直播流，重复调用无副作用 */
    fun start() {
        if (started) return
        started = true
        logger.fInfo { "start live player: roomId = $roomId, title = $title, uname = $uname" }
        initPlayer()
        initDanmakuPlayer()
        loadLiveStream(qn = LIVE_DEFAULT_QN)
    }

    /** 用户手动重试（失败层按钮），沿用当前清晰度 */
    fun retry() {
        if (loading) return
        loadLiveStream(qn = currentQn)
    }

    /**
     * 切换清晰度（仅用户触发）
     *
     * 直播没有「换流不中断」的能力，这里直接重新取流并重新 prepare。
     */
    fun switchQuality(qn: Int) {
        if (switchingQuality || qn == currentQn) return
        switchingQuality = true
        viewModelScope.launch {
            logger.fInfo { "switch live quality: roomId = $roomId, qn = $qn" }
            runCatching { liveRepository.getLiveStreamUrl(roomId = roomId, qn = qn) }
                .onSuccess { playStream(it) }
                .onFailure { error ->
                    logger.fError { "切换清晰度失败: ${error.localizedMessage}" }
                    "切换清晰度失败：${error.localizedMessage ?: "未知错误"}".toast(BVApp.context)
                }
            switchingQuality = false
        }
    }

    /** 弹幕开关（只影响后续弹幕与引擎启停，不移除弹幕 View） */
    fun toggleDanmaku() {
        danmakuEnabled = !danmakuEnabled
        if (danmakuEnabled) {
            if (videoPlayer?.isPlaying == true) danmakuPlayer?.start(danmakuConfig)
        } else {
            danmakuPlayer?.pause()
        }
    }

    /** Activity.onStart：连接弹幕 WebSocket */
    fun onStart() {
        activityStarted = true
        if (!started) return
        // 还没取流成功（加载中 / 未开播 / 风控 / 网络失败）时连弹幕毫无意义：
        // 只会多打两个请求并立刻失败；成功加载的路径自己会幂等建连
        if (!streamLoaded || errorMessage != null) return
        ensureDanmakuConnected()
    }

    /** Activity.onResume：恢复播放 */
    fun onResume() {
        activityResumed = true
        if (!started) return
        // 首次进房时取流还没回来（回调里会自己起播），这里只需处理「回前台恢复」；
        // 两者都走 streamLoaded 判断，重复 start 对 ExoPlayer 是幂等的
        if (!streamLoaded || errorMessage != null) return
        videoPlayer?.start()
    }

    /** Activity.onPause：暂停播放 */
    fun onPause() {
        activityResumed = false
        if (!started) return
        videoPlayer?.pause()
        danmakuPlayer?.pause()
    }

    /** Activity.onStop：断开弹幕 WebSocket 并暂停 */
    fun onStop() {
        activityStarted = false
        activityResumed = false
        if (!started) return
        disconnectWebSocket()
        videoPlayer?.pause()
        danmakuPlayer?.pause()
    }

    override fun onCleared() {
        super.onCleared()
        logger.fInfo { "clear live player view model: roomId = $roomId" }
        // 取消连接 Job 即会关闭 socket（LiveDataWebSocket.readEvents 的 finally 负责收尾），
        // 此时 viewModelScope 已取消，不能再靠它去跑 suspend 的 close()
        socketJob?.cancel()
        socketJob = null
        eventJob?.cancel()
        eventJob = null
        liveDataWebSocket = null
        videoPlayer?.release()
        videoPlayer = null
        danmakuPlayer?.release()
        danmakuPlayer = null
    }

    private fun initPlayer() {
        val options = VideoPlayerOptions(
            userAgent = BiliUserAgentConfig().apply { buildUserAgents() }.webUserAgent,
            referer = LIVE_REFERER,
            enableFfmpegAudioRenderer = Prefs.enableFfmpegAudioRenderer
        )
        videoPlayer = ExoPlayerFactory().create(BVApp.context, options).also {
            it.setPlayerEventListener(playerListener)
        }
    }

    private fun initDanmakuPlayer() {
        danmakuPlayer = DanmakuPlayer(SimpleRenderer())
    }

    private fun loadLiveStream(qn: Int) {
        viewModelScope.launch {
            loading = true
            errorMessage = null
            runCatching {
                val roomInfo = liveRepository.getRoomInfo(roomId = roomId, qn = qn)
                // 未开播时没必要再请求一次播放地址
                if (roomInfo.liveStatus == 0) throw IllegalStateException("主播尚未开播")
                qualityOptions = roomInfo.playUrlInfo?.playurl?.qualityDescriptions
                    ?.filter { it.qn > 0 }
                    ?.distinctBy { it.qn }
                    ?.map { LiveQuality(qn = it.qn, description = it.desc) }
                    .orEmpty()
                // 复用上面这份 playurl_info 选流：getLiveStreamUrl(roomId) 内部还会再请求一次
                // 同参数的 v2 getRoomPlayInfo，等于进房打两次，白耗配额还更容易撞风控
                liveRepository.getLiveStreamUrl(roomInfo = roomInfo)
            }.onSuccess { stream ->
                playStream(stream)
                // 任何一次成功加载（含失败后的重试）都幂等确保弹幕连接；
                // 但取流期间用户可能已按 HOME，此时不能在后台建 WS（建了立刻又断，纯浪费请求）
                if (activityStarted) ensureDanmakuConnected()
            }.onFailure { error ->
                onLoadFailed(error)
            }
            loading = false
        }
    }

    private suspend fun playStream(stream: LiveStreamUrl) {
        logger.fInfo {
            "play live stream: qn = ${stream.qn}, quality = ${stream.qualityDescription}, " +
                    "codec = ${stream.codecName}, protocol = ${stream.protocolName}/${stream.formatName}"
        }
        withContext(Dispatchers.Main) {
            currentQn = stream.qn
            currentQualityDescription = stream.qualityDescription
            errorMessage = null
            showBuffering = true
            streamLoaded = true
            videoPlayer?.playUrl(stream.url, null)
            videoPlayer?.prepare()
            // 必须显式起播：ExoMediaPlayer 只在 setOptions() 里设 playWhenReady = true，
            // 而直播链路从不调用 setOptions()，只 prepare() 会一直停在 READY / 首帧不出画面。
            // 切到后台（onPause 已过）时不抢播，回到前台由 onResume 起播。
            if (activityResumed) videoPlayer?.start()
        }
    }

    private fun onLoadFailed(error: Throwable) {
        logger.fError { "直播加载失败: ${error.stackTraceToString()}" }
        showBuffering = false
        val message = when (error) {
            is RiskControlException -> "请求过于频繁，请稍后再试"
            is IllegalStateException -> error.localizedMessage ?: "直播流不可用"
            else -> "直播加载失败：${error.localizedMessage ?: "网络错误"}"
        }
        errorMessage = message
        message.toast(BVApp.context)
    }

    /**
     * 幂等确保弹幕连接
     *
     * 调用方无需关心「是不是首次」：已连上、风控熔断、Activity 不在前台时都直接返回。
     */
    private fun ensureDanmakuConnected() {
        if (socketJob?.isActive == true) return
        // 命中风控后本次播放会话不再尝试：重连只会继续打接口、加深风控
        if (danmakuRiskControlBlocked) {
            // 熔断是终态，提示要一直留着（onStop 也不清），否则回前台就看不到失败原因了
            connectionHint = DANMAKU_RISK_CONTROL_HINT
            return
        }
        val socket = LiveDataWebSocket(
            roomInfoProvider = BiliLiveRoomInfoProvider,
            authRepository = authRepository
        )
        liveDataWebSocket = socket
        // 先起收集再连接，避免漏掉最早的 Connected 事件（SharedFlow replay = 0）
        eventJob = viewModelScope.launch {
            socket.events.collect { event -> handleLiveEvent(event) }
        }
        socketJob = socket.connect(roomId = roomId, scope = viewModelScope)
        logger.fInfo { "connect live danmaku websocket: roomId = $roomId" }
    }

    private fun disconnectWebSocket() {
        val socket = liveDataWebSocket ?: return
        liveDataWebSocket = null
        socketJob?.cancel()
        socketJob = null
        eventJob?.cancel()
        eventJob = null
        // 熔断提示是终态信息：切后台不能清，否则回前台时 ensureDanmakuConnected 直接 return，提示就丢了
        if (!danmakuRiskControlBlocked) connectionHint = null
        viewModelScope.launch { socket.close() }
        logger.fInfo { "disconnect live danmaku websocket: roomId = $roomId" }
    }

    private fun handleLiveEvent(event: LiveEvent) {
        when (event) {
            is LiveEvent.DanmakuEvent -> appendDanmaku(event)
            is LiveEvent.Connection.Connecting -> connectionHint = "弹幕连接中…"
            is LiveEvent.Connection.Connected -> connectionHint = null
            is LiveEvent.Connection.Reconnecting ->
                connectionHint = "弹幕重连中（第 ${event.attempt} 次）"

            is LiveEvent.Connection.Failed -> {
                // 风控熔断：终态，之后不再自动重连（LiveDataWebSocket 内部也已停止重连）
                if (event.riskControl) danmakuRiskControlBlocked = true
                connectionHint = if (event.riskControl) {
                    DANMAKU_RISK_CONTROL_HINT
                } else {
                    "弹幕连接失败"
                }
            }
            is LiveEvent.Connection.Closed -> connectionHint = null
            is LiveEvent.HeartbeatReply -> popularity = event.popularity
            is LiveEvent.RoomChange -> {
                event.title?.takeIf { it.isNotBlank() }?.let { title = it }
                event.areaName?.takeIf { it.isNotBlank() }?.let { areaName = it }
            }

            is LiveEvent.LivePreparing -> liveStatusHint = "主播尚未开播"
            is LiveEvent.LiveStart -> liveStatusHint = null
            else -> Unit
        }
    }

    /**
     * 实时追加一条弹幕
     *
     * 用 akdanmaku 的 [DanmakuPlayer.send] 增量接口（内部只是把数据塞进 DataSystem 的待添加队列，
     * 按 danmakuId 去重，线程安全），不需要像点播那样全量 `updateData`，因此不会闪烁。
     * `position` 取引擎当前时间，保证弹幕立刻到期显示。
     */
    private fun appendDanmaku(event: LiveEvent.DanmakuEvent) {
        if (!danmakuEnabled) return
        if (event.text.isBlank()) return
        val player = danmakuPlayer ?: return
        player.send(
            DanmakuItemData(
                danmakuId = ++danmakuIdSeed,
                position = player.getCurrentTimeMs(),
                content = event.text,
                mode = DanmakuItemData.DANMAKU_MODE_ROLLING,
                textSize = LIVE_DANMAKU_TEXT_SIZE,
                // 弹幕色是 RGB 十进制（白色 0xFFFFFF），这里补上不透明 alpha
                textColor = (event.color and 0xFFFFFF) or 0xFF000000.toInt()
            )
        )
    }

    companion object {
        private const val LIVE_REFERER = "https://live.bilibili.com"
        private const val LIVE_DANMAKU_TEXT_SIZE = 25

        /** 弹幕风控熔断提示（终态：本次播放会话不再连弹幕，切后台也不清） */
        private const val DANMAKU_RISK_CONTROL_HINT = "弹幕连接失败（风控），退出重进后恢复"
    }
}
