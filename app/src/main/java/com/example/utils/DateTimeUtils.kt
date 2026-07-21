package com.example.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateTimeUtils {

    private const val DEFAULT_FORMAT = "yyyy-MM-dd HH:mm:ss"

    fun formatEpochToString(epochMillis: Long): String {
        val sdf = SimpleDateFormat(DEFAULT_FORMAT, Locale.getDefault())
        return sdf.format(Date(epochMillis))
    }
}
