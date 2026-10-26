package ru.family.budget.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.family.budget.model.Category
import ru.family.budget.model.Expense
import ru.family.budget.model.FamilyData
import ru.family.budget.util.Money
import ru.family.budget.util.RuDates
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    data: FamilyData,
    syncState: SyncState,
    pending: Boolean,
    onSync: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onCategories: () -> Unit,
    onSettings: () -> Unit,
) {
    var monthKey by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthKey)
    var filterCategoryId by rememberSaveable { mutableStateOf<String?>(null) }

    val categoriesById = remember(data) { data.categories.associateBy { it.id } }
    val monthExpenses = remember(data, monthKey) {
        val prefix = "$monthKey-"
        data.expenses.filter { !it.deleted && it.date.startsWith(prefix) }
    }
    val total = monthExpenses.sumOf { it.amount }
    val byCategory = remember(monthExpenses) {
        monthExpenses.groupBy { it.categoryId }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }
    }
    val shown = monthExpenses.filter { filterCategoryId == null || it.categoryId == filterCategoryId }
    val byDay = shown.groupBy { it.date }.toSortedMap(reverseOrder())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Семейный бюджет") },
                actions = {
                    SyncButton(syncState, pending, onSync)
                    IconButton(onClick = onCategories) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Категории")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Настройки")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Расход") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = { monthKey = month.minusMonths(1).toString() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Предыдущий месяц")
                    }
                    Text(
                        RuDates.month(month),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.clickable { monthKey = YearMonth.now().toString() },
                    )
                    IconButton(onClick = { monthKey = month.plusMonths(1).toString() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Следующий месяц")
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Потрачено за месяц", style = MaterialTheme.typography.labelLarge)
                        Text(
                            Money.format(total),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        SyncStatusLine(syncState, pending, onSettings)
                    }
                }
            }

            if (byCategory.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("По категориям", style = MaterialTheme.typography.titleSmall)
                            byCategory.forEach { (catId, sum) ->
                                CategoryBar(
                                    category = categoriesById[catId],
                                    sum = sum,
                                    share = if (total > 0) sum.toFloat() / total else 0f,
                                    selected = filterCategoryId == catId,
                                    onClick = { filterCategoryId = if (filterCategoryId == catId) null else catId },
                                )
                            }
                        }
                    }
                }
            }

            if (filterCategoryId != null) {
                item {
                    val c = categoriesById[filterCategoryId]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Показаны: ${c?.emoji ?: ""} ${c?.name ?: "Без категории"}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { filterCategoryId = null }) {
                            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Все")
                        }
                    }
                }
            }

            if (shown.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "В этом месяце расходов пока нет.\nНажмите «+ Расход», чтобы добавить.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            byDay.forEach { (dateStr, list) ->
                item(key = "day-$dateStr") {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            RuDates.day(LocalDate.parse(dateStr)),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            Money.format(list.sumOf { it.amount }),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(list.sortedByDescending { it.createdAt }, key = { it.id }) { e ->
                    ExpenseRow(e, categoriesById[e.categoryId]) { onEdit(e.id) }
                }
            }
        }
    }
}

@Composable
private fun SyncButton(state: SyncState, pending: Boolean, onSync: () -> Unit) {
    IconButton(onClick = onSync, enabled = state !is SyncState.Running) {
        if (state is SyncState.Running) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        } else {
            Box {
                Icon(Icons.Filled.Refresh, contentDescription = "Синхронизировать")
                if (pending || state is SyncState.Failed || state is SyncState.NotConfigured) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error)
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncStatusLine(state: SyncState, pending: Boolean, onSettings: () -> Unit) {
    val style = MaterialTheme.typography.bodySmall
    when (state) {
        SyncState.NotConfigured -> Text(
            "⚠️ Синхронизация с GitHub не настроена — нажмите, чтобы настроить",
            style = style,
            modifier = Modifier.clickable(onClick = onSettings),
        )
        SyncState.Running -> Text("Синхронизация…", style = style)
        is SyncState.Failed -> Text(
            "⚠️ ${state.message}" + if (pending) "\nИзменения сохранены на телефоне и отправятся позже." else "",
            style = style,
            color = MaterialTheme.colorScheme.error,
        )
        is SyncState.Done -> Text(
            if (pending) "Есть неотправленные изменения" else "✓ Синхронизировано в ${formatTime(state.at)}",
            style = style,
        )
    }
}

private fun formatTime(millis: Long): String {
    val sameDay = LocalDate.now().toEpochDay() ==
        java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
    val pattern = if (sameDay) "HH:mm" else "dd.MM HH:mm"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(millis))
}

@Composable
private fun CategoryBar(category: Category?, sum: Long, share: Float, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${category?.emoji ?: "❔"} ${category?.name ?: "Без категории"}",
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${Money.format(sum)}  ${(share * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { share },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun ExpenseRow(expense: Expense, category: Category?, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(category?.emoji ?: "❔", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    category?.name ?: "Без категории",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = listOf(expense.note, expense.author).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotEmpty()) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(Money.format(expense.amount), style = MaterialTheme.typography.titleMedium)
        }
    }
}

