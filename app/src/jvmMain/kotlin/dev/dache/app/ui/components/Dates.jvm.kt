package dev.dache.app.ui.components

import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.time.Instant
import java.time.Instant as JavaInstant

private val dayFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()).withZone(ZoneOffset.UTC)
private val hourFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())

actual fun formatDay(instant: Instant): String = dayFormatter.format(instant.toJava())

actual fun formatHour(instant: Instant): String = hourFormatter.format(instant.toJava())

private fun Instant.toJava(): JavaInstant = JavaInstant.ofEpochSecond(epochSeconds, nanosecondsOfSecond.toLong())
