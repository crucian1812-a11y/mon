package ru.family.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.family.budget.model.Category
import ru.family.budget.model.DefaultCategories
import ru.family.budget.model.Expense
import ru.family.budget.model.FamilyData
import ru.family.budget.model.Merge

class MergeTest {
    private fun exp(id: String, amount: Long, updatedAt: Long, deleted: Boolean = false) = Expense(
        id = id, amount = amount, categoryId = "default-groceries", date = "2026-10-26",
        createdAt = 1, updatedAt = updatedAt, deleted = deleted,
    )

    @Test
    fun `расходы с двух телефонов объединяются`() {
        val phone1 = FamilyData(expenses = listOf(exp("a", 100, 1)))
        val phone2 = FamilyData(expenses = listOf(exp("b", 200, 2)))
        val merged = Merge.merge(phone1, phone2)
        assertEquals(setOf("a", "b"), merged.expenses.map { it.id }.toSet())
    }

    @Test
    fun `более новая правка побеждает`() {
        val old = FamilyData(expenses = listOf(exp("a", 100, 1)))
        val new = FamilyData(expenses = listOf(exp("a", 150, 5)))
        assertEquals(150, Merge.merge(old, new).expenses.single().amount)
        assertEquals(150, Merge.merge(new, old).expenses.single().amount)
    }

    @Test
    fun `удаление не воскресает после синхронизации`() {
        val local = FamilyData(expenses = listOf(exp("a", 100, 5, deleted = true)))
        val remote = FamilyData(expenses = listOf(exp("a", 100, 1)))
        assertTrue(Merge.merge(remote, local).expenses.single().deleted)
    }

    @Test
    fun `слияние коммутативно и идемпотентно`() {
        val a = FamilyData(
            categories = listOf(Category("c1", "Кофе", createdAt = 10, updatedAt = 10)),
            expenses = listOf(exp("a", 100, 3), exp("b", 1, 1)),
        )
        val b = FamilyData(expenses = listOf(exp("a", 100, 3, deleted = true), exp("c", 7, 2)))
        val ab = Merge.merge(a, b)
        assertEquals(ab, Merge.merge(b, a))
        assertEquals(ab, Merge.merge(ab, a))
        assertEquals(ab, Merge.merge(ab, b))
    }

    @Test
    fun `базовые категории не дублируются`() {
        val one = Merge.withDefaults(FamilyData())
        val two = Merge.withDefaults(FamilyData())
        val merged = Merge.merge(one, two)
        assertEquals(DefaultCategories.all.size, merged.categories.size)
    }

    @Test
    fun `переименованная базовая категория не откатывается`() {
        val renamed = DefaultCategories.all.first().copy(name = "Еда", updatedAt = 100)
        val data = Merge.withDefaults(FamilyData(categories = listOf(renamed)))
        assertEquals("Еда", data.categories.first { it.id == renamed.id }.name)
        assertFalse(data.categories.any { it.name == DefaultCategories.all.first().name })
    }
}
