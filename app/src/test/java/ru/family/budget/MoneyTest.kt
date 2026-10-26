package ru.family.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.family.budget.util.Money

class MoneyTest {
    @Test
    fun parse() {
        assertEquals(125000L, Money.parse("1250"))
        assertEquals(9990L, Money.parse("99,90"))
        assertEquals(9990L, Money.parse("99.9"))
        assertEquals(123456L, Money.parse("1 234,56"))
        assertEquals(47050L, Money.parse("350+120,50"))
        assertEquals(15000L, Money.parse("150,"))
        assertNull(Money.parse(""))
        assertNull(Money.parse("0"))
        assertNull(Money.parse("1,234"))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("150+"))
    }

    @Test
    fun format() {
        assertEquals("0 ₽", Money.format(0))
        assertEquals("1 234,56 ₽", Money.format(123456))
        assertEquals("1 000 ₽", Money.format(100000))
        assertEquals("5,05 ₽", Money.format(505))
        assertEquals("1234,5", Money.toInput(123450))
        assertEquals("1000", Money.toInput(100000))
    }
}
