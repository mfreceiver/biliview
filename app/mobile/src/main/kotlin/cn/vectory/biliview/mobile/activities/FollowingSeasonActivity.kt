package cn.vectory.biliview.mobile.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import cn.vectory.biliview.mobile.screen.FollowingSeasonScreen
import cn.vectory.biliview.mobile.theme.BVMobileTheme

class FollowingSeasonActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val windowSize = calculateWindowSizeClass(this)
            BVMobileTheme {
                FollowingSeasonScreen(
                    windowSize = windowSize
                )
            }
        }
    }
}