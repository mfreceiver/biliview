package cn.vectory.biliview.tv.screens

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.rememberDrawerState
import cn.vectory.biliview.R
import cn.vectory.biliview.tv.component.UserPanel
import cn.vectory.biliview.tv.activities.settings.SettingsActivity
import cn.vectory.biliview.tv.activities.user.FavoriteActivity
import cn.vectory.biliview.tv.activities.user.FollowingSeasonActivity
import cn.vectory.biliview.tv.activities.user.HistoryActivity
import cn.vectory.biliview.tv.activities.user.LoginActivity
import cn.vectory.biliview.tv.activities.user.ToViewActivity
import cn.vectory.biliview.tv.activities.user.UserInfoActivity
import cn.vectory.biliview.tv.screens.main.DrawerContent
import cn.vectory.biliview.tv.screens.main.DrawerItem
import cn.vectory.biliview.tv.screens.main.HomeContent
import cn.vectory.biliview.tv.screens.main.PgcContent
import cn.vectory.biliview.tv.screens.main.UgcContent
import cn.vectory.biliview.tv.screens.search.SearchInputScreen
import cn.vectory.biliview.util.fException
import cn.vectory.biliview.util.fInfo
import cn.vectory.biliview.util.toast
import cn.vectory.biliview.viewmodel.UserViewModel
import cn.vectory.biliview.viewmodel.home.DynamicViewModel
import cn.vectory.biliview.viewmodel.home.PopularViewModel
import cn.vectory.biliview.viewmodel.home.RecommendViewModel
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.androidx.compose.koinViewModel

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    recommendViewModel: RecommendViewModel = koinViewModel(),
    popularViewModel: PopularViewModel = koinViewModel(),
    dynamicViewModel: DynamicViewModel = koinViewModel(),
    userViewModel: UserViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val logger = KotlinLogging.logger("MainScreen")
    var showUserPanel by remember { mutableStateOf(false) }
    var lastPressBack: Long by remember { mutableLongStateOf(0L) }
    var selectedDrawerItem by remember { mutableStateOf(DrawerItem.Home) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val mainFocusRequester = remember { FocusRequester() }
    val ugcFocusRequester = remember { FocusRequester() }
    val pgcFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }

    val handleBack = {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastPressBack < 1000 * 3) {
            logger.fInfo { "Exiting bug video" }
            (context as Activity).finish()
        } else {
            lastPressBack = currentTime
            R.string.home_press_back_again_to_exit.toast(context)
        }
    }

    val onFocusToContent: () -> Unit = {
        when (selectedDrawerItem) {
            DrawerItem.Home -> mainFocusRequester.requestFocus()
            DrawerItem.UGC -> ugcFocusRequester.requestFocus()
            DrawerItem.PGC -> pgcFocusRequester.requestFocus()
            DrawerItem.Search -> searchFocusRequester.requestFocus()
            else -> {}
        }
    }

    LaunchedEffect(Unit) {
        runCatching {
            mainFocusRequester.requestFocus()
        }.onFailure {
            logger.fException(it) { "request default focus requester failed" }
        }
    }

    BackHandler {
        handleBack()
    }

    NavigationDrawer(
        modifier = modifier,
        drawerContent = {
            DrawerContent(
                isLogin = userViewModel.isLogin,
                avatar = userViewModel.face,
                username = userViewModel.username,
                onDrawerItemChanged = { selectedDrawerItem = it },
                onOpenSettings = {
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                },
                onShowUserPanel = {
                    showUserPanel = true
                },
                onFocusToContent = onFocusToContent,
                onLogin = {
                    context.startActivity(Intent(context, LoginActivity::class.java))
                }
            )
        },
        drawerState = drawerState
    ) {
        Box(
            modifier = Modifier
        ) {
            AnimatedContent(
                targetState = selectedDrawerItem,
                label = "main animated content",
                transitionSpec = {
                    val coefficient = 20
                    if (targetState.ordinal < initialState.ordinal) {
                        fadeIn() + slideInVertically { -it / coefficient } togetherWith
                                fadeOut() + slideOutVertically { it / coefficient }
                    } else {
                        fadeIn() + slideInVertically { it / coefficient } togetherWith
                                fadeOut() + slideOutVertically { -it / coefficient }
                    }
                }
            ) { screen ->
                when (screen) {
                    DrawerItem.Home -> HomeContent(navFocusRequester = mainFocusRequester)
                    DrawerItem.UGC -> UgcContent(navFocusRequester = ugcFocusRequester)
                    DrawerItem.PGC -> PgcContent(navFocusRequester = pgcFocusRequester)
                    DrawerItem.Search -> SearchInputScreen(defaultFocusRequester = searchFocusRequester)
                    else -> {}
                }
            }

            AnimatedVisibility(
                visible = showUserPanel,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    AnimatedVisibility(
                        modifier = Modifier
                            .align(Alignment.Center),
                        visible = showUserPanel,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut()
                    ) {
                        UserPanel(
                            modifier = Modifier
                                .padding(12.dp),
                            username = userViewModel.username,
                            face = userViewModel.face,
                            onHide = { showUserPanel = false },
                            onGoMy = {
                                context.startActivity(Intent(context, UserInfoActivity::class.java))
                            },
                            onGoHistory = {
                                context.startActivity(Intent(context, HistoryActivity::class.java))
                            },
                            onGoFavorite = {
                                context.startActivity(Intent(context, FavoriteActivity::class.java))
                            },
                            onGoFollowing = {
                                context.startActivity(
                                    Intent(
                                        context,
                                        FollowingSeasonActivity::class.java
                                    )
                                )
                            },
                            onGoLater = {
                                context.startActivity(Intent(context, ToViewActivity::class.java))
                            }
                        )
                    }
                }
            }
        }
    }
}
