package cn.vectory.biliview.tv.activities.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cn.vectory.biliview.tv.screens.settings.SpeedTestScreen
import cn.vectory.biliview.ui.theme.BVTheme

class SpeedTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BVTheme {
                SpeedTestScreen()
            }
        }
    }
}