package ru.family.budget.util

import java.time.LocalDate
import java.time.YearMonth

object RuDates {
    private val monthsNominative = listOf(
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
    )
    private val monthsGenitive = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )
    private val weekdays = listOf(
        "понедельник", "вторник", "среда", "четверг", "пятница", "суббота", "воскресенье",
    )

    fun month(ym: YearMonth): String = "${monthsNominative[ym.monthValue - 1]} ${ym.year}"

    fun day(date: LocalDate, today: LocalDate = LocalDate.now()): String {
        val base = "${date.dayOfMonth} ${monthsGenitive[date.monthValue - 1]}"
        val withYear = if (date.year != today.year) "$base ${date.year}" else base
        return when (date) {
            today -> "Сегодня, $withYear"
            today.minusDays(1) -> "Вчера, $withYear"
            else -> "$withYear, ${weekdays[date.dayOfWeek.value - 1]}"
        }
    }

    fun short(date: LocalDate): String =
        "${date.dayOfMonth} ${monthsGenitive[date.monthValue - 1]} ${date.year}"
}
