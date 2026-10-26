package ru.family.budget.importer

import ru.family.budget.model.FamilyData

/** Подбирает категорию для операции со скриншота. */
class CategoryGuesser(private val data: FamilyData) {

    private val active = data.categories.filter { !it.deleted }
    private val activeIds = active.map { it.id }.toSet()

    /** Магазин → категория, которую семья уже выбирала для него раньше. */
    private val history: Map<String, String> = data.expenses
        .filter { !it.deleted && it.note.isNotBlank() && it.categoryId in activeIds }
        .sortedBy { it.updatedAt }
        .associate { normalize(it.note) to it.categoryId }

    fun guess(op: FoundOperation): String {
        val merchant = normalize(op.merchant)
        history[merchant]?.let { return it }

        val labels = op.labels.map(::normalize)
        // Своя категория с тем же названием, что у банка («Маркетплейсы», «Супермаркеты»...)
        active.firstOrNull { c -> labels.any { it == normalize(c.name) } }?.let { return it.id }

        if (op.isTransfer) return fallback()
        // Сначала категория, которую показал банк, потом название магазина.
        for (text in labels + merchant) {
            for ((id, words) in keywords) {
                if ("default-$id" in activeIds && words.any { text.contains(it) }) return "default-$id"
            }
        }
        return fallback()
    }

    private fun fallback(): String =
        if ("default-other" in activeIds) "default-other" else active.lastOrNull()?.id ?: "default-other"

    companion object {
        fun normalize(s: String) = s.lowercase().replace('ё', 'е').replace(Regex("\\s+"), " ").trim()

        // Порядок важен: «Детский мир» — дети, а не одежда; АЗС — автомобиль, а не ЖКХ.
        private val keywords: List<Pair<String, List<String>>> = listOf(
            "kids" to listOf("детск", "детей", "дети", "игрушк", "кораблик"),
            "groceries" to listOf(
                "супермаркет", "продукт", "магнит", "пятерочк", "перекрест", "вкусвилл", "лента", "ашан",
                "auchan", "дикси", "азбука вкуса", "окей", "о'кей", "глобус", "globus", "самокат",
                "лавка", "spar", "спар", "верный", "магнолия", "монетка", "мария-ра", "пятерочка", "pyaterochka",
                "magnit", "perekrestok", "vkusvill", "lenta", "dixy", "чижик", "светофор", "бахетле",
            ),
            "cafe" to listOf(
                "ресторан", "кафе", "фастфуд", "фаст-фуд", "кофе", "coffee", "кофейн", "шоколадниц", "бургер",
                "burger", "kfc", "ростикс", "rostic", "вкусно и точка", "теремок", "додо", "dodo", "пицц",
                "pizza", "суши", "sushi", "роллы", "столовая", "макдон", "starbucks", "cofix", "бар ", "доставка еды",
            ),
            "transport" to listOf(
                "такси", "taxi", "яндекс go", "yandex go", "yandex.go", "метро", "metro moscow", "транспорт",
                "автобус", "ржд", "электрич", "аэроэкспресс", "тройка", "каршеринг", "делимобиль", "ситидрайв",
                "belkacar", "whoosh", "самокаты", "uber", "ситимобил", "проезд",
            ),
            "car" to listOf(
                "азс", "azs", "топливо", "бензин", "lukoil", "лукойл", "роснефть", "rosneft", "газпромнефть",
                "gazprom neft", "gazpromneft", "shell", "татнефть", "tatneft", "автосервис", "шиномонтаж",
                "парковк", "parking", "автомойк", "автозапчаст", "автомобил", "осаго", "штраф гибдд",
            ),
            "utilities" to listOf(
                "жкх", "жку", "коммунал", "жилищ", "энергосбыт", "электроэнерг", "водоканал", "мосэнерго",
                "управляющая компания", "капремонт", "квартплата",
            ),
            "communication" to listOf(
                "мобильная связь", "связь", "мтс", "mts", "билайн", "beeline", "мегафон", "megafon", "теле2",
                "tele2", "yota", "йота", "ростелеком", "rostelecom", "интернет", "дом.ру",
            ),
            "health" to listOf(
                "аптек", "apteka", "ригла", "горздрав", "здравсити", "медицин", "клиник", "стоматолог",
                "здоровье", "инвитро", "invitro", "гемотест", "больниц", "поликлиник", "лаборатор", "оптика",
            ),
            "clothes" to listOf(
                "одежд", "обув", "zara", "gloria jeans", "глория", "спортмастер", "lamoda", "ламода", "befree",
                "ostin", "остин", "kari", "uniqlo", "lime", "love republic", "zolla", "текстиль",
            ),
            "household" to listOf(
                "дом и сад", "для дома", "товары для дома", "хозтовар", "леруа", "leroy", "икеа", "ikea", "оби",
                "fix price", "фикс прайс", "ремонт", "стройматериал", "мебел", "hoff", "хофф", "бытов",
                "м.видео", "эльдорадо", "dns", "электроник",
            ),
            "entertainment" to listOf(
                "развлечен", "кино", "netflix", "кинопоиск", "okko", "театр", "концерт", "steam",
                "playstation", "яндекс плюс", "yandex plus", "spotify", "музык", "подписк", "боулинг", "парк",
                "музей", "билеты",
            ),
            "gifts" to listOf("подар", "цветы", "цветов", "flowers", "флорист"),
            "beauty" to listOf(
                "красот", "салон", "парикмахер", "косметик", "золотое яблоко", "летуаль", "л'этуаль", "letu",
                "рив гош", "барбершоп", "barbershop", "маникюр",
            ),
            "education" to listOf(
                "образован", "курс", "школ", "учеб", "книг", "литрес", "litres", "читай-город", "читай город",
                "skyeng", "университет", "репетитор",
            ),
            "pets" to listOf("животн", "зоо", "zoo", "ветеринар", "ветклиник", "четыре лапы", "бетховен", "корм"),
            "travel" to listOf(
                "путешеств", "авиа", "aeroflot", "аэрофлот", "победа", "pobeda", "s7", "отель", "hotel",
                "гостиниц", "booking", "туту", "tutu", "турагент", "туроператор", "островок", "ostrovok",
            ),
        )
    }
}
