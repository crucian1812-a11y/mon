package ru.family.budget.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FamilyBudgetTheme {
                App()
            }
        }
    }
}

private const val HOME = "home"
private const val CATEGORIES = "categories"
private const val SETTINGS = "settings"
private const val NEW_EXPENSE = "edit:"

@Composable
private fun App(vm: MainViewModel = viewModel()) {
    val data by vm.data.collectAsStateWithLifecycle()
    val syncState by vm.syncState.collectAsStateWithLifecycle()
    val pending by vm.hasPendingChanges.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val deviceName by vm.deviceName.collectAsStateWithLifecycle()

    // Каждый раз, когда приложение открывается, подтягиваем изменения со второго телефона.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.sync() }

    val stack: SnapshotStateList<String> = rememberSaveable(
        saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })
    ) { mutableStateListOf(HOME) }
    val route = stack.last()
    fun go(r: String) = stack.add(r)
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    BackHandler(enabled = stack.size > 1) { back() }

    when {
        route == HOME -> HomeScreen(
            data = data,
            syncState = syncState,
            pending = pending,
            onSync = vm::sync,
            onAdd = { go(NEW_EXPENSE) },
            onEdit = { go(NEW_EXPENSE + it) },
            onCategories = { go(CATEGORIES) },
            onSettings = { go(SETTINGS) },
        )
        route.startsWith(NEW_EXPENSE) -> {
            val id = route.removePrefix(NEW_EXPENSE).ifEmpty { null }
            ExpenseEditScreen(
                data = data,
                expenseId = id,
                onSave = { amount, categoryId, date, note ->
                    vm.saveExpense(id, amount, categoryId, date, note)
                    back()
                },
                onDelete = {
                    if (id != null) vm.deleteExpense(id)
                    back()
                },
                onAddCategory = vm::addCategory,
                onBack = ::back,
            )
        }
        route == CATEGORIES -> CategoriesScreen(
            data = data,
            onAdd = { name, emoji -> vm.addCategory(name, emoji) },
            onUpdate = vm::updateCategory,
            onDelete = vm::deleteCategory,
            onBack = ::back,
        )
        route == SETTINGS -> SettingsScreen(
            config = config,
            deviceName = deviceName,
            syncState = syncState,
            onSave = { cfg, name -> vm.saveSettings(cfg, name) },
            onBack = ::back,
        )
    }
}

private fun <T> List<T>.toMutableStateList(): SnapshotStateList<T> =
    mutableStateListOf<T>().also { it.addAll(this) }
