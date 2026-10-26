package ru.family.budget.data

import android.content.Context

data class SyncConfig(
    val token: String = "",
    val owner: String = "",
    val repo: String = "",
    /** Пусто — основная ветка репозитория. */
    val branch: String = "",
    val path: String = DEFAULT_PATH,
) {
    val isComplete: Boolean
        get() = token.isNotBlank() && owner.isNotBlank() && repo.isNotBlank() && path.isNotBlank()

    companion object {
        const val DEFAULT_PATH = "family-budget/data.json"
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var config: SyncConfig
        get() = SyncConfig(
            token = prefs.getString("token", "") ?: "",
            owner = prefs.getString("owner", "") ?: "",
            repo = prefs.getString("repo", "") ?: "",
            branch = prefs.getString("branch", "") ?: "",
            path = prefs.getString("path", SyncConfig.DEFAULT_PATH) ?: SyncConfig.DEFAULT_PATH,
        )
        set(value) {
            prefs.edit()
                .putString("token", value.token.trim())
                .putString("owner", value.owner.trim())
                .putString("repo", value.repo.trim())
                .putString("branch", value.branch.trim())
                .putString("path", value.path.trim().trim('/'))
                .apply()
        }

    var deviceName: String
        get() = prefs.getString("deviceName", "") ?: ""
        set(value) = prefs.edit().putString("deviceName", value.trim()).apply()

    /** Есть локальные изменения, ещё не отправленные в GitHub. */
    var hasPendingChanges: Boolean
        get() = prefs.getBoolean("pending", false)
        set(value) = prefs.edit().putBoolean("pending", value).apply()

    var lastSyncAt: Long
        get() = prefs.getLong("lastSyncAt", 0)
        set(value) = prefs.edit().putLong("lastSyncAt", value).apply()
}
