package cn.vectory.biliview.tv.activities.video

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cn.vectory.biliview.entity.proxy.ProxyArea
import cn.vectory.biliview.tv.screens.VideoInfoScreen
import cn.vectory.biliview.ui.theme.BVTheme

class VideoInfoActivity : ComponentActivity() {
    companion object {
        fun actionStart(
            context: Context, aid: Long,
            fromSeason: Boolean = false,
            proxyArea: ProxyArea = ProxyArea.MainLand
        ) {
            context.startActivity(
                Intent(context, VideoInfoActivity::class.java).apply {
                    putExtra("aid", aid)
                    putExtra("fromSeason", fromSeason)
                    putExtra("proxy_area", proxyArea.ordinal)
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BVTheme(
                forceDark = true
            ) {
                VideoInfoScreen()
            }
        }
    }
}
