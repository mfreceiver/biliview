package cn.vectory.biliview.tv.activities.video

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cn.vectory.biliview.entity.proxy.ProxyArea
import cn.vectory.biliview.tv.screens.SeasonInfoScreen
import cn.vectory.biliview.ui.theme.BVTheme

class SeasonInfoActivity : ComponentActivity() {
    companion object {
        fun actionStart(
            context: Context,
            epId: Int? = null,
            seasonId: Int? = null,
            proxyArea: ProxyArea = ProxyArea.MainLand
        ) {
            context.startActivity(
                Intent(context, SeasonInfoActivity::class.java).apply {
                    epId?.let { putExtra("epid", epId) }
                    seasonId?.let { putExtra("seasonid", seasonId) }
                    putExtra("proxy_area", proxyArea.ordinal)
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BVTheme {
                SeasonInfoScreen()
            }
        }
    }
}
