package ru.family.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.family.budget.importer.CategoryGuesser
import ru.family.budget.importer.FoundOperation
import ru.family.budget.importer.OcrLine
import ru.family.budget.importer.ScreenshotParser
import ru.family.budget.model.Expense
import ru.family.budget.model.FamilyData
import ru.family.budget.model.Merge
import java.time.LocalDate

/**
 * Фикстуры — настоящий вывод Tesseract (rus+eng, режим sparse text) по макетам
 * экранов банковских приложений: строка «left top right bottom|текст».
 */
class ScreenshotParserTest {
    private val today = LocalDate.of(2026, 10, 26)
    private val data = Merge.withDefaults(FamilyData())

    private fun load(name: String): List<OcrLine> =
        javaClass.getResource("/ocr/$name.lines")!!.readText().lines().filter { it.isNotBlank() }.map { l ->
            val (box, text) = l.split("|", limit = 2)
            val (a, b, c, d) = box.split(" ").map { it.toInt() }
            OcrLine(text, a, b, c, d)
        }

    private fun parse(name: String) = ScreenshotParser.parse(load(name), today)
    private fun cat(op: FoundOperation) = CategoryGuesser(data).guess(op)

    @Test
    fun `список операций с минусами`() {
        val ops = parse("list")
        assertEquals(
            listOf(123456L, 38900, 75640, 112000, 65000, 250000, 300000),
            ops.map { it.amount },
        )
        assertEquals(
            listOf("Пятёрочка", "Яндекс Со", "АПТЕКА РИГЛА", "Шоколадница", "МТС", "LUKOIL AZS 123", "Мария К."),
            ops.map { it.merchant },
        )
        assertEquals(
            listOf(today, today, today.minusDays(1), today.minusDays(1),
                LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 23), LocalDate.of(2026, 10, 23)),
            ops.map { it.date },
        )
        assertEquals(
            listOf("default-groceries", "default-transport", "default-health", "default-cafe",
                "default-communication", "default-car", "default-other"),
            ops.map(::cat),
        )
        assertTrue(ops.last().isTransfer)
        assertFalse(ops.first().isTransfer)
    }

    @Test
    fun `экран одной операции`() {
        val op = parse("detail").single() // кэшбэк 48 ₽ не считается расходом
        assertEquals(487090L, op.amount)
        assertEquals("ВКУСВИЛЛ", op.merchant)
        assertEquals(LocalDate.of(2026, 10, 21), op.date)
        assertTrue(op.dateFound)
        assertEquals("default-groceries", cat(op))
    }

    @Test
    fun `список в стиле Сбера без минусов, светлая и тёмная тема`() {
        for (name in listOf("sber", "dark")) {
            val ops = parse(name)
            // Зарплата «+85 000 ₽» — поступление, пропускается
            assertEquals(name, listOf(84230L, 59900, 341520, 219900, 156300), ops.map { it.amount })
            assertEquals(name, listOf("Магнит", "Netflix", "Лента", "Детский мир", "Ozon"), ops.map { it.merchant })
            assertEquals(name, listOf(today, today, today.minusDays(1), today.minusDays(1), today.minusDays(1)), ops.map { it.date })
            assertEquals(
                name,
                listOf("default-groceries", "default-entertainment", "default-groceries", "default-kids", "default-other"),
                ops.map(::cat),
            )
        }
    }

    @Test
    fun `категория запоминается по магазину`() {
        val withHistory = Merge.merge(data, FamilyData(expenses = listOf(
            Expense("1", 100, "default-household", "2026-10-01", note = "OZON", updatedAt = 1),
        )))
        val ozon = parse("sber").last()
        assertEquals("default-household", CategoryGuesser(withHistory).guess(ozon))
    }

    @Test
    fun `даты`() {
        val p = { s: String -> ScreenshotParser.parseDate(s, today) }
        assertEquals(today, p("Сегодня"))
        assertEquals(today.minusDays(1), p("Вчера, 25 октября"))
        assertEquals(LocalDate.of(2026, 5, 9), p("9 мая"))
        assertEquals(LocalDate.of(2026, 3, 8), p("8 марта, воскресенье"))
        assertEquals(LocalDate.of(2025, 12, 31), p("31 декабря")) // будущая дата → прошлый год
        assertEquals(LocalDate.of(2026, 10, 21), p("21.10.2026 19:42"))
        assertEquals(LocalDate.of(2026, 10, 21), p("21.10.26"))
        assertEquals(null, p("Магнит"))
        assertEquals(null, p("48 Р"))
    }

    @Test
    fun `варианты записи сумм`() {
        fun amount(s: String) = ScreenshotParser.parse(listOf(OcrLine(s, 500, 0, 900, 40)), today).singleOrNull()?.amount
        assertEquals(156300L, amount("1563 2"))       // ₽ прочитан как «2»
        assertEquals(123400L, amount("1 234 ₽"))
        assertEquals(99990L, amount("- 999,90 P"))
        assertEquals(50000L, amount("500 руб."))
        assertEquals(null, amount("+500 ₽"))           // поступление
        assertEquals(null, amount("2026"))              // просто число
        assertEquals(null, amount("87%"))
    }
}
