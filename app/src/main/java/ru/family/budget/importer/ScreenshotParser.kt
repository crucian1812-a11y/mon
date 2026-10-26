package ru.family.budget.importer

import java.time.LocalDate
import kotlin.math.abs

/** Строка текста, найденная на скриншоте, с координатами в пикселях. */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val height get() = bottom - top
    val centerY get() = (top + bottom) / 2
}

/** Операция, найденная на скриншоте банка. */
data class FoundOperation(
    /** Сумма в копейках. */
    val amount: Long,
    /** Название магазина/получателя. */
    val merchant: String,
    /** Подписи рядом (категория банка, «Перевод по СБП» и т.п.). */
    val labels: List<String>,
    val date: LocalDate,
    /** Дата взята со скриншота, а не подставлена «сегодня». */
    val dateFound: Boolean,
    val isTransfer: Boolean,
)

/**
 * Разбирает текст со скриншота банковского приложения (Сбер, Т-Банк, ВТБ, Альфа и т.п.):
 * находит суммы, сопоставляет их с названиями магазинов по положению на экране и
 * определяет дату по заголовкам «Сегодня», «Вчера», «23 октября».
 */
object ScreenshotParser {

    private const val SIGNS = "+\\-−—–~"

    // Знак ₽ OCR часто читает как Р, P или даже 2 (последнее — только через пробел).
    private val amountRegex = Regex(
        "^([$SIGNS])?\\s*(\\d{1,3}(?:[ \\u00A0]\\d{3})+|\\d+)(?:[.,](\\d{1,2}))?" +
            "(?:\\s*(?:₽|[РPрpR]|руб\\.?|RUB)|\\s+2)?$"
    )
    private val currencyRegex = Regex("(?:₽|[РPрpR]|руб\\.?|RUB|\\s2)$")

    private val months = listOf("янв", "фев", "мар", "апр", "ма", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
    private val wordDateRegex = Regex("^(\\d{1,2})\\s+([а-яё]+)\\.?(?:\\s+(\\d{4}))?", RegexOption.IGNORE_CASE)
    private val numericDateRegex = Regex("^(\\d{1,2})\\.(\\d{1,2})(?:\\.(\\d{2}|\\d{4}))?(?!\\d)")

    private data class Amount(val line: OcrLine, val kopecks: Long, val sign: Char?, val hasCurrency: Boolean)

    fun parse(lines: List<OcrLine>, today: LocalDate = LocalDate.now()): List<FoundOperation> {
        val clean = lines.map { it.copy(text = it.text.trim()) }.filter { it.text.isNotEmpty() }

        val amounts = clean.mapNotNull { parseAmount(it) }
        val amountLines = amounts.map { it.line }.toSet()
        val dates = clean.filter { it !in amountLines }.mapNotNull { l -> parseDate(l.text, today)?.let { l to it } }
        val dateLines = dates.map { it.first }.toSet()
        val labels = clean.filter { l ->
            l !in dateLines && l !in amountLines && l.text.count { it.isLetter() } >= 2
        }

        // Если где-то есть минус — расходы помечены минусом, а суммы без знака (кэшбэк, баланс) не берём.
        // Если минусов нет (как в Сбере) — расходы без знака, а «+» это поступления.
        val hasMinus = amounts.any { it.sign != null && it.sign != '+' }
        val expenses = amounts.filter { a ->
            when {
                a.sign == '+' -> false
                hasMinus -> a.sign != null
                else -> a.hasCurrency
            }
        }
        if (expenses.isEmpty()) return emptyList()

        // Подписи слева от суммы в той же «строке» списка достаются ближайшей сумме.
        val assigned = HashMap<Amount, MutableList<OcrLine>>()
        for (label in labels) {
            val owner = expenses
                .filter { a -> label.right <= a.line.left + 10 && rowDistance(label, a.line) <= 1.3 * a.line.height }
                .minByOrNull { abs(it.line.centerY - label.centerY) }
            if (owner != null) assigned.getOrPut(owner) { mutableListOf() } += label
        }

        return expenses.sortedBy { it.line.top }.map { a ->
            var near = assigned[a].orEmpty().sortedBy { it.top }
            if (near.isEmpty()) {
                // Экран одной операции: крупная сумма, а название магазина — под ней.
                near = labels
                    .filter { it.top >= a.line.bottom - 5 && it.top - a.line.bottom < 4 * a.line.height }
                    .filter { it.right > a.line.left && it.left < a.line.right }
                    .sortedBy { it.top }
                    .take(2)
            }
            val dateAbove = dates.filter { it.first.bottom <= a.line.centerY }.maxByOrNull { it.first.top }
            val date = dateAbove ?: if (expenses.size == 1) dates.firstOrNull() else null
            val merchant = near.firstOrNull()?.text.orEmpty()
            val rest = near.drop(1).map { it.text }
            FoundOperation(
                amount = a.kopecks,
                merchant = merchant,
                labels = rest,
                date = date?.second ?: today,
                dateFound = date != null,
                isTransfer = (rest + merchant).any { it.contains("перевод", ignoreCase = true) },
            )
        }
    }

    private fun rowDistance(a: OcrLine, b: OcrLine): Int = when {
        a.bottom < b.top -> b.top - a.bottom
        b.bottom < a.top -> a.top - b.bottom
        else -> 0
    }

    private fun parseAmount(line: OcrLine): Amount? {
        val text = line.text
            .replace(Regex("\\s+"), " ")
            .replace(Regex("^([$SIGNS])\\s+"), "$1")
        val m = amountRegex.matchEntire(text) ?: return null
        val sign = m.groupValues[1].firstOrNull()
        val hasCurrency = currencyRegex.containsMatchIn(text)
        if (sign == null && !hasCurrency) return null
        val rub = m.groupValues[2].replace(" ", "").replace(" ", "").toLongOrNull() ?: return null
        val kop = m.groupValues[3].padEnd(2, '0').ifEmpty { "00" }.toLong()
        val total = rub * 100 + kop
        if (total <= 0 || rub > 10_000_000) return null
        return Amount(line, total, sign?.let { if (it == '+') '+' else '-' }, hasCurrency)
    }

    /** Дата в начале строки: «Сегодня», «Вчера», «23 октября», «21 октября 2026, 19:42», «23.10.2026». */
    fun parseDate(text: String, today: LocalDate): LocalDate? {
        val t = text.trim().lowercase()
        when {
            t.startsWith("сегодня") -> return today
            t.startsWith("вчера") -> return today.minusDays(1)
            t.startsWith("позавчера") -> return today.minusDays(2)
        }
        wordDateRegex.find(t)?.let { m ->
            val word = m.groupValues[2]
            val month = months.indexOfFirst { word.startsWith(it) } + 1
            if (month == 0) return null
            if (month == 5 && !(word.startsWith("мая") || word.startsWith("май"))) return null
            return makeDate(m.groupValues[1].toInt(), month, m.groupValues[3].toIntOrNull(), today)
        }
        numericDateRegex.find(t)?.let { m ->
            val year = m.groupValues[3].toIntOrNull()?.let { if (it < 100) 2000 + it else it }
            return makeDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), year, today)
        }
        return null
    }

    private fun makeDate(day: Int, month: Int, year: Int?, today: LocalDate): LocalDate? = try {
        val d = LocalDate.of(year ?: today.year, month, day)
        // «28 декабря» в январе — это прошлый год.
        if (year == null && d.isAfter(today)) d.minusYears(1) else d
    } catch (e: Exception) {
        null
    }
}
