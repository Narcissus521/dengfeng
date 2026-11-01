package com.campusfinance.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/** Keep every colour explanation beside its ring, in the same pagination item. */
@Composable fun NecessityDistribution(report:ExpenseReport,groupId:String?,leafId:String?,open:(Editor)->Unit) {
    val values=report.necessities(groupId,leafId)
    val total=report.slice(groupId,leafId)
    val group=ExpenseTaxonomy.groups.find{it.id==groupId}
    val categories=when {
        leafId!=null->listOf(DistributionItem(leafId,ExpenseTaxonomy.analysisLeaves.firstOrNull{it.id==leafId}?.name ?: group?.name.orEmpty(),total))
        group==null->ExpenseTaxonomy.groups.map{DistributionItem(it.id,it.name,report.groups.getValue(it.id))}
        group.children.isEmpty()->listOf(DistributionItem(group.id,group.name,total))
        else->group.children.map{DistributionItem(it.id,it.name,report.leaves.getValue(it.id))}
    }.filter{it.value.count>0||it.value.refundCount>0}.sortedByDescending{it.value.net}
    var legend by rememberSaveable(groupId,leafId){mutableIntStateOf(0)}
    var page by rememberSaveable(groupId,leafId){mutableIntStateOf(0)}
    val pages=((categories.size+2)/3).coerceAtLeast(1)
    val selected=page.coerceIn(0,pages-1)
    fun categoryClick(id:String){
        if(group==null)open(analysisEditor("category",report.range,id))
        else open(Editor("analysisRecords",kind=ReportFilter(report.range,group.id,if(group.children.isEmpty())null else id).encode()))
    }
    val density=LocalDensity.current
    val compact=with(density){LocalWindowInfo.current.containerSize.height.toDp()}<820.dp||density.fontScale>1.2f
    TeaCard(modifier=Modifier.testTag("necessity-distribution"),padding=12.dp,spacing=6.dp) {
        AmountLine("净消费总额",total.net)
        Text("外圈 · ${if(group?.children?.isNotEmpty()==true&&leafId==null)"二级分类" else "消费种类"}    内圈 · 消费必要性",fontSize=11.sp,color=Muted)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f).testTag("necessity-chart")) {
                ReportDonut(categories.map{it.id to it.value.chart(report.grossBasis)},values.map{it.key to it.value.chart(report.grossBasis)},"双层圆环图：外圈消费种类，内圈必要消费、非必要消费、未判断",::categoryClick,
                    {n->open(Editor("analysisRecords",kind=ReportFilter(report.range,groupId,leafId,necessity=n).encode()))},legend=false,height=if(compact)150.dp else 174.dp)
            }
            Column(Modifier.weight(1.1f).testTag("necessity-legend"),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                TeaTabs(listOf("内圈","外圈"),legend){legend=it}
                if(legend==0)Necessity.entries.forEach{n->val value=values.getValue(n.name)
                    TextButton(onClick={open(analysisEditor("necessityDetail",report.range,groupId.orEmpty(),leafId.orEmpty(),n.name))},modifier=Modifier.fillMaxWidth().testTag("necessity-type-${n.name}"),contentPadding=PaddingValues(horizontal=2.dp,vertical=5.dp)) {
                        Box(Modifier.size(8.dp).background(expenseChartColor(n.name)));Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(n.label+" ›",fontSize=12.sp,color=Ink)
                            Text("¥${Money.format(value.net)}",fontSize=12.sp,color=Ink)
                            Text("${report.share(value,total).asShare()} · ${value.count} 笔",fontSize=10.sp,color=Muted)
                        }
                    }
                }
                else {
                    if(categories.isEmpty())TeaSmall("暂无分类记录")
                    categories.drop(selected*3).take(3).forEach{item->
                        TextButton(onClick={categoryClick(item.id)},modifier=Modifier.fillMaxWidth().testTag("necessity-category-${item.id}"),contentPadding=PaddingValues(horizontal=2.dp,vertical=5.dp)) {
                            Box(Modifier.size(8.dp).background(expenseChartColor(item.id)));Spacer(Modifier.width(6.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name+" ›",fontSize=12.sp,color=Ink)
                                Text("¥${Money.format(item.value.net)} · ${report.share(item.value,total).asShare()}",fontSize=10.sp,color=Muted)
                            }
                        }
                    }
                    if(pages>1)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center) {
                        TextButton(onClick={page=selected-1},enabled=selected>0,modifier=Modifier.size(30.dp).testTag("necessity-legend-previous"),contentPadding=PaddingValues(0.dp)){Text("‹")}
                        Text("${selected+1}/$pages",fontSize=10.sp,color=Muted)
                        TextButton(onClick={page=selected+1},enabled=selected<pages-1,modifier=Modifier.size(30.dp).testTag("necessity-legend-next"),contentPadding=PaddingValues(0.dp)){Text("›")}
                    }
                }
            }
        }
        Text(if(report.grossBasis)"存在净退款，占比按消费原额；金额显示净消费。" else "占比按退款后的净消费计算。",fontSize=10.sp,color=Muted)
    }
}

