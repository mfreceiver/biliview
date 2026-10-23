package cn.vectory.biliview.tv.activities.pgc.anime

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cn.vectory.biliview.tv.screens.main.pgc.anime.AnimeTimelineScreen
import cn.vectory.biliview.ui.theme.BVTheme

class AnimeTimelineActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BVTheme {
                AnimeTimelineScreen()
            }
        }
    }
}