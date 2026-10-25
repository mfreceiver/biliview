package cn.vectory.biliview.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.kuaishou.akdanmaku.ui.DanmakuPlayer
import com.kuaishou.akdanmaku.ui.DanmakuView

@Composable
fun AkDanmakuPlayer(
    modifier: Modifier = Modifier,
    danmakuPlayer: DanmakuPlayer?,
    releaseOnDispose: Boolean = true
) {
    val context = LocalContext.current
    var danmakuView: DanmakuView? by remember { mutableStateOf(null) }

    DisposableEffect(Unit) {
        onDispose {
            if (releaseOnDispose) {
                danmakuPlayer?.release()
            } else {
                // 引擎生命周期由外部（如 retained ViewModel 的 onCleared）管理：
                // dispose 只解绑并暂停，避免 Activity 配置重建后引擎被永久释放
                danmakuPlayer?.stop()
            }
        }
    }

    LaunchedEffect(danmakuPlayer) {
        if (danmakuView != null) {
            danmakuPlayer?.bindView(danmakuView!!)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                danmakuView = DanmakuView(context)
                danmakuView!!
            }
        )
    }
}
