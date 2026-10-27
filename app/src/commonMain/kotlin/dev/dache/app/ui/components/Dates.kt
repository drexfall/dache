package dev.dache.app.ui.components

import kotlin.time.Instant

/** Short localized day label, e.g. "Oct 27". Uses UTC to match the UTC day buckets in core. */
expect fun formatDay(instant: Instant): String

/** Short localized time of day in the device's zone, e.g. "14:00". */
expect fun formatHour(instant: Instant): String
