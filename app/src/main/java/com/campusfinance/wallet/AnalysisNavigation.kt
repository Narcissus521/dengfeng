package com.campusfinance.wallet

import java.time.LocalDate

data class AnalysisDestination(val page:String,val range:ReportRange,val group:String="",val leaf:String="",val necessity:String="") {
    fun encode()=listOf(page,range.start,range.end,group,leaf,necessity).joinToString("~")
    companion object {
        fun decode(kind:String,month:String):AnalysisDestination {
            if('~' in kind){val p=kind.split('~');return AnalysisDestination(p[0],ReportRange(LocalDate.parse(p[1]),LocalDate.parse(p[2])),p.getOrElse(3){""},p.getOrElse(4){""},p.getOrElse(5){""})}
            return AnalysisDestination(if(kind.endsWith(":categories"))"category" else "overview",presetReportRange(month,kind.substringBefore(':')))
        }
    }
}
fun analysisEditor(page:String,range:ReportRange,group:String="",leaf:String="",necessity:String="")=Editor("analysisHub",id=range.end.toString().take(7),kind=AnalysisDestination(page,range,group,leaf,necessity).encode())
