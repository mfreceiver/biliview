package cn.vectory.biliview.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import cn.vectory.biliview.BVApp
import cn.vectory.biliview.dao.AppDatabase
import cn.vectory.biliview.entity.AccountBackup
import cn.vectory.biliview.entity.AccountBackupEntry
import cn.vectory.biliview.entity.db.UserDB
import cn.vectory.biliview.util.Prefs
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single

/**
 * 公共目录账号库（方案 A：明文凭证放用户授权的 SAF 目录）。
 *
 * 授权目录 URI 存 [Prefs.accountBackupDir]；注意 Android 卸载重装会清掉 persisted
 * URI permissions，重装后需在设置里重新指一次目录；未授权/文件不存在时静默跳过。
 *
 * 不依赖 androidx.documentfile，直接用 DocumentsContract 操作目录树。
 */
@Single
class AccountBackupManager(
    private val db: AppDatabase = BVApp.getAppDatabase()
) {
    companion object {
        private val logger = KotlinLogging.logger { }
        const val FILE_NAME = "biliview_accounts.json"
        private const val MIME_TYPE = "application/json"
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** 是否已授权备份目录 */
    val enabled: Boolean
        get() = Prefs.accountBackupDir.isNotBlank()

    /**
     * 应用侧 → 文件：把「Room 全部账号 ∪ 文件中已有账号」按 uid 去重合并后写入账号库文件
     * （同 uid 本地覆盖文件，仅文件中有的账号保留，防止重装后本地账号少时把文件冲掉），
     * lastUid 记为当前账号。未授权目录或 IO 失败时抛出，由调用方决定是否忽略。
     */
    suspend fun syncToBackup(lastUid: Long, context: Context = BVApp.context) {
        val treeUri = treeUri() ?: error("Account backup dir not authorized")
        val localEntries = db.userDao().getAll().mapNotNull { user ->
            runCatching {
                AccountBackupEntry(
                    uid = user.uid,
                    username = user.username,
                    avatar = user.avatar,
                    auth = cn.vectory.biliview.entity.AuthData.fromJson(user.auth)
                )
            }.getOrNull().also {
                if (it == null) {
                    logger.warn { "Skip user ${user.uid} with invalid auth json in backup" }
                }
            }
        }
        // 先读旧文件合并，再覆盖写：未读取合并前绝不写，避免抹除文件中本地没有的账号
        val merged = LinkedHashMap<Long, AccountBackupEntry>()
        readBackup(context)?.accounts?.forEach { merged[it.uid] = it }
        localEntries.forEach { merged[it.uid] = it }
        writeBackup(AccountBackup(lastUid = lastUid, accounts = merged.values.toList()), treeUri, context)
        logger.info { "Account backup synced: ${merged.size} accounts, lastUid=$lastUid" }
    }

    /**
     * 文件 → 应用侧：读取账号库文件，把文件中的账号 upsert 进 Room
     * （本地已有的账号以本地为准，不覆盖 auth/用户名/头像；仅补充本地没有的账号）。
     *
     * @return 解析出的 [AccountBackup]；未授权目录、文件不存在或解析失败时返回 null。
     */
    suspend fun readBackup(context: Context = BVApp.context): AccountBackup? {
        val treeUri = treeUri() ?: return null
        val fileUri = findFileUri(treeUri, context) ?: return null
        val content = runCatching {
            context.contentResolver.openInputStream(fileUri)?.use { input ->
                input.bufferedReader().readText()
            } ?: return null
        }.getOrNull() ?: return null
        return runCatching { json.decodeFromString<AccountBackup>(content) }
            .onFailure {
                logger.warn { "Parse account backup failed: ${it.stackTraceToString()}" }
            }
            .getOrNull()
    }

    /** 把备份中的账号补进 Room（本地已有则跳过），返回库中现有的全部账号。 */
    suspend fun upsertToRoom(backup: AccountBackup): List<UserDB> {
        backup.accounts.forEach { entry ->
            val exist = db.userDao().findUserByUid(entry.uid)
            if (exist == null) {
                db.userDao().insert(
                    UserDB(
                        uid = entry.uid,
                        username = entry.username,
                        avatar = entry.avatar,
                        auth = entry.auth.toJson()
                    )
                )
            }
        }
        return db.userDao().getAll()
    }

    private fun treeUri(): Uri? =
        runCatching { Uri.parse(Prefs.accountBackupDir) }.getOrNull()

    private fun writeBackup(backup: AccountBackup, treeUri: Uri, context: Context) {
        val resolver = context.contentResolver
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val targetUri = findFileUri(treeUri, context)
            ?: DocumentsContract.createDocument(
                resolver,
                DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId),
                MIME_TYPE,
                FILE_NAME
            )
            ?: error("Can not create account backup file")
        resolver.openOutputStream(targetUri, "rwt")?.use { output ->
            output.write(json.encodeToString(backup).toByteArray())
        } ?: error("Can not open account backup file for writing")
    }

    private fun findFileUri(treeUri: Uri, context: Context): Uri? {
        val resolver = context.contentResolver
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
        return runCatching {
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null,
                null,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(1) == FILE_NAME) {
                        return DocumentsContract.buildDocumentUriUsingTree(
                            treeUri, cursor.getString(0)
                        )
                    }
                }
                null
            }
        }.getOrNull()
    }
}
