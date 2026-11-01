package com.campusfinance.wallet

import java.time.LocalTime
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class ReminderPolicyTest {
    private fun time(s:String)=ZonedDateTime.parse(s).withZoneSameInstant(financeZone)
    @Test fun daytimeIncludesLastMinuteAndSkipsMissedWindow(){
        val start=LocalTime.of(20,0);val end=LocalTime.of(22,0)
        assertNull(ReminderPolicy.windowDay(start,end,time("2026-11-01T19:59:59+08:00")))
        assertEquals("2026-11-01",ReminderPolicy.windowDay(start,end,time("2026-11-01T20:00:00+08:00")).toString())
        assertNotNull(ReminderPolicy.windowDay(start,end,time("2026-11-01T22:00:59+08:00")))
        assertNull(ReminderPolicy.windowDay(start,end,time("2026-11-01T22:01:00+08:00")))
        assertEquals("2026-11-02T20:00+08:00[Asia/Shanghai]",ReminderPolicy.nextStart(start,time("2026-11-01T22:01:00+08:00")).toString())
    }
    @Test fun overnightWindowUsesOneStartDayAndBeijingTime(){
        val start=LocalTime.of(23,0);val end=LocalTime.of(1,0)
        assertEquals("2026-11-01",ReminderPolicy.windowDay(start,end,time("2026-11-02T00:30:00+08:00")).toString())
        assertEquals("2026-11-01",ReminderPolicy.windowDay(start,end,time("2026-11-01T16:30:00Z")).toString())
        assertNull(ReminderPolicy.windowDay(start,end,time("2026-11-02T02:00:00+08:00")))
    }
}
