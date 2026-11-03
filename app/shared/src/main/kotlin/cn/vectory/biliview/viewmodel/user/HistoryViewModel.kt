package cn.vectory.biliview.viewmodel.user

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aaa1115910.biliapi.http.entity.AuthFailureException
import dev.aaa1115910.biliapi.repositories.HistoryRepository
import cn.vectory.biliview.BVApp
import cn.vectory.biliview.shared.BuildConfig
import cn.vectory.biliview.shared.R
import cn.vectory.biliview.entity.carddata.VideoCardData
import cn.vectory.biliview.repository.UserRepository
import cn.vectory.biliview.util.Prefs
import cn.vectory.biliview.util.fInfo
import cn.vectory.biliview.util.fWarn
import cn.vectory.biliview.util.formatHourMinSec
import cn.vectory.biliview.util.toast
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class HistoryViewModel(
    private val userRepository: UserRepository,
    private val historyRepository: HistoryRepository
) : ViewModel() {
    companion object {
        private val logger = KotlinLogging.logger { }
    }

    var histories = mutableStateListOf<VideoCardData>()
    var noMore by mutableStateOf(false)

    private var cursor = 0L
    private var updateJob: Job? = null
    private var updateGeneration = 0
    private var loadedUid by mutableStateOf(userRepository.uid)
    private var loadedLogin by mutableStateOf(userRepository.isLogin)
    var updating by mutableStateOf(false)
    var hasLoaded by mutableStateOf(false)
        private set
    var loadError by mutableStateOf(false)
        private set
    val isLogin get() = userRepository.isLogin
    val userId get() = userRepository.uid
    val isCurrentAccount get() = loadedUid == userId && loadedLogin == isLogin

    fun syncAccount() {
        if (!isCurrentAccount) clearData()
    }

    fun update() {
        syncAccount()
        if (updating || noMore) return
        val generation = ++updateGeneration
        val requestUserId = userRepository.uid
        val requestLogin = userRepository.isLogin
        updating = true
        loadError = false
        updateJob = viewModelScope.launch {
            try {
                if (updateHistories(requestUserId, requestLogin)) hasLoaded = true
            } catch (e: CancellationException) {
                throw e
            } finally {
                if (generation == updateGeneration) {
                    updating = false
                    updateJob = null
                }
            }
        }
    }

    fun cancelUpdate() {
        updateGeneration++
        updateJob?.cancel()
        updateJob = null
        updating = false
    }

    fun clearData() {
        cancelUpdate()
        histories.clear()
        cursor = 0L
        noMore = false
        hasLoaded = false
        loadError = false
        loadedUid = userRepository.uid
        loadedLogin = userRepository.isLogin
    }

    private suspend fun updateHistories(requestUserId: Long, requestLogin: Boolean, context: Context = BVApp.context): Boolean {
        logger.fInfo { "Updating histories with params [cursor=$cursor, apiType=${Prefs.apiType}]" }
        try {
            val data = withContext(Dispatchers.IO) {
                historyRepository.getHistories(
                    cursor = cursor,
                    preferApiType = Prefs.apiType
                )
            }
            if (requestUserId != userRepository.uid || requestLogin != userRepository.isLogin) return false

            data.data.forEach { historyItem ->
                histories.add(
                    VideoCardData(
                        avid = historyItem.oid,
                        title = historyItem.title,
                        cover = historyItem.cover,
                        upName = historyItem.author,
                        timeString = if (historyItem.progress == -1) context.getString(R.string.play_time_finish)
                        else context.getString(
                            R.string.play_time_history,
                            (historyItem.progress * 1000L).formatHourMinSec(),
                            (historyItem.duration * 1000L).formatHourMinSec()
                        )
                    )
                )
            }
            cursor = data.cursor
            logger.fInfo { "Update history cursor: [cursor=$cursor]" }
            logger.fInfo { "Update histories success" }
            if (cursor == 0L) {
                noMore = true
                logger.fInfo { "No more history" }
            }
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (requestUserId != userRepository.uid || requestLogin != userRepository.isLogin) return false
            logger.fWarn { "Update histories failed: ${e.stackTraceToString()}" }
            loadError = true
            if (e is AuthFailureException) {
                BVApp.context.getString(R.string.exception_auth_failure).toast(BVApp.context)
                logger.fInfo { "User auth failure" }
                if (!BuildConfig.DEBUG) {
                    withContext(NonCancellable + Dispatchers.IO) {
                        if (requestUserId == userRepository.uid && requestLogin == userRepository.isLogin) {
                            userRepository.logout()
                        }
                    }
                }
            }
            return false
        }
    }
}