package cn.vectory.biliview.mobile.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cn.vectory.biliview.mobile.screen.FollowingUserScreen
import cn.vectory.biliview.mobile.theme.BVMobileTheme

class FollowingUserActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BVMobileTheme {
                FollowingUserScreen()
            }
        }
    }
}