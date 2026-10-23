package cn.vectory.biliview.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.vectory.biliview.BVApp
import cn.vectory.biliview.dao.AppDatabase
import cn.vectory.biliview.entity.db.UserDB
import cn.vectory.biliview.repository.UserRepository
import cn.vectory.biliview.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel

@KoinViewModel
class UserSwitchViewModel(
    private val userRepository: UserRepository,
    private val db: AppDatabase = BVApp.getAppDatabase()
) : ViewModel() {
    var loading by mutableStateOf(true)
    var currentUser by mutableStateOf(UserDB(-1, -1, "", "", ""))
    val userDbList = mutableStateListOf<UserDB>()

    fun updateData() {
        viewModelScope.launch(Dispatchers.IO) {
            updateUserDbList()
            withContext(Dispatchers.Main) { loading = false }
        }
    }

    suspend fun updateUserDbList() {
        withContext(Dispatchers.Main) {
            userDbList.clear()
            userDbList.addAll(db.userDao().getAll())
            currentUser = userDbList.find { it.uid == Prefs.uid } ?: UserDB(-1, -1, "", "", "")
        }
    }

    suspend fun switchUser(user: UserDB) {
        userRepository.setUser(user)
    }

    suspend fun deleteUser(userDB: UserDB) {
        db.userDao().delete(userDB)
        updateUserDbList()
        if (userDbList.isNotEmpty()) {
            switchUser(userDbList.first())
        } else {
            userRepository.logout()
        }
    }
}