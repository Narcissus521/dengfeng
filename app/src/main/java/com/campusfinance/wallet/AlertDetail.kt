package com.campusfinance.wallet

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.YearMonth

fun alertDetailEditor(a:SpendingAlert)=Editor("analysisAlert",kind=JSONObject().put("type",a.type).put("filter",a.filter.encode()).put("entry",a.entryId ?: "").put("title",a.title).toString())
@Composable fun AlertDetailForm(s:Ledger,encoded:String,close:()->Unit,open:(Editor)->Unit) {
    val identity=remember(encoded){JSONObject(encoded)}
    val original=remember(encoded){ReportFilter.decode(identity.getString("filter"))}
    val prefs=LocalContext.current.getSharedPreferences("wallet_settings",0)
    val suffix=if(s.profile.demo)"demo" else "personal"
    val rules=AnomalyRules(prefs.getLong("analysis_large_$suffix",50000).coerceIn(1,MAX_MONEY),prefs.getLong("analysis_gap_$suffix",10000).coerceIn(1,MAX_MONEY))
    data class Detail(val alert:SpendingAlert?)
    val data by produceState<Detail?>(null,s.profile.revision,encoded,rules){value=withContext(Dispatchers.Default){
        val alerts=ExpenseIndex(s).alerts(YearMonth.from(original.range.end),rules).alerts
        Detail(alerts.find{a->a.type==identity.getString("type")&&if(identity.getString("entry").isNotEmpty())a.entryId==identity.getString("entry") else a.filter.groupId==original.groupId&&a.filter.consumptionId==original.consumptionId})
    }}
    Form("异常详情",close=close,confirm=null) {
        Text(identity.getString("title"),fontSize=21.sp,color=Amber)
        TeaSmall("${original.range.start} — ${original.range.end}")
        if(data==null)TeaSmall("正在核对最新交易…") else {
            val alert=data?.alert
            if(alert==null)TeaSmall("交易或规则已变化，此项已不再满足提醒条件。") else TeaCard{Text("判断原因",fontSize=17.sp);TeaSmall(alert.explanation)}
            TeaSmall("这是需要关注的变化，不代表消费不合理，也不会改变必要性或财务记录。")
            val filter=alert?.filter ?: original
            TeaLink("查看构成此项的交易"){open(Editor("analysisRecords",kind=filter.encode()))}
            (alert?.entryId ?: identity.getString("entry").ifEmpty{null})?.takeIf{id->s.entries.any{it.id==id}}?.let{id->TeaLink("查看原消费详情"){open(Editor("detail",id))}}
            filter.groupId?.let{g->TeaLink("查看该分类分析"){open(analysisEditor("category",filter.range,g))}}
        }
    }
}
