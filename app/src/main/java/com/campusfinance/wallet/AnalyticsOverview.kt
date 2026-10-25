package com.campusfinance.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

private data class OverviewAnalysis(val report:ExpenseReport,val trend:List<TrendPoint>,val growth:Growth,val budget:BudgetComparison)
@Composable fun AnalyticsOverview(s:Ledger,month:String,onMonth:(String)->Unit,open:(Editor)->Unit) {
    var choice by rememberSaveable{mutableIntStateOf(0)}
    var custom by rememberSaveable{mutableStateOf("")}
    var dateDialog by remember{mutableStateOf(false)}
    var start by rememberSaveable{mutableStateOf(YearMonth.parse(month).atDay(1).toString())}
    var end by rememberSaveable{mutableStateOf(minOf(YearMonth.parse(month).atEndOfMonth(),today()).toString())}
    var error by remember{mutableStateOf<String?>(null)}
    val range=remember(month,choice,custom){if(choice==4&&custom.isNotEmpty())ReportRange.parse(custom) else presetReportRange(month,listOf("month","3","6","year")[choice.coerceIn(0,3)])}
    val data by produceState<OverviewAnalysis?>(null,s.profile.revision,range.key) {
        value=null;value=withContext(Dispatchers.Default){val index=ExpenseIndex(s);val report=index.report(range);val selectedMonth=YearMonth.from(range.end)
            val current=index.report(ReportRange.month(selectedMonth.toString()));val old=index.report(ReportRange.month(selectedMonth.minusMonths(1).toString()))
            OverviewAnalysis(report,index.trend(selectedMonth,6),compareSpending(current.total.net,old.total.net,old.total.count>0||old.total.refundCount>0),index.budgetComparison(range))}
    }
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("消费分析",fontSize=23.sp,fontWeight=FontWeight.Medium,color=Ink)
        TeaTabs(listOf("月度","3 个月","6 个月","年度","自定"),choice){if(it==4){start=range.start.toString();end=range.end.toString();dateDialog=true}else choice=it}
        if(choice==4)TextButton(onClick={dateDialog=true},modifier=Modifier.fillMaxWidth()){Text("${range.start} — ${range.end}  ▾",fontSize=12.sp)}
        else MonthPicker(month,{onMonth(minOf(it,currentMonth()))})
        OverviewGrid(listOf(OverviewCard.ANALYSIS_TOTAL,OverviewCard.ANALYSIS_CHART,OverviewCard.ANALYSIS_TREND),Modifier.weight(1f),minimumRow=60.dp) {card,info->
            val destination=when(card){OverviewCard.ANALYSIS_CHART->"category";OverviewCard.ANALYSIS_TREND->"trend";else->"overview"}
            GridTile(card,info,if(card==OverviewCard.ANALYSIS_TOTAL)LocalWalletColors.current.hero else Card,click={open(analysisEditor(destination,range))}) {
                GridColumn(info) {
                    val d=data
                    when(card) {
                        OverviewCard.ANALYSIS_TOTAL->{
                            if(d==null)GridNote("正在整理账本…") else if(info.narrow){if(info.compact){GridNote(if(choice==0)"本月净消费" else "时段净消费",lines=1);GridMoney(d.report.total.net,Modifier.fillMaxWidth(),20)}else{GridHeading(card,info,if(choice==0)"本月净消费" else "时段净消费");GridMoney(d.report.total.net,Modifier.fillMaxWidth(),25);GridDetailsForOverview("日均",d.report.dailyAverage)}}
                            else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                                Column(Modifier.weight(1.3f)){GridNote(if(choice==0)"本月净消费" else "时段净消费");GridMoney(d.report.total.net,Modifier.fillMaxWidth(),if(info.compact)22 else 25)}
                                Column(Modifier.weight(1f)){GridNote("日均消费");GridMoney(d.report.dailyAverage,Modifier.fillMaxWidth(),17)}
                                Column(Modifier.weight(1.2f)){GridNote("${YearMonth.from(range.end).monthValue}月较上月");GridNote(d.growth.percent?.let{String.format(Locale.ROOT,"%+.1f%%",it)} ?: "无可比增长率",lines=1);if(!info.compact)GridMoney(d.growth.delta,Modifier.fillMaxWidth(),15)}
                            }
                        }
                        OverviewCard.ANALYSIS_CHART->{
                            AnalysisPreviewHeading("分类概览","category"){open(analysisEditor("category",range))}
                            if(d==null)GridNote("正在整理分类…") else if(info.compact)GridNote(d.report.groups.maxByOrNull{it.value.net}?.key?.let{id->ExpenseTaxonomy.groups.first{it.id==id}.name} ?: "暂无消费")
                            else Row(Modifier.weight(1f).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                if(!info.narrow)BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                                    ReportDonut(d.report.groups.map{it.key to it.value.chart(d.report.grossBasis)},d.report.necessities.map{it.key to it.value.chart(d.report.grossBasis)},
                                        "双层圆环图：外圈消费种类，内圈必要消费、非必要消费、未判断",{g->open(analysisEditor("category",range,g))},{n->open(Editor("analysisRecords",kind=ReportFilter(range,necessity=n).encode()))},legend=false,height=maxHeight)
                                }
                                Column(Modifier.weight(1.3f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                    val top=ExpenseTaxonomy.groups.filter{d.report.groups.getValue(it.id).let{v->v.count>0||v.refundCount>0}}.sortedByDescending{d.report.groups.getValue(it.id).net}.take(3)
                                    if(top.isEmpty())GridNote("暂无消费记录")
                                    top.forEachIndexed{i,g->val value=d.report.groups.getValue(g.id);val share=d.report.share(value)?.let{String.format(Locale.ROOT,"%.1f%%",it)} ?: "—"
                                        if(info.detailed){GridNote("${i+1}  ${g.name} · $share",lines=1);GridMoney(value.net,Modifier.fillMaxWidth(),15)}
                                        else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalAlignment=Alignment.CenterVertically){Text("${i+1} ${g.name} $share",Modifier.weight(1f),fontSize=10.sp,color=Muted,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis);Text("¥${Money.format(value.net)}",fontSize=11.sp,color=Ink,maxLines=1)}
                                    }
                                    if(d.report.grossBasis)GridNote("占比按消费原额",lines=1)
                                }
                            }
                        }
                        OverviewCard.ANALYSIS_TREND->{
                            AnalysisPreviewHeading("近 6 个月消费","trend"){open(analysisEditor("trend",range))}
                            if(d==null)GridNote("正在整理趋势…") else if(info.compact)GridNote("${d.trend.last().month} ¥${Money.format(d.trend.last().amount)}")
                            else {BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()){TrendPlot(d.trend,d.trend.last().month,height=maxHeight){m->open(Editor("analysisRecords",kind=ReportFilter(ReportRange.month(m)).encode()))}};GridNote("${d.trend.last().month} · 净消费 ¥${Money.format(d.trend.last().amount)}",lines=1)}
                        }
                        else->Unit
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(Triple("necessity","消费必要性","必要 / 非必要 / 未判断"),Triple("budget","预算分析",data?.budget?.rate?.let{String.format(Locale.ROOT,"使用 %.1f%%",it)} ?: "查看预算安排"),Triple("alerts","异常消费","查看需关注的变化")).forEach{(page,title,subtitle)->
                TeaCard(modifier=Modifier.weight(1f).fillMaxHeight().testTag("analysis-entry-$page"),onClick={open(analysisEditor(page,range))},padding=9.dp,spacing=0.dp) {
                    Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp,Alignment.CenterVertically)) {
                        Text(title,Modifier.fillMaxWidth(),fontSize=12.sp,lineHeight=16.sp,color=Ink,textAlign=TextAlign.Center)
                        Text(subtitle,Modifier.fillMaxWidth(),fontSize=9.sp,lineHeight=13.sp,color=Muted,textAlign=TextAlign.Center,maxLines=2)
                    }
                }
            }
        }
    }
    if(dateDialog)AlertDialog(onDismissRequest={dateDialog=false},title={Text("自定义统计时段")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){DateInput("统计开始日期",start){start=it};DateInput("统计结束日期",end){end=it};error?.let{Text(it,color=Red)}}},confirmButton={TextButton(onClick={runCatching{val r=ReportRange(LocalDate.parse(start),LocalDate.parse(end));require(r.start>=LocalDate.of(2000,1,1)&&r.end<=today()){"请选择 2000 年起至今天之间的日期"};custom=r.key;choice=4;dateDialog=false;error=null}.onFailure{error=it.message}}){Text("应用时段")}},dismissButton={TextButton(onClick={dateDialog=false}){Text("取消")}})
}
@Composable private fun GridDetailsForOverview(label:String,amount:Long){GridNote(label);GridMoney(amount,Modifier.fillMaxWidth(),17)}

@Composable private fun AnalysisPreviewHeading(title:String,page:String,open:()->Unit){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(title,Modifier.weight(1f),fontSize=13.sp,color=Ink,maxLines=1);TextButton(onClick=open,modifier=Modifier.height(24.dp).testTag("analysis-open-$page"),contentPadding=PaddingValues(horizontal=3.dp,vertical=0.dp)){Text("查看详情 ›",fontSize=10.sp)}}}
