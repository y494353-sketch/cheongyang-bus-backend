package com.example.cheongyangbus.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** App clock and date formatting use Korea Standard Time regardless of the device timezone. */
object SeoulTime {
    private val timeZone: TimeZone
        get() = TimeZone.getTimeZone("Asia/Seoul")

    fun calendar(atMillis: Long = System.currentTimeMillis()): Calendar =
        Calendar.getInstance(timeZone).apply { timeInMillis = atMillis }

    fun formatter(pattern: String, locale: Locale = Locale.KOREA): SimpleDateFormat =
        SimpleDateFormat(pattern, locale).apply { timeZone = this@SeoulTime.timeZone }
}
