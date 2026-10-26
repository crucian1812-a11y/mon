package ru.family.budget.importer

import java.time.LocalDate

/** Операция на экране проверки перед добавлением. */
data class ImportItem(
    val key: Int,
    val checked: Boolean,
    val amountText: String,
    val note: String,
    val categoryId: String,
    val date: LocalDate,
    val dateFound: Boolean,
    /** Такой расход (дата + сумма) уже есть в приложении. */
    val duplicate: Boolean,
    val transfer: Boolean,
)
