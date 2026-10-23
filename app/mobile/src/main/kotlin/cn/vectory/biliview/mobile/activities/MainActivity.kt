package cn.vectory.biliview.mobile.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import cn.vectory.biliview.mobile.screen.MobileMainScreen
import cn.vectory.biliview.mobile.theme.BVMobileTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            BVMobileTheme {
                MobileMainScreen()
            }
        }
    }
}
