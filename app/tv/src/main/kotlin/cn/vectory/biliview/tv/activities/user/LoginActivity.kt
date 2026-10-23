package cn.vectory.biliview.tv.activities.user

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cn.vectory.biliview.tv.screens.login.LoginScreen
import cn.vectory.biliview.ui.theme.BVTheme

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BVTheme {
                LoginScreen()
            }
        }
    }
}
