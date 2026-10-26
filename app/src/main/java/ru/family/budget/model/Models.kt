package ru.family.budget.model

import kotlinx.serialization.Serializable

/**
 * Все данные семьи. Этот же объект хранится JSON-файлом в репозитории GitHub.
 *
 * Записи никогда не удаляются физически — у удалённых ставится [Expense.deleted] = true.
 * Благодаря этому два телефона могут независимо вносить изменения, а при синхронизации
 * они объединяются без потерь: для каждой записи побеждает версия с бóльшим updatedAt.
 */
@Serializable
data class FamilyData(
    val version: Int = 1,
    val categories: List<Category> = emptyList(),
    val expenses: List<Expense> = emptyList(),
)

@Serializable
data class Category(
    val id: String,
    val name: String,
    val emoji: String = "📦",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
)

@Serializable
data class Expense(
    val id: String,
    /** Сумма в копейках. */
    val amount: Long,
    val categoryId: String,
    /** Дата в формате yyyy-MM-dd. */
    val date: String,
    val note: String = "",
    /** Кто внёс расход (имя устройства из настроек). */
    val author: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val deleted: Boolean = false,
)

object DefaultCategories {
    // Постоянные id, чтобы оба телефона создавали одинаковые базовые категории без дублей.
    private val items = listOf(
        "groceries" to ("Продукты" to "🛒"),
        "cafe" to ("Кафе и рестораны" to "🍽️"),
        "transport" to ("Транспорт" to "🚌"),
        "car" to ("Автомобиль" to "🚗"),
        "utilities" to ("ЖКХ" to "🏠"),
        "communication" to ("Связь и интернет" to "📱"),
        "health" to ("Здоровье и аптека" to "💊"),
        "clothes" to ("Одежда и обувь" to "👕"),
        "kids" to ("Дети" to "🧸"),
        "household" to ("Дом и быт" to "🧺"),
        "entertainment" to ("Развлечения" to "🎬"),
        "gifts" to ("Подарки" to "🎁"),
        "beauty" to ("Красота и уход" to "💄"),
        "education" to ("Образование" to "📚"),
        "pets" to ("Питомцы" to "🐾"),
        "travel" to ("Путешествия" to "✈️"),
        "other" to ("Прочее" to "📦"),
    )

    val all: List<Category> = items.mapIndexed { index, (id, nameEmoji) ->
        Category(
            id = "default-$id",
            name = nameEmoji.first,
            emoji = nameEmoji.second,
            createdAt = index.toLong(),
            updatedAt = 0,
        )
    }
}

object Merge {
    /** Объединяет две версии данных. Операция коммутативна и идемпотентна. */
    fun merge(a: FamilyData, b: FamilyData): FamilyData = FamilyData(
        version = maxOf(a.version, b.version),
        categories = mergeById(a.categories, b.categories, Category::id, Category::updatedAt, Category::deleted)
            .sortedWith(compareBy<Category>({ it.createdAt }, { it.id })),
        expenses = mergeById(a.expenses, b.expenses, Expense::id, Expense::updatedAt, Expense::deleted)
            .sortedWith(compareBy<Expense>({ it.date }, { it.createdAt }, { it.id })),
    )

    fun withDefaults(data: FamilyData): FamilyData =
        merge(data, FamilyData(categories = DefaultCategories.all))

    private fun <T> mergeById(
        a: List<T>,
        b: List<T>,
        id: (T) -> String,
        updatedAt: (T) -> Long,
        deleted: (T) -> Boolean,
    ): List<T> {
        val result = LinkedHashMap<String, T>()
        for (item in a + b) {
            val key = id(item)
            val existing = result[key]
            result[key] = if (existing == null) item else newer(existing, item, updatedAt, deleted)
        }
        return result.values.toList()
    }

    private fun <T> newer(x: T, y: T, updatedAt: (T) -> Long, deleted: (T) -> Boolean): T {
        val ux = updatedAt(x)
        val uy = updatedAt(y)
        return when {
            ux != uy -> if (ux > uy) x else y
            deleted(x) != deleted(y) -> if (deleted(x)) x else y
            // Полная ничья: выбираем детерминированно, чтобы оба устройства пришли к одному результату.
            else -> if (x.toString() >= y.toString()) x else y
        }
    }
}
