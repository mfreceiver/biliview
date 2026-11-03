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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.vectory.biliview.tv.component.HomeTopNavItem
import cn.vectory.biliview.tv.component.TopNav
import cn.vectory.biliview.tv.screens.main.home.DynamicsScreen
import cn.vectory.biliview.tv.screens.main.home.HistoryHomeScreen
import cn.vectory.biliview.tv.screens.main.home.PopularScreen
import cn.vectory.biliview.tv.screens.main.home.RecommendScreen
import cn.vectory.biliview.util.fInfo
import cn.vectory.biliview.util.isKeyDown
import cn.vectory.biliview.util.requestFocus
import cn.vectory.biliview.viewmodel.UserViewModel
import cn.vectory.biliview.viewmodel.home.DynamicViewModel
import cn.vectory.biliview.viewmodel.home.PopularViewModel
import cn.vectory.biliview.viewmodel.home.RecommendViewModel
import cn.vectory.biliview.viewmodel.user.HistoryViewModel
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeContent(
    modifier: Modifier = Modifier,
    navFocusRequester: FocusRequester,
    recommendViewModel: RecommendViewModel = koinViewModel(),
    popularViewModel: PopularViewModel = koinViewModel(),
    dynamicViewModel: DynamicViewModel = koinViewModel(),
    userViewModel: UserViewModel = koinViewModel(),
    historyViewModel: HistoryViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logger = KotlinLogging.logger("HomeContent")

    val recommendState = rememberLazyListState()
    val popularState = rememberLazyListState()
    val dynamicState = rememberLazyListState()
    val historyState = rememberLazyListState()
    val historyAccount = historyViewModel.isLogin to historyViewModel.userId

    var selectedTab by remember { mutableStateOf(HomeTopNavItem.Recommend) }
    var focusOnContent by remember { mutableStateOf(false) }
    var hasFocus by remember { mutableStateOf(false) }
    val currentListOnTop by remember {
        derivedStateOf {
            with(
                when (selectedTab) {
                    HomeTopNavItem.Recommend -> recommendState
                    HomeTopNavItem.Popular -> popularState
                    HomeTopNavItem.Dynamics -> dynamicState
                    HomeTopNavItem.History -> historyState
                }
            ) {
                firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0
            }
        }
    }

    var recommendRefreshing by remember { mutableStateOf(false) }
    val refreshRecommend: (Boolean) -> Unit = { fromContent ->
        if (!recommendRefreshing && !recommendViewModel.loading) {
            recommendRefreshing = true
            scope.launch {
                try {
                    if (fromContent) {
                        if (!runCatching { navFocusRequester.requestFocus() }.getOrDefault(false)) {
                            delay(100)
                            if (!runCatching { navFocusRequester.requestFocus() }.getOrDefault(false)) return@launch
                        }
                        recommendState.scrollToItem(0)
                    }
                    logger.fInfo { "clear recommend data" }
                    recommendViewModel.clearData()
                    logger.fInfo { "reload recommend data" }
                    withContext(Dispatchers.IO) { recommendViewModel.loadMore() }
                } finally {
                    recommendRefreshing = false
                }
            }
        }
    }
    val onMenuKey: (() -> Unit)? = when (selectedTab) {
        HomeTopNavItem.Recommend -> { { refreshRecommend(true) } }
        HomeTopNavItem.Popular, HomeTopNavItem.Dynamics, HomeTopNavItem.History -> null
    }

    //启动时刷新数据
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            recommendViewModel.loadMore()
        }
        scope.launch(Dispatchers.IO) {
            popularViewModel.loadMore()
        }
        scope.launch(Dispatchers.IO) {
            dynamicViewModel.loadMoreVideo()
        }
        scope.launch(Dispatchers.IO) {
            userViewModel.updateUserInfo()
        }
    }

    //监听登录变化
    LaunchedEffect(userViewModel.isLogin) {
        if (userViewModel.isLogin) {
            //login
            userViewModel.updateUserInfo()
        } else {
            //logout
            userViewModel.clearUserInfo()
        }
    }

    LaunchedEffect(selectedTab, historyAccount) {
        historyViewModel.syncAccount()
        if (selectedTab == HomeTopNavItem.History && historyAccount.first) {
            if (!historyViewModel.hasLoaded && !historyViewModel.updating) {
                historyViewModel.update()
            }
        } else {
            historyViewModel.cancelUpdate()
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
            when (selectedTab) {
                HomeTopNavItem.Recommend -> recommendState.animateScrollToItem(0)
                HomeTopNavItem.Popular -> popularState.animateScrollToItem(0)
                HomeTopNavItem.Dynamics -> dynamicState.animateScrollToItem(0)
                HomeTopNavItem.History -> historyState.animateScrollToItem(0)
            }
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
                items = HomeTopNavItem.entries,
                isLargePadding = !focusOnContent && currentListOnTop,
                onSelectedChanged = { nav ->
                    selectedTab = nav as HomeTopNavItem
                    when (nav) {
                        HomeTopNavItem.Recommend -> {}
                        HomeTopNavItem.Popular -> {}
                        HomeTopNavItem.History -> {}
                        HomeTopNavItem.Dynamics -> {
                            if (!dynamicViewModel.loadingVideo && dynamicViewModel.isLogin && dynamicViewModel.dynamicVideoList.isEmpty()) {
                                scope.launch(Dispatchers.IO) { dynamicViewModel.loadMoreVideo() }
                            }
                        }
                    }
                },
                onClick = { nav ->
                    when (nav) {
                        HomeTopNavItem.Recommend -> refreshRecommend(false)

                        HomeTopNavItem.Popular -> {
                            logger.fInfo { "clear popular data" }
                            popularViewModel.clearData()
                            logger.fInfo { "reload popular data" }
                            scope.launch(Dispatchers.IO) { popularViewModel.loadMore() }
                        }

                        HomeTopNavItem.Dynamics -> {
                            logger.fInfo { "clear dynamic data" }
                            dynamicViewModel.clearVideoData()
                            logger.fInfo { "reload dynamic data" }
                            scope.launch(Dispatchers.IO) { dynamicViewModel.loadMoreVideo() }
                        }

                        HomeTopNavItem.History -> {
                            historyViewModel.clearData()
                            scope.launch { historyState.scrollToItem(0) }
                            if (historyViewModel.isLogin) historyViewModel.update()
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .onFocusChanged { focusOnContent = it.hasFocus }
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Menu && onMenuKey != null) {
                        if (event.isKeyDown() && event.nativeKeyEvent.repeatCount == 0) {
                            onMenuKey()
                        }
                        true
                    } else {
                        false
                    }
                }
        ) {
            AnimatedContent(
                targetState = selectedTab,
                label = "home animated content",
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
                when (screen) {
                    HomeTopNavItem.Recommend -> RecommendScreen(lazyListState = recommendState)
                    HomeTopNavItem.Popular -> PopularScreen(lazyListState = popularState)
                    HomeTopNavItem.Dynamics -> DynamicsScreen(lazyListState = dynamicState)
                    HomeTopNavItem.History -> HistoryHomeScreen(
                        lazyListState = historyState,
                        historyViewModel = historyViewModel,
                        isActive = selectedTab == HomeTopNavItem.History,
                        isLogin = historyAccount.first && historyViewModel.isCurrentAccount
                    )
                }
            }
        }
    }
}
