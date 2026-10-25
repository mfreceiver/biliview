package cn.vectory.biliview.tv.screens.live

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import cn.vectory.biliview.player.AkDanmakuPlayer
import cn.vectory.biliview.player.impl.exo.ExoMediaPlayer
import cn.vectory.biliview.tv.component.LoadingTip
import cn.vectory.biliview.viewmodel.live.LivePlayerViewModel
import cn.vectory.biliview.viewmodel.live.LiveQuality
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/** 无操作后控制器自动隐藏的延迟 */
private const val CONTROLS_HIDE_DELAY_MS = 5_000L

/**
 * 直播播放页（TV）
 *
 * 极简控制器（不复用 [cn.vectory.biliview.player.tv.BvPlayer]，直播没有进度条 / 倍速 / 选集）：
 * - 顶部：标题 / 主播 / 分区 / 人气 / 画质 / 弹幕连接提示，几秒后随控制条一起隐藏
 * - 底部：弹幕开关、清晰度菜单、退出
 * - 遥控：控制器隐藏时任意方向键 / OK / MENU 唤出，BACK 退出（清晰度菜单打开时 BACK 只关菜单）
 *
 * 弹幕层始终留在组合里，开关只切 alpha：弹幕引擎的释放由 [LivePlayerViewModel] 的 onCleared 负责
 * （`AkDanmakuPlayer` 传 `releaseOnDispose = false`，dispose 只解绑/暂停），
 * 条件移出组合或 Activity 配置重建都不会释放引擎。
 */
@Composable
fun LivePlayerScreen(
    modifier: Modifier = Modifier,
    viewModel: LivePlayerViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var controlsVisible by remember { mutableStateOf(true) }
    var qualityMenuVisible by remember { mutableStateOf(false) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val rootFocusRequester = remember { FocusRequester() }
    val qualityButtonFocusRequester = remember { FocusRequester() }
    val retryButtonFocusRequester = remember { FocusRequester() }

    fun keepControlsAlive() {
        controlsVisible = true
        lastInteraction = System.currentTimeMillis()
    }

    fun hideQualityMenu() {
        qualityMenuVisible = false
        keepControlsAlive()
        scope.launch {
            delay(100)
            runCatching { qualityButtonFocusRequester.requestFocus() }
        }
    }

    BackHandler {
        if (qualityMenuVisible) hideQualityMenu() else activity?.finish()
    }

    // 初始焦点给根容器，方向键自然流向控制条按钮
    LaunchedEffect(Unit) {
        runCatching { rootFocusRequester.requestFocus() }
    }

    // 控制器几秒无操作后隐藏（清晰度菜单打开时不隐藏）
    LaunchedEffect(controlsVisible, lastInteraction, qualityMenuVisible) {
        if (controlsVisible && !qualityMenuVisible) {
            delay(CONTROLS_HIDE_DELAY_MS)
            controlsVisible = false
            runCatching { rootFocusRequester.requestFocus() }
        }
    }

    // 出错时把焦点交给「重试」，遥控可直接确认
    LaunchedEffect(viewModel.errorMessage) {
        if (viewModel.errorMessage != null) {
            delay(100)
            runCatching { retryButtonFocusRequester.requestFocus() }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.Menu, Key.DirectionCenter -> {
                        keepControlsAlive()
                        true
                    }

                    Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight -> {
                        if (controlsVisible) false else {
                            keepControlsAlive()
                            true
                        }
                    }

                    else -> false
                }
            }
    ) {
        viewModel.videoPlayer?.let { player ->
            LiveVideoView(
                modifier = Modifier.fillMaxSize(),
                player = player
            )
        }

        viewModel.danmakuPlayer?.let { player ->
            AkDanmakuPlayer(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (viewModel.danmakuEnabled) 1f else 0f),
                danmakuPlayer = player,
                releaseOnDispose = false
            )
        }

        if (controlsVisible) {
            LiveTopInfoBar(
                modifier = Modifier.align(Alignment.TopStart),
                title = viewModel.title,
                uname = viewModel.uname,
                areaName = viewModel.areaName,
                popularity = viewModel.popularity,
                qualityDescription = viewModel.currentQualityDescription,
                connectionHint = viewModel.connectionHint,
                liveStatusHint = viewModel.liveStatusHint
            )
        }

        if (controlsVisible && !qualityMenuVisible) {
            LiveControlBar(
                modifier = Modifier.align(Alignment.BottomStart),
                danmakuEnabled = viewModel.danmakuEnabled,
                qualityDescription = viewModel.currentQualityDescription,
                canSwitchQuality = viewModel.qualityOptions.size > 1,
                switchingQuality = viewModel.switchingQuality,
                qualityButtonFocusRequester = qualityButtonFocusRequester,
                onInteraction = { keepControlsAlive() },
                onToggleDanmaku = {
                    keepControlsAlive()
                    viewModel.toggleDanmaku()
                },
                onQualityClick = {
                    keepControlsAlive()
                    qualityMenuVisible = true
                },
                onExit = { activity?.finish() }
            )
        }

        if (qualityMenuVisible) {
            LiveQualityMenu(
                modifier = Modifier.align(Alignment.CenterEnd),
                options = viewModel.qualityOptions,
                currentQn = viewModel.currentQn,
                onSelect = { quality ->
                    qualityMenuVisible = false
                    viewModel.switchQuality(quality.qn)
                    scope.launch {
                        delay(100)
                        runCatching { qualityButtonFocusRequester.requestFocus() }
                    }
                },
                onDismiss = { hideQualityMenu() }
            )
        }

        viewModel.errorMessage?.let { message ->
            LiveErrorOverlay(
                modifier = Modifier.align(Alignment.Center),
                message = message,
                retryButtonFocusRequester = retryButtonFocusRequester,
                onRetry = { viewModel.retry() },
                onExit = { activity?.finish() }
            )
        }

        if (viewModel.loading && viewModel.errorMessage == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                LoadingTip()
            }
        } else if (viewModel.showBuffering && viewModel.errorMessage == null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                LoadingTip()
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun LiveVideoView(
    modifier: Modifier = Modifier,
    player: ExoMediaPlayer
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                this.player = player.mPlayer
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                useController = false
            }
        },
        update = { view -> view.player = player.mPlayer }
    )
}

