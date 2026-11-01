package com.campusfinance.wallet

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** A window belongs to its start day, including when it ends after midnight. */
object ReminderPolicy {
    fun windowDay(start:LocalTime,end:LocalTime,instant:ZonedDateTime):LocalDate? {
        val now=instant.withZoneSameInstant(financeZone)
        val current=now.toLocalTime().withSecond(0).withNano(0)
        val within=if(start<=end)current>=start&&current<=end else current>=start||current<=end
        if(!within)return null
        return if(end<start&&current<=end)now.toLocalDate().minusDays(1) else now.toLocalDate()
    }
    fun nextStart(start:LocalTime,instant:ZonedDateTime):ZonedDateTime {
        val now=instant.withZoneSameInstant(financeZone)
        val next=now.toLocalDate().atTime(start).atZone(financeZone)
        return if(next<=now)next.plusDays(1) else next
    }
}
