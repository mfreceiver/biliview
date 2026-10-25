package cn.vectory.biliview.tv.screens.main.live

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import cn.vectory.biliview.tv.component.LoadingTip
import cn.vectory.biliview.tv.component.videocard.LiveCard
import cn.vectory.biliview.tv.screens.main.ugc.gridItems
import cn.vectory.biliview.util.fInfo
import cn.vectory.biliview.util.toast
import cn.vectory.biliview.viewmodel.live.LiveViewModel
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private val logger = KotlinLogging.logger("LiveScreen")

/**
 * 单个直播 tab 的列表页（结构与 RecommendScreen 对齐）
 */
@Composable
fun LiveScreen(
    modifier: Modifier = Modifier,
    lazyListState: LazyListState,
    areaId: Int,
    liveViewModel: LiveViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val liveState = liveViewModel.state(areaId)
    var currentFocusedIndex by remember(areaId) { mutableIntStateOf(0) }
    val shouldLoadMore by remember(areaId) {
        derivedStateOf { currentFocusedIndex + 24 > liveState.items.size }
    }

    //不能直接使用 LaunchedEffect(currentFocusedIndex)，会导致整个页面重组
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            scope.launch(Dispatchers.IO) {
                liveViewModel.loadMore(areaId)
                //加载完成后重置shouldLoadMore为false，避免如果加载失败后无法重新加载
                currentFocusedIndex = -100
            }
        }
    }

    LazyColumn(
        modifier = modifier,
        state = lazyListState
    ) {
        gridItems(
            data = liveState.items,
            columnCount = 4,
            modifier = Modifier
                .width(880.dp)
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            itemContent = { index, item ->
                LiveCard(
                    data = item,
                    onClick = { actionStartLive(context, item.roomId) },
                    onFocus = { currentFocusedIndex = index }
                )
            }
        )

        if (!liveState.loading && liveState.items.isEmpty() && liveState.initialized) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无直播",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        if (liveState.loading)
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingTip()
                }
            }
    }
}

/**
 * 跳转直播间
 *
 * TODO(Lane D): 直播播放页落地后替换为
 * `context.startActivity(Intent(context, LivePlayerActivity::class.java).putExtra("roomId", roomId))`，
 * 与 [cn.vectory.biliview.tv.activities.video.VideoInfoActivity.actionStart] 保持同样的 actionStart 惯例。
 * 在此之前只做日志 + 轻提示，保证遥控点击不会崩。
 */
fun actionStartLive(context: Context, roomId: Long) {
    logger.fInfo { "actionStartLive: roomId = $roomId, live player page is not implemented yet" }
    "直播间 $roomId 播放页开发中".toast(context)
}
