package com.campusfinance.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** Chart and paged ranking are one layout item, so AutoPages cannot separate them. */
@Composable fun CategoryDistribution(report:ExpenseReport,group:ExpenseGroup?,budgets:Map<String,BudgetComparison>,open:(Editor)->Unit) {
    val range=report.range
    val total=if(group==null)report.total else report.groups.getValue(group.id)
    val inner=report.necessities(group?.id)
    val items=if(group==null)ExpenseTaxonomy.groups.filter { g ->
        report.groups.getValue(g.id).let{it.count>0||it.refundCount>0}||budgets.getValue(g.id).planned!=null
    }.map{DistributionItem(it.id,it.name,report.groups.getValue(it.id))}
    else if(group.children.isEmpty())listOf(DistributionItem(group.id,group.name,total))
    else group.children.map{DistributionItem(it.id,it.name,report.leaves.getValue(it.id))}
    val ranked=items.sortedByDescending{it.value.net}
    val density=LocalDensity.current
    val windowHeight=with(density){LocalWindowInfo.current.containerSize.height.toDp()}
    val compact=windowHeight<740.dp||density.fontScale>1.2f
    val perPage=if(compact||group?.children?.isNotEmpty()==true)2 else 3
    var page by rememberSaveable(group?.id){mutableIntStateOf(0)}
    val pages=((ranked.size+perPage-1)/perPage).coerceAtLeast(1)
    val selected=page.coerceIn(0,pages-1)
    fun categoryClick(id:String){
        if(group==null)open(analysisEditor("category",range,id))
        else open(Editor("analysisRecords",kind=ReportFilter(range,group.id,if(group.children.isEmpty())null else id).encode()))
    }
    fun necessityClick(id:String){open(Editor("analysisRecords",kind=ReportFilter(range,group?.id,necessity=id).encode()))}
    TeaCard(modifier=Modifier.testTag("category-distribution"),padding=12.dp,spacing=5.dp) {
        AmountLine(if(group==null)"净消费总额" else "本类净消费",total.net)
        Text("外圈 · ${if(group?.children?.isNotEmpty()==true)"二级分类" else "消费种类"}    内圈 · 消费必要性",fontSize=11.sp,color=Muted)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1.1f)) {
                ReportDonut(items.map{it.id to it.value.chart(report.grossBasis)},inner.map{it.key to it.value.chart(report.grossBasis)},
                    "双层圆环图：外圈消费种类，内圈必要消费、非必要消费、未判断",::categoryClick,::necessityClick,legend=false,height=if(compact)138.dp else 158.dp)
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Necessity.entries.forEach{n->
                    val value=inner.getValue(n.name)
                    TextButton(onClick={necessityClick(n.name)},modifier=Modifier.fillMaxWidth().testTag("distribution-necessity-${n.name}"),contentPadding=PaddingValues(2.dp)) {
                        Box(Modifier.size(7.dp).background(expenseChartColor(n.name)));Spacer(Modifier.width(5.dp))
                        Column(Modifier.weight(1f)) {
                            Text(n.label,fontSize=11.sp,color=Ink)
                            Text("¥${Money.format(value.net)} · ${report.share(value,total).asShare()}",fontSize=10.sp,color=Muted)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("分类支出排名",Modifier.weight(1f),fontSize=14.sp,color=Ink)
            if(pages>1) {
                TextButton(onClick={page=selected-1},enabled=selected>0,modifier=Modifier.size(34.dp).testTag("distribution-previous"),contentPadding=PaddingValues(0.dp)){Text("‹")}
                Text("${selected+1}/$pages",fontSize=11.sp,color=Muted)
                TextButton(onClick={page=selected+1},enabled=selected<pages-1,modifier=Modifier.size(34.dp).testTag("distribution-next"),contentPadding=PaddingValues(0.dp)){Text("›")}
            }
        }
        if(ranked.isEmpty())Text("所选时段还没有消费记录。",fontSize=12.sp,color=Muted)
        ranked.drop(selected*perPage).take(perPage).forEachIndexed{i,item->
            Column {
                TextButton(onClick={categoryClick(item.id)},modifier=Modifier.fillMaxWidth().testTag(if(group==null)"analysis-group-${item.id}" else "analysis-leaf-${item.id}"),contentPadding=PaddingValues(horizontal=2.dp,vertical=5.dp)) {
                    Box(Modifier.size(8.dp).background(expenseChartColor(item.id)));Spacer(Modifier.width(7.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${selected*perPage+i+1}. ${item.name}",fontSize=13.sp,color=Ink,maxLines=2)
                        Text("${report.share(item.value,total).asShare()} · ${item.value.count} 笔消费 / ${item.value.refundCount} 笔退款",fontSize=10.sp,color=Muted)
                        if(group==null)budgets[item.id]?.let{cap->cap.planned?.let{Text("预算 ¥${Money.format(it)} · 余 ¥${Money.format(cap.remaining!!)}",fontSize=10.sp,color=Muted,maxLines=1,overflow=TextOverflow.Ellipsis)}}
                    }
                    Spacer(Modifier.width(4.dp));Text("¥${Money.format(item.value.net)} ›",fontSize=13.sp,color=Ink)
                }
                if(group?.children?.isNotEmpty()==true)TextButton(onClick={open(analysisEditor("trend",range,group.id,item.id))},modifier=Modifier.align(Alignment.End).height(24.dp),contentPadding=PaddingValues(0.dp)){Text("${item.name}趋势 ›",fontSize=11.sp)}
            }
        }
        Text(if(report.grossBasis)"存在净退款，占比统一按消费原额；金额显示净消费。" else "占比按退款后的净消费计算。点按圆环或分类查看明细。",fontSize=10.sp,color=Muted)
    }
}
private data class DistributionItem(val id:String,val name:String,val value:ExpenseTotals)
private fun Double?.asShare()=this?.let{String.format(Locale.ROOT,"%.1f%%",it)} ?: "—"
