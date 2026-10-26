package cn.vectory.biliview.entity

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 公共目录账号库文件（biliview_accounts.json）的结构：
 * 人类可读 JSON，包含全部账号与上次使用的账号 uid。
 */
@Serializable
data class AccountBackup(
    val version: Int = 1,
    /** 上次使用的账号 uid，冷启动按此恢复登录态 */
    @SerialName("lastUid")
    val lastUid: Long = 0,
    val accounts: List<AccountBackupEntry> = emptyList()
)

@Serializable
data class AccountBackupEntry(
    val uid: Long,
    val username: String,
    val avatar: String,
    val auth: AuthData
)
