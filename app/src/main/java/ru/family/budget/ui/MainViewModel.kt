package ru.family.budget.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.family.budget.data.GitHubSync
import ru.family.budget.data.LocalStore
import ru.family.budget.data.SettingsStore
import ru.family.budget.data.SyncConfig
import ru.family.budget.data.SyncEngine
import ru.family.budget.data.SyncException
import ru.family.budget.model.Category
import ru.family.budget.model.Expense
import ru.family.budget.model.FamilyData
import ru.family.budget.model.Merge
import java.time.LocalDate
import java.util.UUID

sealed interface SyncState {
    data object NotConfigured : SyncState
    data object Running : SyncState
    data class Done(val at: Long) : SyncState
    data class Failed(val message: String) : SyncState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val local = LocalStore(app)
    private val settings = SettingsStore(app)

    private val _data = MutableStateFlow(Merge.withDefaults(local.load()))
    val data: StateFlow<FamilyData> = _data.asStateFlow()

    private val _syncState = MutableStateFlow(initialSyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _pending = MutableStateFlow(settings.hasPendingChanges)
    val hasPendingChanges: StateFlow<Boolean> = _pending.asStateFlow()

    private val _config = MutableStateFlow(settings.config)
    val config: StateFlow<SyncConfig> = _config.asStateFlow()

    private val _deviceName = MutableStateFlow(settings.deviceName)
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    private val syncMutex = Mutex()
    private var scheduledSync: Job? = null

    private fun initialSyncState(): SyncState = when {
        !settings.config.isComplete -> SyncState.NotConfigured
        settings.lastSyncAt > 0 -> SyncState.Done(settings.lastSyncAt)
        else -> SyncState.Failed("Ещё не синхронизировано")
    }

    // ---------- Расходы ----------

    fun saveExpense(id: String?, amount: Long, categoryId: String, date: LocalDate, note: String) {
        val now = System.currentTimeMillis()
        val existing = id?.let { eid -> _data.value.expenses.find { it.id == eid } }
        val expense = existing?.copy(
            amount = amount,
            categoryId = categoryId,
            date = date.toString(),
            note = note.trim(),
            updatedAt = now,
        ) ?: Expense(
            id = UUID.randomUUID().toString(),
            amount = amount,
            categoryId = categoryId,
            date = date.toString(),
            note = note.trim(),
            author = settings.deviceName,
            createdAt = now,
            updatedAt = now,
        )
        mutate { Merge.merge(it, FamilyData(expenses = listOf(expense))) }
    }

    fun deleteExpense(id: String) {
        val existing = _data.value.expenses.find { it.id == id } ?: return
        val tombstone = existing.copy(deleted = true, updatedAt = System.currentTimeMillis())
        mutate { Merge.merge(it, FamilyData(expenses = listOf(tombstone))) }
    }

    // ---------- Категории ----------

    /** @return id созданной категории */
    fun addCategory(name: String, emoji: String): String {
        val now = System.currentTimeMillis()
        val category = Category(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            emoji = emoji.trim().ifEmpty { "📦" },
            createdAt = now,
            updatedAt = now,
        )
        mutate { Merge.merge(it, FamilyData(categories = listOf(category))) }
        return category.id
    }

    fun updateCategory(id: String, name: String, emoji: String) {
        val existing = _data.value.categories.find { it.id == id } ?: return
        val updated = existing.copy(
            name = name.trim(),
            emoji = emoji.trim().ifEmpty { "📦" },
            updatedAt = System.currentTimeMillis(),
        )
        mutate { Merge.merge(it, FamilyData(categories = listOf(updated))) }
    }

    fun deleteCategory(id: String) {
        val existing = _data.value.categories.find { it.id == id } ?: return
        val tombstone = existing.copy(deleted = true, updatedAt = System.currentTimeMillis())
        mutate { Merge.merge(it, FamilyData(categories = listOf(tombstone))) }
    }

    // ---------- Настройки ----------

    fun saveSettings(config: SyncConfig, deviceName: String) {
        settings.config = config
        settings.deviceName = deviceName
        _config.value = settings.config
        _deviceName.value = settings.deviceName
        if (!settings.config.isComplete) _syncState.value = SyncState.NotConfigured
        sync()
    }

    // ---------- Синхронизация ----------

    private fun mutate(change: (FamilyData) -> FamilyData) {
        _data.update(change)
        local.save(_data.value)
        settings.hasPendingChanges = true
        _pending.value = true
        scheduleSync()
    }

    /** Отправляем изменения не сразу, а через пару секунд — чтобы несколько правок ушли одним коммитом. */
    private fun scheduleSync() {
        scheduledSync?.cancel()
        scheduledSync = viewModelScope.launch {
            delay(2_000)
            doSync()
        }
    }

    fun sync() {
        scheduledSync?.cancel()
        viewModelScope.launch { doSync() }
    }

    private suspend fun doSync() {
        val config = settings.config
        if (!config.isComplete) {
            _syncState.value = SyncState.NotConfigured
            return
        }
        syncMutex.withLock {
            _syncState.value = SyncState.Running
            try {
                val who = settings.deviceName.ifBlank { android.os.Build.MODEL }
                val merged = withContext(Dispatchers.IO) {
                    SyncEngine(GitHubSync(config)).sync(
                        current = { _data.value },
                        commitMessage = "Расходы: обновление с устройства «$who»",
                    )
                }
                _data.update { Merge.merge(it, merged) }
                local.save(_data.value)
                // Если пока шла синхронизация пользователь что-то изменил — отправим ещё раз.
                val stillPending = _data.value != merged
                settings.hasPendingChanges = stillPending
                _pending.value = stillPending
                val now = System.currentTimeMillis()
                settings.lastSyncAt = now
                _syncState.value = SyncState.Done(now)
                if (stillPending) scheduleSync()
            } catch (e: SyncException) {
                _syncState.value = SyncState.Failed(e.message ?: "Ошибка синхронизации")
            } catch (e: Exception) {
                _syncState.value = SyncState.Failed("Ошибка синхронизации: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

}