@Composable fun BudgetOverviewCompact(index:ExpenseIndex,report:ExpenseReport,total:BudgetComparison,caps:Map<String,BudgetComparison>,open:(Editor)->Unit) {
    val range=report.range
    val density=LocalDensity.current
    val compact=with(density){LocalWindowInfo.current.containerSize.height.toDp()}<820.dp||density.fontScale>1.2f
    TeaCard(modifier=Modifier.testTag("budget-total-summary"),tint=LocalWalletColors.current.hero,padding=14.dp,spacing=5.dp,onClick={
        if(total.planned==null)open(Editor("budgetOverview",kind=java.time.YearMonth.from(range.end).toString())) else open(analysisEditor("budgetDetail",range))
    }) {
        Text("总预算",fontSize=13.sp,color=Muted)
        if(total.planned==null)Text("未设置",fontSize=28.sp,color=Ink) else GridMoney(total.planned,Modifier.fillMaxWidth(),28)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)){TeaSmall("实际净消费");GridMoney(report.total.net,Modifier.fillMaxWidth(),18)}
            Column(Modifier.weight(1f)){TeaSmall(if((total.remaining ?: 0)<0)"超出预算" else "剩余预算");val remaining=total.remaining;if(remaining==null)Text("—",fontSize=18.sp,color=Ink) else GridMoney(kotlin.math.abs(remaining),Modifier.fillMaxWidth(),18,color=if(remaining<0)Red else Ink)}
        }
        total.planned?.let{Progress(total.actual,it)}
        TeaSmall(if(total.planned==null)"尚未设置预算 · 点按设置" else "使用 ${total.rate.asShare()} · 点按查看详情")
        if(total.coveredMonths<total.allMonths&&total.planned!=null)TeaSmall("预算覆盖 ${total.coveredMonths}/${total.allMonths} 月，剩余仅按有预算月份计算。")
    }
    TeaCard(modifier=Modifier.testTag("budget-nature-summary"),padding=12.dp,spacing=9.dp) {
        listOf(Bucket.NECESSARY.name to "必要消费",Bucket.FREE.name to "非必要消费").forEach{(bucket,label)->
            val value=index.budgetNatureComparison(range,bucket)
            Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f),fontSize=13.sp,color=Ink);Text(if(value.planned==null)"未设置预算" else "¥${Money.format(value.actual)} / ¥${Money.format(value.planned)}",fontSize=11.sp,color=Muted)}
                LinearProgressIndicator(progress={(value.rate?.div(100) ?: 0.0).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(3.dp)),color=if((value.remaining ?: 0)<0)Red else expenseChartColor(if(bucket==Bucket.NECESSARY.name)Necessity.NECESSARY.name else Necessity.NONESSENTIAL.name),trackColor=Muted.copy(alpha=.12f))
            }
        }
        val selectedPlans=index.ledger.budgets.filter{it.month in range.months}
        if(selectedPlans.isNotEmpty())TeaSmall("未分配 ¥${Money.format(selectedPlans.map{it.total-it.necessary-it.free}.exactSum())} · 不自动挪用")
    }
    val focus=ExpenseTaxonomy.groups.filter{caps.getValue(it.id).planned!=null}.sortedWith(compareByDescending<ExpenseGroup>{(caps.getValue(it.id).remaining ?: 0)<0}.thenByDescending{report.groups.getValue(it.id).net})
    BudgetCategoryList(report,caps,focus,open,focus=true)
    if(compact)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        TextButton(onClick={open(analysisEditor("budgetCategories",range))},modifier=Modifier.weight(1f)){Text("查看全部分类",fontSize=12.sp)}
        TextButton(onClick={open(Editor("budgetOverview",kind=java.time.YearMonth.from(range.end).toString()))},modifier=Modifier.weight(1f)){Text("调整月度预算",fontSize=12.sp)}
    } else {
        TeaLink("查看全部分类","分类预算按需设置"){open(analysisEditor("budgetCategories",range))}
        TeaLink("调整月度预算"){open(Editor("budgetOverview",kind=java.time.YearMonth.from(range.end).toString()))}
    }
}

