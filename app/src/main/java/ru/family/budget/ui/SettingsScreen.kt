package ru.family.budget.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import ru.family.budget.data.SyncConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    config: SyncConfig,
    deviceName: String,
    syncState: SyncState,
    onSave: (SyncConfig, String) -> Unit,
    onBack: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(deviceName) }
    var owner by rememberSaveable { mutableStateOf(config.owner) }
    var repo by rememberSaveable { mutableStateOf(config.repo) }
    var branch by rememberSaveable { mutableStateOf(config.branch) }
    var path by rememberSaveable { mutableStateOf(config.path) }
    var token by rememberSaveable { mutableStateOf(config.token) }
    var showToken by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(30) },
                label = { Text("Чей это телефон") },
                placeholder = { Text("Например: Мама") },
                supportingText = { Text("Имя показывается рядом с расходами, которые внесены с этого телефона") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Хранение в GitHub", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = owner,
                onValueChange = { owner = it.trim() },
                label = { Text("Владелец репозитория (логин GitHub)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = repo,
                onValueChange = { repo = it.trim() },
                label = { Text("Название репозитория") },
                placeholder = { Text("family-budget-data") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it.trim() },
                label = { Text("Токен доступа GitHub") },
                placeholder = { Text("github_pat_…") },
                singleLine = true,
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { showToken = !showToken }) { Text(if (showToken) "Скрыть" else "Показать") }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = branch,
                onValueChange = { branch = it.trim() },
                label = { Text("Ветка (необязательно)") },
                supportingText = { Text("Пусто — основная ветка репозитория") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = path,
                onValueChange = { path = it.trim() },
                label = { Text("Файл с данными") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    onSave(
                        SyncConfig(
                            token = token,
                            owner = owner,
                            repo = repo,
                            branch = branch,
                            path = path.ifBlank { SyncConfig.DEFAULT_PATH },
                        ),
                        name,
                    )
                },
                enabled = syncState !is SyncState.Running,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Сохранить и синхронизировать")
            }

            when (syncState) {
                SyncState.Running -> Text("Синхронизация…")
                is SyncState.Done -> Text("✓ Синхронизация работает", color = MaterialTheme.colorScheme.primary)
                is SyncState.Failed -> Text("⚠️ ${syncState.message}", color = MaterialTheme.colorScheme.error)
                SyncState.NotConfigured -> Unit
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text(
                    HELP,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

private val HELP = """
Как настроить (один раз):

1. На github.com создайте приватный репозиторий, например family-budget-data.

2. Создайте токен: Settings → Developer settings → Personal access tokens → Fine-grained tokens → Generate new token.
   • Repository access: Only select repositories → выберите этот репозиторий.
   • Permissions → Repository permissions → Contents: Read and write.
   • Expiration — можно выбрать максимальный срок.

3. На ОБОИХ телефонах введите одинаковые владельца, репозиторий и токен, а «Чей это телефон» — у каждого своё.

Расходы сохраняются на телефоне сразу (работает и без интернета) и отправляются в GitHub при каждом изменении и при открытии приложения. Если оба телефона внесли расходы одновременно — данные объединятся, ничего не потеряется.
""".trimIndent()
