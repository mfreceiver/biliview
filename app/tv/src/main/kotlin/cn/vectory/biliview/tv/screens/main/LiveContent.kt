package cn.vectory.biliview.tv.screens.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import cn.vectory.biliview.tv.component.LiveTopNavItem
import cn.vectory.biliview.tv.component.TopNav
import cn.vectory.biliview.tv.screens.main.live.LiveScreen
import cn.vectory.biliview.util.fInfo
import cn.vectory.biliview.util.requestFocus
import cn.vectory.biliview.viewmodel.live.LiveViewModel
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * 直播页骨架，结构与 [HomeContent] 对齐：TopNav + 每个 tab 一份 LazyListState + 返回键回到侧栏
 *
 * 风控纪律：进入页面 / 切换 tab / 翻页 / 重新点击 tab 才会请求，不做定时轮询与预加载。
 */
@Composable
fun LiveContent(
    modifier: Modifier = Modifier,
    navFocusRequester: FocusRequester,
    liveViewModel: LiveViewModel = koinViewModel()
) {
    val scope = rememberCoroutineScope()
    val logger = KotlinLogging.logger("LiveContent")

    val recommendState = rememberLazyListState()
    val virtualState = rememberLazyListState()
    val onlineGameState = rememberLazyListState()
    val mobileGameState = rememberLazyListState()
    val entertainmentState = rememberLazyListState()
    val radioState = rememberLazyListState()

    fun listStateOf(tab: LiveTopNavItem): LazyListState = when (tab) {
        LiveTopNavItem.Recommend -> recommendState
        LiveTopNavItem.Virtual -> virtualState
        LiveTopNavItem.OnlineGame -> onlineGameState
        LiveTopNavItem.MobileGame -> mobileGameState
        LiveTopNavItem.Entertainment -> entertainmentState
        LiveTopNavItem.Radio -> radioState
    }

    var selectedTab by remember { mutableStateOf(LiveTopNavItem.Recommend) }
    var focusOnContent by remember { mutableStateOf(false) }
    var hasFocus by remember { mutableStateOf(false) }
    val currentListOnTop by remember {
        derivedStateOf {
            with(listStateOf(selectedTab)) {
                firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0
            }
        }
    }

    LaunchedEffect(hasFocus) {
        if (hasFocus) {
            navFocusRequester.requestFocus()
        }
    }

    BackHandler(focusOnContent) {
        logger.fInfo { "onFocusBackToNav" }
        navFocusRequester.requestFocus(scope)
        // scroll to top
        scope.launch(Dispatchers.Main) {
            listStateOf(selectedTab).animateScrollToItem(0)
        }
    }

    Scaffold(
        modifier = Modifier
            .onFocusChanged { hasFocus = it.hasFocus },
        topBar = {
            TopNav(
                modifier = Modifier
                    .focusRequester(navFocusRequester)
                    .padding(end = 80.dp),
                items = LiveTopNavItem.entries,
                isLargePadding = !focusOnContent && currentListOnTop,
                onSelectedChanged = { nav ->
                    // 首次进入该分区时由 LiveScreen 自己触发首帧加载，这里只切状态
                    selectedTab = nav as LiveTopNavItem
                },
                onClick = { nav ->
                    // 重新点击当前 tab = 首刷
                    liveViewModel.refresh((nav as LiveTopNavItem).areaId)
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .onFocusChanged { focusOnContent = it.hasFocus }
        ) {
            AnimatedContent(
                targetState = selectedTab,
                label = "live animated content",
                transitionSpec = {
                    val coefficient = 10
                    if (targetState.ordinal < initialState.ordinal) {
                        fadeIn() + slideInHorizontally { -it / coefficient } togetherWith
                                fadeOut() + slideOutHorizontally { it / coefficient }
                    } else {
                        fadeIn() + slideInHorizontally { it / coefficient } togetherWith
                                fadeOut() + slideOutHorizontally { -it / coefficient }
                    }
                }
            ) { screen ->
                LiveScreen(
                    lazyListState = listStateOf(screen),
                    areaId = screen.areaId
                )
            }
        }
    }
}
