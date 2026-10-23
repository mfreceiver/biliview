package cn.vectory.biliview.mobile.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cn.vectory.biliview.mobile.screen.settings.SettingsScreen
import cn.vectory.biliview.mobile.theme.BVMobileTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BVMobileTheme {
                SettingsScreen()
            }
        }
    }
}
