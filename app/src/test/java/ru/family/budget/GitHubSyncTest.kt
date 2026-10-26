package ru.family.budget

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Before
import org.junit.Test
import ru.family.budget.data.GitHubSync
import ru.family.budget.data.SyncConfig
import ru.family.budget.data.SyncEngine
import ru.family.budget.data.SyncException
import ru.family.budget.model.Expense
import ru.family.budget.model.FamilyData
import ru.family.budget.model.Merge
import java.security.MessageDigest
import java.util.Base64

/** Имитация GitHub Contents API: один файл, sha от содержимого, 409 при устаревшем sha. */
class GitHubSyncTest {
    private val server = MockWebServer()
    private var fileContent: String? = null
    private val sha get() = fileContent?.let { sha1(it) }
    private var commits = 0
    private var beforeNextPut: (() -> Unit)? = null
    private val config = SyncConfig(token = "t", owner = "me", repo = "data", path = "family-budget/data.json")
    private val api get() = GitHubSync(config, server.url("").toString().trimEnd('/'))

    @Before
    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val (code, body) = handle(request)
                return MockResponse().setResponseCode(code).setBody(body)
            }
        }
        server.start()
    }

    private fun handle(ex: RecordedRequest): Pair<Int, String> {
        val path = ex.requestUrl!!.encodedPath
        return when {
            ex.getHeader("Authorization") != "Bearer t" -> 401 to """{"message":"Bad credentials"}"""
            path == "/repos/me/data" -> 200 to "{}"
            path != "/repos/me/data/contents/family-budget/data.json" -> 404 to """{"message":"Not Found"}"""
            ex.method == "GET" -> {
                val c = fileContent
                if (c == null) 404 to """{"message":"Not Found"}"""
                else {
                    // GitHub разбивает base64 на строки по 60 символов
                    val b64 = Base64.getEncoder().encodeToString(c.toByteArray()).chunked(60).joinToString("\\n")
                    200 to """{"sha":"$sha","encoding":"base64","content":"$b64"}"""
                }
            }
            ex.method == "PUT" -> {
                beforeNextPut?.also { beforeNextPut = null }?.invoke()
                val req = Json.parseToJsonElement(ex.body.readUtf8()).jsonObject
                val reqSha = req["sha"]?.jsonPrimitive?.content
                when {
                    fileContent != null && reqSha == null -> 422 to """{"message":"Invalid request. \"sha\" wasn't supplied."}"""
                    fileContent != null && reqSha != sha -> 409 to """{"message":"does not match"}"""
                    else -> {
                        fileContent = String(Base64.getDecoder().decode(req["content"]!!.jsonPrimitive.content))
                        commits++
                        201 to "{}"
                    }
                }
            }
            else -> 405 to "{}"
        }
    }

    @After
    fun stop() = server.shutdown()

    private fun sha1(s: String) =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun exp(id: String, amount: Long, author: String) = Expense(
        id = id, amount = amount, categoryId = "default-groceries", date = "2026-10-26",
        note = "Пятёрочка", author = author, createdAt = 1, updatedAt = 1,
    )

    /** Телефон: локальные данные + синхронизация как в приложении. */
    private inner class Phone(var data: FamilyData) {
        fun add(e: Expense) { data = Merge.merge(data, FamilyData(expenses = listOf(e))) }
        fun sync() {
            val merged = SyncEngine(api).sync({ data }, "test")
            data = Merge.merge(data, merged)
        }
    }

    @Test
    fun `два телефона видят расходы друг друга`() {
        val mom = Phone(Merge.withDefaults(FamilyData()))
        val dad = Phone(Merge.withDefaults(FamilyData()))
        mom.add(exp("m1", 150000, "Мама"))
        dad.add(exp("d1", 49990, "Папа"))

        mom.sync()   // создаёт файл
        dad.sync()   // скачивает, объединяет, загружает
        mom.sync()   // получает расход папы

        assertEquals(mom.data, dad.data)
        assertEquals(setOf("m1", "d1"), mom.data.expenses.map { it.id }.toSet())
        assertEquals(2, commits)
        // Повторная синхронизация без изменений не создаёт пустых коммитов
        mom.sync(); dad.sync()
        assertEquals(2, commits)
        // Кириллица доходит без искажений
        assertTrue(fileContent!!.contains("Пятёрочка"))
    }

    @Test
    fun `одновременная запись не теряет данные`() {
        val mom = Phone(FamilyData())
        val dad = Phone(FamilyData())
        mom.add(exp("m1", 100, "Мама")); mom.sync()
        dad.sync()
        dad.add(exp("d2", 200, "Папа"))
        mom.add(exp("m2", 300, "Мама"))
        // Пока папа отправляет, мама успевает записать своё — первый PUT папы получит 409.
        beforeNextPut = { mom.sync() }
        dad.sync()
        mom.sync()
        assertEquals(setOf("m1", "m2", "d2"), mom.data.expenses.map { it.id }.toSet())
        assertEquals(mom.data, dad.data)
    }

    @Test
    fun `пустой репозиторий`() {
        val remote = api.fetch()
        assertNull(remote.data)
        assertNull(remote.sha)
    }

    @Test(expected = SyncException::class)
    fun `неверный токен даёт понятную ошибку`() {
        GitHubSync(config.copy(token = "wrong"), server.url("").toString().trimEnd('/')).fetch()
    }
}
