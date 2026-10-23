package cn.vectory.biliview.mobile.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cn.vectory.biliview.mobile.screen.UserSpaceScreen
import cn.vectory.biliview.mobile.theme.BVMobileTheme

class UserSpaceActivity : ComponentActivity() {
    companion object {
        fun actionStart(context: Context, mid: Long, name: String) {
            context.startActivity(
                Intent(context, UserSpaceActivity::class.java).apply {
                    putExtra("mid", mid)
                    putExtra("name", name)
                }
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BVMobileTheme {
                UserSpaceScreen()
            }
        }
    }
}