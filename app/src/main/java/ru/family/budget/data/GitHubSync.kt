package ru.family.budget.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import ru.family.budget.model.FamilyData
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64

class SyncException(message: String, cause: Throwable? = null) : Exception(message, cause)
class ConflictException : Exception("Файл в GitHub изменился во время отправки")

/**
 * Хранение данных в репозитории GitHub через Contents API:
 * один JSON-файл, каждое сохранение — отдельный коммит (видна вся история изменений).
 */
class GitHubSync(
    private val config: SyncConfig,
    private val api: String = "https://api.github.com",
) {

    data class Remote(val data: FamilyData?, val sha: String?)

    private val plainJson = Json { ignoreUnknownKeys = true }

    fun fetch(): Remote {
        val ref = if (config.branch.isNotBlank()) "?ref=${enc(config.branch)}" else ""
        val (code, body) = request("GET", "${contentsUrl()}$ref")
        if (code == 404) {
            // Файла ещё нет — либо репозиторий/ветка не найдены. Проверяем, что сам репозиторий доступен.
            val (repoCode, _) = request("GET", "$api/repos/${enc(config.owner)}/${enc(config.repo)}")
            if (repoCode != 200) throw error(repoCode, "")
            return Remote(null, null)
        }
        if (code != 200) throw error(code, body)

        val obj = plainJson.parseToJsonElement(body).jsonObject
        val sha = obj.str("sha") ?: throw SyncException("Неожиданный ответ GitHub: нет sha")
        val encoding = obj.str("encoding")
        val content = if (encoding == "base64" && !obj.str("content").isNullOrEmpty()) {
            obj.str("content")!!
        } else {
            // Файлы больше 1 МБ Contents API отдаёт без содержимого — читаем через Git Blob API.
            val (blobCode, blobBody) = request(
                "GET", "$api/repos/${enc(config.owner)}/${enc(config.repo)}/git/blobs/$sha"
            )
            if (blobCode != 200) throw error(blobCode, blobBody)
            plainJson.parseToJsonElement(blobBody).jsonObject.str("content") ?: ""
        }
        val text = String(Base64.getMimeDecoder().decode(content), Charsets.UTF_8)
        val data = try {
            AppJson.decodeFromString(FamilyData.serializer(), text)
        } catch (e: Exception) {
            throw SyncException("Файл ${config.path} в GitHub повреждён или имеет другой формат", e)
        }
        return Remote(data, sha)
    }

    /** @throws ConflictException если файл успел измениться (нужно повторить синхронизацию). */
    fun push(data: FamilyData, sha: String?, message: String) {
        val text = AppJson.encodeToString(FamilyData.serializer(), data)
        val payload: JsonObject = buildJsonObject {
            put("message", message)
            put("content", Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8)))
            if (sha != null) put("sha", sha)
            if (config.branch.isNotBlank()) put("branch", config.branch)
        }
        val (code, body) = request("PUT", contentsUrl(), payload.toString())
        when (code) {
            200, 201 -> Unit
            409 -> throw ConflictException()
            422 -> if (body.contains("sha", ignoreCase = true)) throw ConflictException() else throw error(code, body)
            else -> throw error(code, body)
        }
    }

    private fun contentsUrl(): String {
        val path = config.path.split('/').filter { it.isNotEmpty() }.joinToString("/") { enc(it) }
        return "$api/repos/${enc(config.owner)}/${enc(config.repo)}/contents/$path"
    }

    private fun request(method: String, url: String, body: String? = null): Pair<Int, String> {
        val conn = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw SyncException("Нет соединения с GitHub", e)
        }
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.useCaches = false
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("Authorization", "Bearer ${config.token.trim()}")
            conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            conn.setRequestProperty("User-Agent", "FamilyBudget-Android")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            return code to text
        } catch (e: IOException) {
            throw SyncException("Нет соединения с GitHub. Проверьте интернет.", e)
        } finally {
            conn.disconnect()
        }
    }

    private fun error(code: Int, body: String): SyncException {
        val ghMessage = try {
            plainJson.parseToJsonElement(body).jsonObject.str("message")
        } catch (e: Exception) {
            null
        }
        val text = when (code) {
            401 -> "GitHub не принял токен. Проверьте токен в настройках (возможно, он истёк)."
            403 -> "Нет прав на запись или превышен лимит запросов. Токену нужен доступ «Contents: Read and write»."
            404 -> "Репозиторий ${config.owner}/${config.repo} или ветка не найдены (или у токена нет к ним доступа)."
            else -> "Ошибка GitHub ($code)"
        }
        return SyncException(if (ghMessage != null && code !in listOf(401, 404)) "$text: $ghMessage" else text)
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
