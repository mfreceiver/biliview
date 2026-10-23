package cn.vectory.biliview.mobile.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cn.vectory.biliview.mobile.screen.LoginScreen
import cn.vectory.biliview.mobile.theme.BVMobileTheme

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BVMobileTheme {
                LoginScreen()
            }
        }
    }
}