/** Dense rows share one glass panel; pagination keeps the panel and its controls together. */
@Composable fun BudgetCategoryList(report:ExpenseReport,caps:Map<String,BudgetComparison>,groups:List<ExpenseGroup>,open:(Editor)->Unit,focus:Boolean=false) {
    val density=LocalDensity.current
    val compact=with(density){LocalWindowInfo.current.containerSize.height.toDp()}<820.dp||density.fontScale>1.2f
    val perPage=if(focus)if(compact)2 else 3 else if(compact)6 else 8
    var page by rememberSaveable(focus){mutableIntStateOf(0)}
    val pages=((groups.size+perPage-1)/perPage).coerceAtLeast(1)
    val selected=page.coerceIn(0,pages-1)
    TeaCard(modifier=Modifier.testTag(if(focus)"budget-focus-list" else "budget-all-list"),padding=12.dp,spacing=3.dp) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(if(focus)"重点分类" else "全部分类",Modifier.weight(1f),fontSize=14.sp,color=Ink)
            if(pages>1){TextButton(onClick={page=selected-1},enabled=selected>0,modifier=Modifier.size(30.dp).testTag("budget-list-previous"),contentPadding=PaddingValues(0.dp)){Text("‹")};Text("${selected+1}/$pages",fontSize=10.sp,color=Muted);TextButton(onClick={page=selected+1},enabled=selected<pages-1,modifier=Modifier.size(30.dp).testTag("budget-list-next"),contentPadding=PaddingValues(0.dp)){Text("›")}}
        }
        if(groups.isEmpty())TeaSmall("暂未设置分类预算，可在全部分类中按需添加。")
        groups.drop(selected*perPage).take(perPage).forEach{g->val cap=caps.getValue(g.id);val actual=report.groups.getValue(g.id).net
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable{open(analysisEditor("budgetDetail",report.range,g.id))}.padding(vertical=7.dp,horizontal=2.dp).testTag("budget-category-${g.id}"),verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.size(7.dp).background(expenseChartColor(g.id)));Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)){Text(g.name,fontSize=13.sp,color=Ink);Text(if(cap.planned==null)"未设分类预算" else if((cap.remaining ?: 0)<0)"超出 ¥${Money.format(-cap.remaining!!)}" else "预算 ¥${Money.format(cap.planned)}",fontSize=10.sp,color=if((cap.remaining ?: 0)<0)Red else Muted)}
                Text("¥${Money.format(actual)}  ›",fontSize=13.sp,color=Ink)
            }
        }
    }
}
