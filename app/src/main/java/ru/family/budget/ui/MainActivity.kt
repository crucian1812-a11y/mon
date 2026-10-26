package ru.family.budget.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)
        setContent {
            FamilyBudgetTheme {
                App(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Скриншоты, присланные через «Поделиться» из галереи или приложения банка. */
    private fun handleShare(intent: Intent?) {
        intent ?: return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND ->
                listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        vm.importScreenshots(uris)
    }
}

private const val HOME = "home"
private const val CATEGORIES = "categories"
private const val SETTINGS = "settings"
private const val IMPORT = "import"
private const val NEW_EXPENSE = "edit:"

@Composable
private fun App(vm: MainViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val syncState by vm.syncState.collectAsStateWithLifecycle()
    val pending by vm.hasPendingChanges.collectAsStateWithLifecycle()
    val config by vm.config.collectAsStateWithLifecycle()
    val deviceName by vm.deviceName.collectAsStateWithLifecycle()
    val importState by vm.importState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Каждый раз, когда приложение открывается, подтягиваем изменения со второго телефона.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.sync() }

    val stack: SnapshotStateList<String> = rememberSaveable(
        saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() })
    ) { mutableStateListOf(HOME) }
    val route = stack.last()
    fun go(r: String) = stack.add(r)
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

    BackHandler(enabled = stack.size > 1) { back() }

    // Системный выбор фото — разрешение на доступ к галерее не нужно.
    val pickScreenshots = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris -> vm.importScreenshots(uris) }
    fun openPicker() = pickScreenshots.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    LaunchedEffect(vm) {
        vm.openImport.collect { if (stack.last() != IMPORT) go(IMPORT) }
    }

    when {
        route == HOME -> HomeScreen(
            data = data,
            syncState = syncState,
            pending = pending,
            onSync = vm::sync,
            onAdd = { go(NEW_EXPENSE) },
            onFromScreenshot = { if (importState == ImportState.Idle) openPicker() else go(IMPORT) },
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
        route == IMPORT -> ImportScreen(
            data = data,
            state = importState,
            onPickScreenshots = ::openPicker,
            onUpdate = vm::updateImportItem,
            onCommit = {
                val n = vm.commitImport()
                Toast.makeText(context, "Добавлено расходов: $n", Toast.LENGTH_SHORT).show()
                back()
            },
            onBack = {
                vm.cancelImport()
                back()
            },
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
