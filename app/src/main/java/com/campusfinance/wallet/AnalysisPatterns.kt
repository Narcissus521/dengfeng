package com.campusfinance.wallet

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

enum class SpendingPattern(val label:String) {
    WEEKDAY("工作日"), WEEKEND("周末"), EARLY("月初 1–10 日"), MIDDLE("月中 11–20 日"), LATE("月末 21 日起");
    fun includes(date:LocalDate)=when(this) {
        WEEKDAY->date.dayOfWeek!=DayOfWeek.SATURDAY&&date.dayOfWeek!=DayOfWeek.SUNDAY
        WEEKEND->date.dayOfWeek==DayOfWeek.SATURDAY||date.dayOfWeek==DayOfWeek.SUNDAY
        EARLY->date.dayOfMonth<=10
        MIDDLE->date.dayOfMonth in 11..20
        LATE->date.dayOfMonth>=21
    }
}
data class SpendingDay(val date:LocalDate,val totals:ExpenseTotals)
data class RhythmPart(val pattern:SpendingPattern,val days:Int,val totals:ExpenseTotals) {
    val average:Long? get()=if(days==0)null else BigDecimal.valueOf(totals.net).divide(BigDecimal.valueOf(days.toLong()),0,RoundingMode.HALF_UP).longValueExact()
}
/** Include zero-spend days only inside the covered range; future/outside days are absent. */
fun ExpenseReport.calendar(month:YearMonth):List<SpendingDay> {
    val first=maxOf(month.atDay(1),range.start);val last=minOf(month.atEndOfMonth(),range.end)
    if(first>last)return emptyList()
    val grouped=rows.groupBy{it.entry.date}
    return generateSequence(first){it.plusDays(1)}.takeWhile{it<=last}.map{SpendingDay(it,expenseTotals(grouped[it.toString()].orEmpty()))}.toList()
}
fun ExpenseReport.rhythm(groupId:String?=null,leafId:String?=null):List<RhythmPart> {
    val dated=rows.filter{(groupId==null||it.groupId==groupId)&&(leafId==null||it.leafId==leafId)}.map{LocalDate.parse(it.entry.date) to it}
    val dates=generateSequence(range.start){it.plusDays(1)}.takeWhile{it<=range.end}.toList()
    return SpendingPattern.entries.map{p->RhythmPart(p,dates.count(p::includes),expenseTotals(dated.filter{p.includes(it.first)}.map{it.second}))}
}
/** A consumption can trigger several rules; its category total is never added repeatedly. */
fun ExpenseIndex.alertGroup(alert:SpendingAlert):String?=alert.filter.groupId ?: rows.firstOrNull {
    alert.filter.consumptionId!=null&&it.consumptionId==alert.filter.consumptionId || alert.entryId!=null&&it.entry.id==alert.entryId
}?.groupId
