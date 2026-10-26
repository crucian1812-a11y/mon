package ru.family.budget.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.family.budget.importer.ImportItem
import ru.family.budget.model.Category
import ru.family.budget.model.FamilyData
import ru.family.budget.util.Money
import ru.family.budget.util.RuDates
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    data: FamilyData,
    state: ImportState,
    onPickScreenshots: () -> Unit,
    onUpdate: (ImportItem) -> Unit,
    onCommit: () -> Unit,
    onBack: () -> Unit,
) {
    var showText by remember { mutableStateOf(false) }
    val categories = data.categories.filter { !it.deleted }
    val byId = data.categories.associateBy { it.id }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Со скриншота") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (state is ImportState.Ready && state.recognizedText.isNotEmpty()) {
                        TextButton(onClick = { showText = true }) { Text("Текст") }
                    }
                },
            )
        },
        bottomBar = {
            if (state is ImportState.Ready && state.items.isNotEmpty()) {
                val selected = state.items.filter { it.checked }
                val total = selected.sumOf { Money.parse(it.amountText) ?: 0L }
                val invalid = selected.any { Money.parse(it.amountText) == null }
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = onCommit,
                        enabled = selected.isNotEmpty() && !invalid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp)
                            .height(52.dp),
                    ) {
                        Text(
                            if (selected.isEmpty()) "Отметьте расходы для добавления"
                            else "Добавить ${selected.size} на ${Money.format(total)}"
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
            when (state) {
                ImportState.Idle -> EmptyState(
                    text = "Сделайте скриншот истории операций или одной покупки в приложении банка " +
                        "и выберите его здесь. Можно сразу несколько.\n\n" +
                        "Ещё быстрее: в галерее или прямо после скриншота нажмите «Поделиться» → «Семейный бюджет».",
                    onPickScreenshots = onPickScreenshots,
                )

                is ImportState.Processing -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (state.total > 1) "Распознаю скриншоты: ${state.done} из ${state.total}"
                        else "Распознаю скриншот…",
                    )
                    if (state.total > 1) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { state.done.toFloat() / state.total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                is ImportState.Ready -> if (state.items.isEmpty()) {
                    EmptyState(
                        text = (state.errors.joinToString("\n").ifEmpty { "Расходы на скриншоте не найдены." }) +
                            "\n\nЛучше всего распознаются скриншоты списка операций или экрана одной покупки " +
                            "без лишних всплывающих окон.",
                        onPickScreenshots = onPickScreenshots,
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            Text(
                                "Проверьте найденные операции: сумму, категорию и дату можно поправить. " +
                                    "В приложение попадут только отмеченные.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        state.errors.forEach { e ->
                            item { Text("⚠️ $e", color = MaterialTheme.colorScheme.error) }
                        }
                        items(state.items, key = { it.key }) { item ->
                            ImportItemCard(item, categories, byId[item.categoryId], onUpdate)
                        }
                        item {
                            OutlinedButton(onClick = onPickScreenshots, modifier = Modifier.fillMaxWidth()) {
                                Text("Выбрать другие скриншоты")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showText && state is ImportState.Ready) {
        AlertDialog(
            onDismissRequest = { showText = false },
            title = { Text("Распознанный текст") },
            text = {
                Text(
                    state.recognizedText,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = { showText = false }) { Text("Закрыть") } },
        )
    }
}

@Composable
private fun EmptyState(text: String, onPickScreenshots: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🧾", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(16.dp))
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onPickScreenshots) { Text("Выбрать скриншоты") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportItemCard(
    item: ImportItem,
    categories: List<Category>,
    category: Category?,
    onUpdate: (ImportItem) -> Unit,
) {
    var pickCategory by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    val amountValid = Money.parse(item.amountText) != null

    Card(
        colors = if (item.checked) CardDefaults.cardColors()
        else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
    ) {
        Column(Modifier.padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = item.checked, onCheckedChange = { onUpdate(item.copy(checked = it)) })
                OutlinedTextField(
                    value = item.note,
                    onValueChange = { onUpdate(item.copy(note = it)) },
                    label = { Text("Где / что") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = item.amountText,
                    onValueChange = { v -> onUpdate(item.copy(amountText = v.filter { it.isDigit() || it in ",. " })) },
                    label = { Text("₽") },
                    singleLine = true,
                    isError = !amountValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(112.dp),
                )
            }
            Row(
                Modifier.padding(start = 44.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    onClick = { pickCategory = true },
                    label = { Text("${category?.emoji ?: "❔"} ${category?.name ?: "Категория"}", maxLines = 1) },
                )
                AssistChip(
                    onClick = { pickDate = true },
                    label = { Text(RuDates.short(item.date).substringBeforeLast(' ').let { if (item.dateFound) it else "$it?" }) },
                    leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                )
            }
            val hint = when {
                item.duplicate -> "Похоже, этот расход уже внесён (та же дата и сумма)"
                item.transfer -> "Перевод — отметьте, если это расход семьи"
                !item.dateFound -> "Дата на скриншоте не найдена — проверьте"
                else -> null
            }
            if (hint != null) {
                Row(Modifier.padding(start = 48.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.height(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (pickCategory) {
        AlertDialog(
            onDismissRequest = { pickCategory = false },
            title = { Text("Категория") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(categories, key = { it.id }) { c ->
                        Text(
                            "${c.emoji}  ${c.name}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (c.id == item.categoryId) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onUpdate(item.copy(categoryId = c.id))
                                    pickCategory = false
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickCategory = false }) { Text("Отмена") } },
        )
    }

    if (pickDate) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = item.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dpState.selectedDateMillis?.let {
                        val d = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        onUpdate(item.copy(date = d, dateFound = true))
                    }
                    pickDate = false
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Отмена") } },
        ) {
            DatePicker(state = dpState)
        }
    }
}
