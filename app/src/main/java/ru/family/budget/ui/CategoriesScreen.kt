package ru.family.budget.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import ru.family.budget.model.Category
import ru.family.budget.model.FamilyData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    data: FamilyData,
    onAdd: (name: String, emoji: String) -> Unit,
    onUpdate: (id: String, name: String, emoji: String) -> Unit,
    onDelete: (id: String) -> Unit,
    onBack: () -> Unit,
) {
    val categories = data.categories.filter { !it.deleted }
    val usage = remember(data) {
        data.expenses.filter { !it.deleted }.groupingBy { it.categoryId }.eachCount()
    }
    var adding by remember { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Категории") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Категория") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(categories, key = { it.id }) { c ->
                Card(Modifier.fillMaxWidth().clickable { editingId = c.id }) {
                    Row(
                        Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(c.emoji, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.bodyLarge)
                            val n = usage[c.id] ?: 0
                            if (n > 0) {
                                Text(
                                    "${n} ${plural(n, "расход", "расхода", "расходов")}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = { editingId = c.id }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Изменить")
                        }
                        IconButton(onClick = { deletingId = c.id }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Удалить")
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        CategoryDialog(
            title = "Новая категория",
            initialName = "",
            initialEmoji = "",
            onDismiss = { adding = false },
            onConfirm = { name, emoji -> onAdd(name, emoji); adding = false },
        )
    }

    editingId?.let { id ->
        val c = categories.find { it.id == id }
        if (c == null) {
            editingId = null
        } else {
            CategoryDialog(
                title = "Изменить категорию",
                initialName = c.name,
                initialEmoji = c.emoji,
                onDismiss = { editingId = null },
                onConfirm = { name, emoji -> onUpdate(id, name, emoji); editingId = null },
            )
        }
    }

    deletingId?.let { id ->
        val c: Category? = categories.find { it.id == id }
        val n = usage[id] ?: 0
        AlertDialog(
            onDismissRequest = { deletingId = null },
            title = { Text("Удалить «${c?.name}»?") },
            text = {
                Text(
                    if (n > 0) "Уже внесённые расходы ($n) останутся в истории с этой категорией, " +
                        "но выбрать её для новых расходов будет нельзя."
                    else "Категория исчезнет на обоих телефонах."
                )
            },
            confirmButton = {
                TextButton(onClick = { onDelete(id); deletingId = null }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { deletingId = null }) { Text("Отмена") } },
        )
    }
}

private val EmojiSuggestions = listOf(
    "🛒", "🍽️", "☕", "🚌", "🚗", "⛽", "🏠", "💡", "📱", "💊", "🩺", "👕", "👟", "🧸", "🎓",
    "🧺", "🛠️", "🎬", "🎮", "🎁", "💄", "💇", "📚", "🐾", "✈️", "🏖️", "⚽", "🍺", "🚬", "💳",
    "🏦", "🧾", "🌿", "🎉", "📦",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryDialog(
    title: String,
    initialName: String,
    initialEmoji: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, emoji: String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var emoji by rememberSaveable { mutableStateOf(initialEmoji.ifEmpty { "📦" }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Название") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it.take(8) },
                    label = { Text("Значок") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow {
                    EmojiSuggestions.forEach { e ->
                        TextButton(onClick = { emoji = e }, contentPadding = PaddingValues(4.dp)) {
                            Text(e, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, emoji) }, enabled = name.isNotBlank()) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private fun plural(n: Int, one: String, few: String, many: String): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}
