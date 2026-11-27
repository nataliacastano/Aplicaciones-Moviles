package com.example.gestorempresa.ui.advanced.forms

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DateTimeFieldsTest {
    @Test fun insertsDashesAndKeepsOnlyDigits() {
        assertEquals("2026", formatDateInput("2026"))
        assertEquals("2026-1", formatDateInput("20261"))
        assertEquals("2026-11-30", formatDateInput("20261130"))
        assertEquals("2026-11-30", formatDateInput("2026-11-30-99"))
        assertEquals("2026-11", formatDateInput("2026/11"))
    }

    @Test fun acceptsOnlyRealDates() {
        assertEquals(LocalDate.of(2028, 2, 29), parseDate("2028-02-29"))
        assertNull(parseDate("2026-02-29"))
        assertNull(parseDate("2026-13-01"))
        assertNull(parseDate("2026-1-1"))
    }

    @Test fun reportsDateErrors() {
        assertNull(dateError("", required = false))
        assertNotNull(dateError("", required = true))
        assertTrue(dateError("2026-11", required = false)!!.contains("incompleta"))
        assertTrue(dateError("2026-02-30", required = false)!!.contains("no existe"))
        assertNull(dateError("2026-11-30", required = true))
        assertNotNull(dateError("2026-11-01", required = true, min = LocalDate.of(2026, 11, 2)))
    }

    @Test fun formatsAndValidatesTwentyFourHourTimes() {
        assertEquals("08", formatTimeInput("08"))
        assertEquals("08:3", formatTimeInput("083"))
        assertEquals("08:30", formatTimeInput("0830"))
        assertNotNull(parseTime("23:59"))
        assertNull(parseTime("24:00"))
        assertNull(parseTime("8:30"))
        assertNull(parseTime("12:60"))
        assertNull(timeError("", required = false))
        assertNotNull(timeError("25:00", required = false))
    }

    @Test fun checksThatEndIsAfterStart() {
        assertNull(timeRangeError("08:00", "16:00"))
        assertNotNull(timeRangeError("16:00", "08:00"))
        assertNotNull(timeRangeError("08:00", "08:00"))
        assertNull(timeRangeError("08:00", ""))
    }
}
