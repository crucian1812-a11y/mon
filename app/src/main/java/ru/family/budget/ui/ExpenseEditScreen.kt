package ru.family.budget.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.family.budget.model.FamilyData
import ru.family.budget.util.Money
import ru.family.budget.util.RuDates
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpenseEditScreen(
    data: FamilyData,
    expenseId: String?,
    onSave: (amount: Long, categoryId: String, date: LocalDate, note: String) -> Unit,
    onDelete: () -> Unit,
    onAddCategory: (name: String, emoji: String) -> String,
    onBack: () -> Unit,
) {
    val existing = remember(expenseId) { data.expenses.find { it.id == expenseId && !it.deleted } }
    var amountText by rememberSaveable { mutableStateOf(existing?.let { Money.toInput(it.amount) } ?: "") }
    var categoryId by rememberSaveable { mutableStateOf(existing?.categoryId) }
    var dateStr by rememberSaveable { mutableStateOf(existing?.date ?: LocalDate.now().toString()) }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showNewCategory by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val date = LocalDate.parse(dateStr)
    val today = LocalDate.now()
    val amount = Money.parse(amountText)
    val activeCategories = data.categories.filter { !it.deleted || it.id == categoryId }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { if (existing == null) focus.requestFocus() }

    fun trySave() {
        showErrors = true
        val cat = categoryId
        if (amount != null && cat != null) onSave(amount, cat, date, note)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Новый расход" else "Изменить расход") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Удалить")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = amountText,
                onValueChange = { v -> amountText = v.filter { it.isDigit() || it in ",. +" } },
                label = { Text("Сумма") },
                suffix = { Text("₽") },
                placeholder = { Text("0") },
                supportingText = {
                    when {
                        showErrors && amount == null -> Text("Введите сумму, например 1250 или 99,90")
                        amountText.contains('+') && amount != null -> Text("Итого: ${Money.format(amount)}")
                        else -> Text("Можно складывать: 350+120,50")
                    }
                },
                isError = showErrors && amount == null,
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineMedium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Категория",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (showErrors && categoryId == null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    activeCategories.forEach { c ->
                        FilterChip(
                            selected = c.id == categoryId,
                            onClick = { categoryId = c.id },
                            label = { Text("${c.emoji} ${c.name}") },
                        )
                    }
                    AssistChip(
                        onClick = { showNewCategory = true },
                        label = { Text("Новая") },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Дата", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = date == today,
                        onClick = { dateStr = today.toString() },
                        label = { Text("Сегодня") },
                    )
                    FilterChip(
                        selected = date == today.minusDays(1),
                        onClick = { dateStr = today.minusDays(1).toString() },
                        label = { Text("Вчера") },
                    )
                    FilterChip(
                        selected = date != today && date != today.minusDays(1),
                        onClick = { showDatePicker = true },
                        label = { Text(if (date != today && date != today.minusDays(1)) RuDates.short(date) else "Другая") },
                        leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                    )
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Комментарий (необязательно)") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            Button(onClick = ::trySave, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Сохранить")
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        dateStr = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDatePicker = false
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Отмена") } },
        ) {
            DatePicker(state = state)
        }
    }

    if (showNewCategory) {
        CategoryDialog(
            title = "Новая категория",
            initialName = "",
            initialEmoji = "",
            onDismiss = { showNewCategory = false },
            onConfirm = { name, emoji ->
                categoryId = onAddCategory(name, emoji)
                showNewCategory = false
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить расход?") },
            text = { Text("Расход удалится на обоих телефонах.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}
