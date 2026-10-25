package cn.vectory.biliview.tv.activities.live

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cn.vectory.biliview.tv.screens.live.LivePlayerScreen
import cn.vectory.biliview.ui.theme.BVTheme
import cn.vectory.biliview.util.fInfo
import cn.vectory.biliview.viewmodel.live.LivePlayerViewModel
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * 直播播放页
 *
 * 与 [cn.vectory.biliview.tv.activities.video.VideoPlayerV3Activity] 一样是横屏 + 常亮的独立 Activity，
 * 房间标题 / 主播等展示信息由列表卡片经 intent 传入（`getRoomPlayInfo` 不返回这些字段），
 * 播放地址、弹幕连接、在线人气全部由 [LivePlayerViewModel] 负责。
 */
class LivePlayerActivity : ComponentActivity() {
    companion object {
        private val logger = KotlinLogging.logger { }

        /**
         * 打开直播间
         *
         * @param roomId 真实房间号（列表卡片里的 `LiveCardData.roomId`）
         */
        fun actionStart(
            context: Context,
            roomId: Long,
            title: String = "",
            uname: String = "",
            areaName: String = "",
            online: Int = 0
        ) {
            context.startActivity(
                Intent(context, LivePlayerActivity::class.java).apply {
                    putExtra("roomId", roomId.toInt())
                    putExtra("title", title)
                    putExtra("uname", uname)
                    putExtra("areaName", areaName)
                    putExtra("online", online)
                }
            )
        }
    }

    private val playerViewModel: LivePlayerViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        getParamsFromIntent()
        playerViewModel.start()
        setContent {
            BVTheme(
                forceDark = true
            ) {
                LivePlayerScreen()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        playerViewModel.onStart()
    }

    override fun onResume() {
        super.onResume()
        playerViewModel.onResume()
    }

    override fun onPause() {
        super.onPause()
        playerViewModel.onPause()
    }

    override fun onStop() {
        super.onStop()
        playerViewModel.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun getParamsFromIntent() {
        val roomId = intent.getIntExtra("roomId", 0)
        val title = intent.getStringExtra("title").orEmpty()
        val uname = intent.getStringExtra("uname").orEmpty()
        val areaName = intent.getStringExtra("areaName").orEmpty()
        val online = intent.getIntExtra("online", 0)
        logger.fInfo { "Launch parameter: [roomId=$roomId, online=$online, title=$title]" }
        playerViewModel.setRoom(
            roomId = roomId,
            title = title,
            uname = uname,
            areaName = areaName
        )
    }
}
