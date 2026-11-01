package com.campusfinance.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable fun ConsumptionTypePicker(value:String,bucket:String,onChange:(String)->Unit,title:String="消费性质",allowUndecided:Boolean=true) {
    val pending=consumptionPending(value,bucket)
    Column(Modifier.testTag("consumption-type-picker"),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Caption(title)
        BucketChoiceRow(if(pending)"" else consumptionBucket(value),allowUndecided){onChange(consumptionNecessity(it))}
        if(pending)ConsumptionLegacyNotice(value,bucket)
        else TeaSmall(if(allowUndecided)"只选一次，同时用于消费分析和预算。未判断只计入总预算，不占用两个分项或未分配额度。" else "默认必要消费，可切换为非必要消费；同时用于消费分析和预算。")
    }
}
@Composable fun ConsumptionLegacyNotice(value:String,bucket:String) {
    Notice("历史记录待确认：原必要性为“${Necessity.valueOf(value).label}”，原预算归属为“${Bucket.valueOf(bucket).label}”。请选择消费性质后合并；未确认前保留原统计，不根据分类或金额判断。")
}
@Composable fun ConsumptionReviewLink(s:Ledger,month:String,open:(Editor)->Unit) {
    val count=s.pendingConsumption(month).size
    if(count>0)TeaLink("历史消费待确认 · $count 笔","原有预算统计已保留，点入逐条确认"){open(Editor("consumptionReview",kind=month))}
}
@Composable fun ConsumptionReviewForm(s:Ledger,month:String,close:()->Unit,open:(Editor)->Unit) {
    var page by rememberSaveable{mutableIntStateOf(0)}
    val records=s.pendingConsumption(month)
    val last=((records.size-1).coerceAtLeast(0)/15);val index=page.coerceAtMost(last)
    Form("历史消费待确认",close=close,confirm=null) {
        TeaSmall("$month · ${records.size} 笔。包括影响本月的原消费及跨月退款；同一原消费只列一次。")
        TeaSmall("金额、账户和总消费保持不变。确认消费性质后，原消费和关联退款的分项预算会重新计算，并保留修改历史。")
        if(records.isEmpty())TeaSmall("本月没有需要确认的历史记录。")
        records.drop(index*15).take(15).forEach{e->TeaCard(modifier=Modifier.testTag("consumption-review-${e.id}"),onClick={open(Editor("expenseMetadata",e.id))}){
            EntryRow(e,s){open(Editor("expenseMetadata",e.id))}
            TeaSmall("原必要性：${Necessity.valueOf(e.necessity).label} · 原预算：${Bucket.valueOf(e.bucket).label}")
            if(e.note.isNotBlank())TeaSmall(e.note)
            Text("选择消费性质 ›",color=Forest)
        }}
        if(records.size>15)TeaTabs(listOf("上一组","${index+1} / ${last+1}","下一组"),1){if(it==0)page=(index-1).coerceAtLeast(0) else if(it==2)page=(index+1).coerceAtMost(last)}
    }
}
