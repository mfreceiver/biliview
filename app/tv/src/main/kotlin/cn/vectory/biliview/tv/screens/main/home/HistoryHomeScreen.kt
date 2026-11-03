package cn.vectory.biliview.tv.screens.main.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import cn.vectory.biliview.entity.proxy.ProxyArea
import cn.vectory.biliview.tv.activities.video.VideoInfoActivity
import cn.vectory.biliview.tv.component.LoadingTip
import cn.vectory.biliview.tv.component.videocard.SmallVideoCard
import cn.vectory.biliview.tv.screens.main.ugc.gridItems
import cn.vectory.biliview.viewmodel.user.HistoryViewModel

@Composable
fun HistoryHomeScreen(
    modifier: Modifier = Modifier,
    lazyListState: LazyListState,
    historyViewModel: HistoryViewModel,
    isActive: Boolean,
    isLogin: Boolean
) {
    val context = LocalContext.current
    var currentFocusedIndex by remember { mutableIntStateOf(-100) }
    val shouldLoadMore by remember {
        derivedStateOf { currentFocusedIndex + 24 > historyViewModel.histories.size }
    }

    LaunchedEffect(shouldLoadMore, isActive, isLogin) {
        if (shouldLoadMore && isActive && isLogin) {
            historyViewModel.update()
            currentFocusedIndex = -100
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = lazyListState
    ) {
        if (!isLogin || historyViewModel.histories.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillParentMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        !isLogin -> Text(text = "请先登录")
                        historyViewModel.updating -> LoadingTip()
                        historyViewModel.loadError -> Text(text = "加载失败，请点击历史标签重试")
                        !historyViewModel.hasLoaded -> LoadingTip()
                        else -> Text(text = "暂无历史记录")
                    }
                }
            }
        } else {
            gridItems(
                data = historyViewModel.histories,
                columnCount = 4,
                modifier = Modifier
                    .width(880.dp)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                itemContent = { index, history ->
                    SmallVideoCard(
                        data = history,
                        onClick = {
                            VideoInfoActivity.actionStart(
                                context = context,
                                aid = history.avid,
                                proxyArea = ProxyArea.checkProxyArea(history.title)
                            )
                        },
                        onFocus = { currentFocusedIndex = index }
                    )
                }
            )

            if (historyViewModel.updating) {
                item {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingTip()
                    }
                }
            }
            if (historyViewModel.noMore) {
                item {
                    Text(text = "没有更多了捏", color = Color.White)
                }
            }
        }
    }
}