@Composable
private fun LiveTopInfoBar(
    modifier: Modifier = Modifier,
    title: String,
    uname: String,
    areaName: String,
    popularity: Long?,
    qualityDescription: String,
    connectionHint: String?,
    liveStatusHint: String?
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                )
            )
            .padding(horizontal = 32.dp, vertical = 20.dp)
    ) {
        Text(
            text = title.ifBlank { "直播间" },
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleLarge
        )
        Row(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (uname.isNotBlank()) {
                Text(
                    text = uname,
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (areaName.isNotBlank()) {
                Text(
                    text = areaName,
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            popularity?.let {
                Text(
                    text = "人气 ${it.toPopularityString()}",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (qualityDescription.isNotBlank()) {
                Text(
                    text = "画质 $qualityDescription",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        liveStatusHint?.let {
            Text(
                text = it,
                color = Color(0xFFFFB74D),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        connectionHint?.let {
            Text(
                text = it,
                color = Color(0xFFFFD54F),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun LiveControlBar(
    modifier: Modifier = Modifier,
    danmakuEnabled: Boolean,
    qualityDescription: String,
    canSwitchQuality: Boolean,
    switchingQuality: Boolean,
    qualityButtonFocusRequester: FocusRequester,
    onInteraction: () -> Unit,
    onToggleDanmaku: () -> Unit,
    onQualityClick: () -> Unit,
    onExit: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                )
            )
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Button(
            onClick = onToggleDanmaku,
            modifier = Modifier.onFocusChanged { if (it.isFocused) onInteraction() }
        ) {
            Text(text = if (danmakuEnabled) "弹幕 开" else "弹幕 关")
        }
        Button(
            onClick = onQualityClick,
            enabled = canSwitchQuality && !switchingQuality,
            modifier = Modifier
                .focusRequester(qualityButtonFocusRequester)
                .onFocusChanged { if (it.isFocused) onInteraction() }
        ) {
            Text(
                text = when {
                    switchingQuality -> "切换中…"
                    qualityDescription.isBlank() -> "清晰度"
                    else -> "清晰度 $qualityDescription"
                }
            )
        }
        Button(
            onClick = onExit,
            modifier = Modifier.onFocusChanged { if (it.isFocused) onInteraction() }
        ) {
            Text(text = "退出")
        }
    }
}

@Composable
private fun LiveQualityMenu(
    modifier: Modifier = Modifier,
    options: List<LiveQuality>,
    currentQn: Int,
    onSelect: (LiveQuality) -> Unit,
    onDismiss: () -> Unit
) {
    val focusRequesters = remember(options.size) { options.map { FocusRequester() } }

    LaunchedEffect(options) {
        delay(100)
        runCatching { focusRequesters.firstOrNull()?.requestFocus() }
    }

    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "清晰度",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium
        )
        options.forEachIndexed { index, quality ->
            Button(
                onClick = { onSelect(quality) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequesters[index])
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = quality.description)
                    if (quality.qn == currentQn) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "当前")
                    }
                }
            }
        }
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "返回")
        }
    }
}

@Composable
private fun LiveErrorOverlay(
    modifier: Modifier = Modifier,
    message: String,
    retryButtonFocusRequester: FocusRequester,
    onRetry: () -> Unit,
    onExit: () -> Unit
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
            .padding(horizontal = 40.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = message,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = onRetry,
                modifier = Modifier.focusRequester(retryButtonFocusRequester)
            ) {
                Text(text = "重试")
            }
            Button(onClick = onExit) {
                Text(text = "退出")
            }
        }
    }
}

/** 人气值：≥1 万显示「x.x万」，≥1 亿显示「x.x亿」 */
private fun Long.toPopularityString(): String = when {
    this >= 100_000_000L -> "%.1f亿".format(this / 100_000_000f)
    this >= 10_000L -> "%.1f万".format(this / 10_000f)
    else -> toString()
}
