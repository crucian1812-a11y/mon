package ru.family.budget.util

import java.math.BigDecimal
import java.math.RoundingMode

object Money {
    private const val NBSP = ' '

    /** 123456 -> "1 234,56 ₽", 100000 -> "1 000 ₽". */
    fun format(kopecks: Long, withSign: Boolean = true): String {
        val negative = kopecks < 0
        val abs = kotlin.math.abs(kopecks)
        val rub = abs / 100
        val kop = abs % 100
        val grouped = rub.toString().reversed().chunked(3).joinToString(NBSP.toString()).reversed()
        val body = if (kop == 0L) grouped else "$grouped,${kop.toString().padStart(2, '0')}"
        val sign = if (negative) "−" else ""
        return if (withSign) "$sign$body$NBSP₽" else "$sign$body"
    }

    /** Значение для поля ввода: 123450 -> "1234,5". */
    fun toInput(kopecks: Long): String =
        BigDecimal.valueOf(kopecks, 2).stripTrailingZeros().toPlainString().replace('.', ',')

    /**
     * Разбирает ввод пользователя в копейки. Понимает пробелы, запятую и точку,
     * а также сложение: "150+89,90" -> 23990. Возвращает null, если ввод некорректен или ≤ 0.
     */
    fun parse(input: String): Long? {
        val cleaned = input.replace(" ", "").replace(NBSP.toString(), "").replace(',', '.')
        if (cleaned.isEmpty()) return null
        var total = BigDecimal.ZERO
        for (part in cleaned.split('+')) {
            if (!part.matches(Regex("""\d+(\.\d{0,2})?|\.\d{1,2}"""))) return null
            total += BigDecimal(part)
        }
        if (total.signum() <= 0) return null
        if (total > BigDecimal("100000000")) return null
        return total.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
    }
}
