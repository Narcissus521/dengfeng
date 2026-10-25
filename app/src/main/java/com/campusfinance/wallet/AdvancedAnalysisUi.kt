package com.campusfinance.wallet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.hypot

fun presetReportRange(month:String,kind:String,now:LocalDate=today()):ReportRange {
    val selected=minOf(YearMonth.parse(month),YearMonth.from(now))
    val end=if(kind=="year")minOf(selected.withMonth(12).atEndOfMonth(),now) else minOf(selected.atEndOfMonth(),now)
    val start=when(kind){"year"->selected.withMonth(1).atDay(1);"3","6"->selected.minusMonths(kind.toLong()-1).atDay(1);else->selected.atDay(1)}
    return ReportRange(start,end)
}
private fun percent(value:Double?)=value?.let{String.format(Locale.ROOT,"%.1f%%",it)} ?: "—"
private fun signedMoney(value:Long)=(if(value>0)"+" else "")+Money.format(value)
private data class TrendComparison(val point:TrendPoint,val growth:Growth,val share:Double?,val oldShare:Double?,val gross:Boolean)
private data class AnalysisBundle(val index:ExpenseIndex,val report:ExpenseReport,val budget:BudgetComparison,
    val categoryBudgets:Map<String,BudgetComparison>,val budgetMonths:List<Pair<Budget,Long>>,val trend:List<TrendPoint>,val necessityTrend:List<TrendPoint>,val comparisons:Map<String,TrendComparison>,val alerts:AlertResult)

@Composable fun AdvancedAnalysisForm(s:Ledger,initialMonth:String,initialKind:String,close:()->Unit,open:(Editor)->Unit) {
    val destination=remember(initialKind){AnalysisDestination.decode(initialKind,initialMonth)}
    val initial=destination.range
    var section by rememberSaveable{mutableIntStateOf(when(destination.page){"category"->1;"necessity"->2;"trend"->3;"budget"->4;"alerts"->5;"budgetDetail"->7;"necessityDetail"->8;else->0})}
    var mode by rememberSaveable{mutableIntStateOf(when(initialKind.substringBefore(':')){"month"->0;"year"->1;else->2})}
    var month by rememberSaveable{mutableStateOf(minOf(initialMonth,currentMonth()))}
    var from by rememberSaveable{mutableStateOf(initial.start.toString())}
    var to by rememberSaveable{mutableStateOf(initial.end.toString())}
    var rangeKey by rememberSaveable{mutableStateOf(initial.key)}
    var parent by rememberSaveable{mutableStateOf(destination.group)}
    var necessityParent by rememberSaveable{mutableStateOf(destination.group)}
    var necessityLeaf by rememberSaveable{mutableStateOf(destination.leaf)}
    var necessityTrend by rememberSaveable{mutableStateOf(destination.necessity.ifEmpty{Necessity.NECESSARY.name})}
    var showRange by rememberSaveable{mutableStateOf(false)}
    var trendGroup by rememberSaveable{mutableStateOf(destination.group)}
    var trendLeaf by rememberSaveable{mutableStateOf(destination.leaf)}
    var trendCount by rememberSaveable{mutableIntStateOf(6)}
    var trendMonth by rememberSaveable{mutableStateOf(YearMonth.from(initial.end).toString())}
    var alertMonth by rememberSaveable{mutableStateOf(YearMonth.from(initial.end).toString())}
    var alertPage by rememberSaveable{mutableIntStateOf(0)}
    var error by remember{mutableStateOf<String?>(null)}
    val context=LocalContext.current
    val prefs=remember(context){context.getSharedPreferences("wallet_settings",0)}
    val suffix=if(s.profile.demo)"demo" else "personal"
    var large by rememberSaveable{mutableLongStateOf(prefs.getLong("analysis_large_$suffix",50000).coerceIn(1,MAX_MONEY))}
    var gap by rememberSaveable{mutableLongStateOf(prefs.getLong("analysis_gap_$suffix",10000).coerceIn(1,MAX_MONEY))}
    var largeText by rememberSaveable{mutableStateOf(Money.plain(large))}
    var gapText by rememberSaveable{mutableStateOf(Money.plain(gap))}
    val range=remember(rangeKey){ReportRange.parse(rangeKey)}
    val bundle by produceState<AnalysisBundle?>(null,s.profile.revision,rangeKey,trendCount,trendGroup,trendLeaf,alertMonth,large,gap,necessityParent,necessityLeaf,necessityTrend) {
        value=null
        value=withContext(Dispatchers.Default) {
            val index=ExpenseIndex(s);val report=index.report(range)
            val trend=index.trend(YearMonth.from(range.end),trendCount,trendGroup.ifEmpty{null},trendLeaf.ifEmpty{null})
            val comparisons=trend.associate{p->
                val previous=index.report(ReportRange.month(YearMonth.parse(p.month).minusMonths(1).toString()))
                fun selected(r:ExpenseReport)=when{trendLeaf.isNotEmpty()->r.leaves.getValue(trendLeaf);trendGroup.isNotEmpty()->r.groups.getValue(trendGroup);else->r.total}
                val old=selected(previous);val current=selected(p.report);val gross=p.report.grossBasis||previous.grossBasis
                fun share(v:ExpenseTotals,r:ExpenseReport)=r.total.chart(gross).takeIf{it>0}?.let{v.chart(gross)*100.0/it}
                p.month to TrendComparison(p,compareSpending(current.net,old.net,previous.total.count>0||previous.total.refundCount>0),share(current,p.report),share(old,previous),gross)
            }
            AnalysisBundle(index,report,index.budgetComparison(range),ExpenseTaxonomy.groups.associate{it.id to index.budgetComparison(range,it.id)},s.budgets.filter{it.month in range.months}.sortedBy{it.month}.map{b->val m=YearMonth.parse(b.month);b to index.report(ReportRange(maxOf(range.start,m.atDay(1)),minOf(range.end,m.atEndOfMonth()))).total.net},trend,if(section==2||section==8)index.trend(YearMonth.from(range.end),trendCount,necessityParent.ifEmpty{null},necessityLeaf.ifEmpty{null},necessity=necessityTrend) else emptyList(),comparisons,index.alerts(YearMonth.parse(alertMonth),AnomalyRules(large,gap)))
        }
    }
    fun back(){if(showRange)showRange=false else if(section==6)section=5 else close()}
    BackHandler{back()}
    val title=when(section){1->ExpenseTaxonomy.groups.find{it.id==parent}?.name ?: "分类分析";2->"消费必要性";3->"消费趋势";4->"预算分析";5->"异常消费";6->"异常判断规则";7->"分类预算详情";8->"${Necessity.valueOf(necessityTrend).label}详情";else->"消费总览"}
    Form(title,close={back()},confirm=if(section==6)"保存规则" else null,submit={
        val rules=AnomalyRules(Money.parse(largeText),Money.parse(gapText));large=rules.largeExpense;gap=rules.minimumDifference;alertPage=0
        prefs.edit().putLong("analysis_large_$suffix",large).putLong("analysis_gap_$suffix",gap).apply();section=5
    }) {
        if(section!=6) {
            TeaSmall("${range.start} — ${range.end} · ${range.days} 天")
            TextButton(onClick={showRange=!showRange}){Text(if(showRange)"收起时段设置" else "调整统计时段")}
            if(showRange){
            TeaTabs(listOf("月度","年度","自定义"),mode){mode=it}
            if(mode<2)MonthPicker(month,{month=minOf(it,currentMonth())})
            else {DateInput("统计开始日期",from){from=it};DateInput("统计结束日期",to){to=it}}
            OutlinedButton(onClick={runCatching {
                val next=if(mode==2)ReportRange(LocalDate.parse(from),LocalDate.parse(to)) else presetReportRange(month,if(mode==0)"month" else "year")
                require(next.start>=LocalDate.of(2000,1,1)&&next.end<=today()){"请选择 2000 年起至今天之间的日期"}
                rangeKey=next.key;trendMonth=YearMonth.from(next.end).toString();alertMonth=trendMonth;alertPage=0;error=null;showRange=false
            }.onFailure{error=it.message}},modifier=Modifier.testTag("analysis-apply-range")){Text("应用时段")}
            error?.let{Notice(it,true)}
            }
        }
        val data=bundle
        if(section==6) {
            Field("单笔大额提醒阈值（元）",largeText,{largeText=it},money=true)
            Field("异常增长最低金额差（元）",gapText,{gapText=it},money=true)
            TeaSmall("大额按截至该月末的未退款金额判断，组合扣款合并识别。历史比较需要此前连续 3 个完整月有消费记录；分类净额高于历史均值 50%，或占比提高至少 10 个百分点，同时达到最低金额差。")
            TeaSmall("异常只提示关注，不代表不合理，也不会替你判断消费是否必要。只在分析页展示，不反复推送通知。")
        } else if(data==null)TeaSmall("正在整理账本…")
        else {
            val report=data.report
            when(section) {
                0->{
                    TeaCard(tint=LocalWalletColors.current.hero){AmountLine("净消费总额",report.total.net);AmountLine("日均净消费",report.dailyAverage);TeaSmall("${report.total.count} 笔消费 · ${report.total.refundCount} 笔退款");AmountLine("消费原额",report.total.gross);AmountLine("到账退款",report.total.refunds)}
                    TeaLink("查看消费与退款记录"){open(Editor("analysisRecords",kind=ReportFilter(range).encode()))}
                    listOf("category" to "分类分析","trend" to "消费趋势","necessity" to "消费必要性","budget" to "预算分析","alerts" to "异常消费").forEach{(page,label)->TeaLink(label){open(analysisEditor(page,range))}}
                }
                1->{
                    val group=ExpenseTaxonomy.groups.find{it.id==parent}
                    if(group==null) {
                        CategoryDistribution(report,null,data.categoryBudgets,open)
                    } else {
                        CategoryDistribution(report,group,data.categoryBudgets,open)
                        TeaSmall(group.guide)
                        TeaLink("查看本类消费记录"){open(Editor("analysisRecords",kind=ReportFilter(range,group.id).encode()))}
                        TeaLink("查看本类消费趋势"){open(analysisEditor("trend",range,group.id))}
                        BudgetCard(data.categoryBudgets.getValue(group.id))
                        TeaLink("调整 ${YearMonth.from(range.end)} 分类预算"){open(Editor("categoryBudget",kind=YearMonth.from(range.end).toString()))}
                    }
                }
                2->{
                    val groups=listOf("")+ExpenseTaxonomy.groups.map{it.id}
                    Select("必要性分析范围",groups,necessityParent,{id->ExpenseTaxonomy.groups.find{it.id==id}?.name ?: "全部分类"}){necessityParent=it;necessityLeaf=""}
                    ExpenseTaxonomy.groups.find{it.id==necessityParent&&it.children.isNotEmpty()}?.let{g->Select("必要性二级分类",listOf("")+g.children.map{it.id},necessityLeaf,{id->g.children.find{it.id==id}?.name ?: "全部二级分类"}){necessityLeaf=it}}
                    val values=report.necessities(necessityParent.ifEmpty{null},necessityLeaf.ifEmpty{null})
                    val total=report.slice(necessityParent.ifEmpty{null},necessityLeaf.ifEmpty{null})
                    ReportDonut(values.map{it.key to it.value.chart(report.grossBasis)},emptyList(),"消费必要性",{n->open(Editor("analysisRecords",kind=ReportFilter(range,necessityParent.ifEmpty{null},necessityLeaf.ifEmpty{null},necessity=n).encode()))},{})
                    BasisNote(report)
                    Necessity.entries.forEach{n->val value=values.getValue(n.name)
                        TeaCard(modifier=Modifier.testTag("necessity-type-${n.name}"),onClick={open(analysisEditor("necessityDetail",range,necessityParent,necessityLeaf,n.name))}){Text(n.label,fontSize=17.sp);AmountLine("净消费",value.net);TeaSmall("${percent(report.share(value,total))} · 查看记录 ›")}
                    }
                    Text("必要性历史变化",fontSize=18.sp);TeaTabs(Necessity.entries.map{it.label},Necessity.entries.indexOfFirst{it.name==necessityTrend}){necessityTrend=Necessity.entries[it].name}
                    TeaTabs(listOf("6 个月","12 个月"),if(trendCount==6)0 else 1){trendCount=if(it==0)6 else 12}
                    TrendPlot(data.necessityTrend,trendMonth){trendMonth=it}
                    data.necessityTrend.find{it.month==trendMonth}?.let{p->AmountLine("${p.month} ${Necessity.valueOf(necessityTrend).label}",p.amount);TeaLink("查看该月对应记录"){open(Editor("analysisRecords",kind=ReportFilter(p.report.range,necessityParent.ifEmpty{null},necessityLeaf.ifEmpty{null},necessityTrend).encode()))}}
                    TeaSmall("未判断单列；不从金额、分类或原有必要／自由预算归属推断。退款跟随原消费的当前必要性。")
                }
                8->{
                    val value=report.slice(necessityParent.ifEmpty{null},necessityLeaf.ifEmpty{null},necessityTrend)
                    val total=report.slice(necessityParent.ifEmpty{null},necessityLeaf.ifEmpty{null})
                    TeaCard{AmountLine("净消费",value.net);TeaSmall("占当前范围 ${percent(report.share(value,total))} · ${value.count} 笔消费 / ${value.refundCount} 笔退款")}
                    BasisNote(report)
                    ExpenseTaxonomy.groups.find{it.id==necessityParent}?.let{TeaSmall(it.name)}
                    ExpenseTaxonomy.analysisLeaves.find{it.id==necessityLeaf}?.let{TeaSmall(it.name)}
                    TeaTabs(listOf("6 个月","12 个月"),if(trendCount==6)0 else 1){trendCount=if(it==0)6 else 12}
                    TrendPlot(data.necessityTrend,trendMonth){trendMonth=it}
                    data.necessityTrend.find{it.month==trendMonth}?.let{p->AmountLine("${p.month} 净消费",p.amount);TeaLink("查看该月必要性记录"){open(Editor("analysisRecords",kind=ReportFilter(p.report.range,necessityParent.ifEmpty{null},necessityLeaf.ifEmpty{null},necessityTrend).encode()))}}
                    TeaLink("查看本时段必要性记录"){open(Editor("analysisRecords",kind=ReportFilter(range,necessityParent.ifEmpty{null},necessityLeaf.ifEmpty{null},necessityTrend).encode()))}
                }
                3->{
                    TeaTabs(listOf("6 个月","12 个月"),if(trendCount==6)0 else 1){trendCount=if(it==0)6 else 12}
                    Select("趋势分类",listOf("")+ExpenseTaxonomy.groups.map{it.id},trendGroup,{id->ExpenseTaxonomy.groups.find{it.id==id}?.name ?: "全部消费"}){trendGroup=it;trendLeaf=""}
                    ExpenseTaxonomy.groups.find{it.id==trendGroup&&it.children.isNotEmpty()}?.let{g->Select("趋势二级分类",listOf("")+g.children.map{it.id},trendLeaf,{id->g.children.find{it.id==id}?.name ?: "全部二级分类"}){trendLeaf=it}}
                    TrendPlot(data.trend,trendMonth){trendMonth=it}
                    trendHistoricalMean(data.trend,s)?.let{AmountLine("${it.months} 个已覆盖完整月均值",it.amount)} ?: TeaSmall("暂无完整历史月份可计算均值。")
                    TeaSmall("点击柱形查看该月；负数为净退款。当前月截至今天，与上月整月比较，不预测月底消费。没有消费记录的月份单列说明。")
                    (data.comparisons[trendMonth] ?: data.comparisons.values.lastOrNull())?.let{comparison->
                        val p=comparison.point
                        TeaCard {Text(p.month,fontSize=19.sp);AmountLine("净消费",p.amount);Text("环比变化 ¥${signedMoney(comparison.growth.delta)}",fontSize=15.sp);TeaSmall(comparison.growth.percent?.let{"环比 ${percent(it)}"} ?: comparison.growth.explanation)
                            if(trendGroup.isNotEmpty()){TeaSmall("占全部${if(comparison.gross)"消费原额" else "净消费"} ${percent(comparison.share)}");if(comparison.share!=null&&comparison.oldShare!=null)TeaSmall("较上月 ${String.format(Locale.ROOT,"%+.1f",comparison.share-comparison.oldShare)} 个百分点")}
                            TeaLink("查看 ${p.month} 对应记录"){open(Editor("analysisRecords",kind=ReportFilter(p.report.range,trendGroup.ifEmpty{null},trendLeaf.ifEmpty{null}).encode()))}
                        }
                    }
                    data.trend.forEach{p->TeaLink("${p.month} · ¥${Money.format(p.amount)}",if(p.hasData)"查看该月记录" else "无消费记录"){open(Editor("analysisRecords",kind=ReportFilter(p.report.range,trendGroup.ifEmpty{null},trendLeaf.ifEmpty{null}).encode()))}}
                }
                4->{
                    BudgetCard(data.budget)
                    TeaSmall("只比较已设置预算的月份；自定义日期使用涉及月份的整月计划，不按天折算。未设置的实际消费单列。")
                    ExpenseTaxonomy.groups.forEach{g->val cap=data.categoryBudgets.getValue(g.id)
                        TeaCard(modifier=Modifier.testTag("budget-category-${g.id}"),onClick={open(analysisEditor("budgetDetail",range,g.id))}){Text(g.name,fontSize=16.sp);BudgetCardLines(cap);cap.rate?.let{LinearProgressIndicator(progress={ (it/100).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())};if(cap.remaining?.let{it<0}==true)AmountLine("超支金额",-cap.remaining!!)}
                    }
                    range.months.forEach{m->TeaLink("$m 分类预算设置"){open(Editor("categoryBudget",kind=m))}}
                    if(data.budgetMonths.isNotEmpty()) {
                        val over=data.budgetMonths.count{(b,actual)->actual>b.total}
                        TeaCard{Text("预算月份回看",fontSize=17.sp);TeaSmall("${data.budgetMonths.size} 个有预算月份中，$over 个月在所选日期内已超过其整月总预算。无预算月份不计入比例。");TeaSmall("占比 ${percent(over*100.0/data.budgetMonths.size)}")}
                        data.budgetMonths.forEach{(b,actual)->TeaLink("${b.month} · 所选日期消费 ¥${Money.format(actual)}","查看该月完整预算、必要／自由额度与记账明细"){open(Editor("budgetOverview",kind=b.month))}}
                    }
                }
                7->{
                    val group=ExpenseTaxonomy.groups.first{it.id==parent}
                    Text(group.name,fontSize=21.sp);BudgetCard(data.categoryBudgets.getValue(parent))
                    TeaLink("查看分类消费明细"){open(Editor("analysisRecords",kind=ReportFilter(range,parent).encode()))}
                    TeaLink("查看分类消费趋势"){open(analysisEditor("trend",range,parent))}
                    range.months.forEach{m->TeaLink("调整 $m 分类预算"){open(Editor("categoryBudget",kind=m))}}
                }
                5->{
                    Text("异常消费关注",fontSize=18.sp);MonthPicker(alertMonth,{alertMonth=minOf(it,currentMonth());alertPage=0})
                    TeaSmall("本页独立按上述自然月评估，当前月截至今天。提示关注，不代表消费不合理。")
                    TeaLink("设置判断阈值","大额 ¥${Money.format(large)} · 最低差额 ¥${Money.format(gap)}"){largeText=Money.plain(large);gapText=Money.plain(gap);section=6}
                    TeaSmall(data.alerts.baselineNote)
                    val alerts=data.alerts.alerts
                    if(alerts.isEmpty())TeaSmall("当前规则下没有需要提示的消费。历史不足时仅检查大额和分类预算，不强行比较历史。")
                    alerts.drop(alertPage*15).take(15).forEach{alert->TeaCard(onClick={open(alertDetailEditor(alert))}){Text(alert.title,fontSize=16.sp,color=Amber);TeaSmall(alert.explanation);TeaSmall("查看相关记录 ›")}}
                    if(alerts.size>15)Row{TextButton(onClick={alertPage--},enabled=alertPage>0){Text("上一组")};TeaSmall("${alertPage+1} / ${(alerts.size+14)/15}");TextButton(onClick={alertPage++},enabled=(alertPage+1)*15<alerts.size){Text("下一组")}}
                }
            }
        }
    }
}
@Composable private fun BasisNote(report:ExpenseReport) {
    TeaSmall(if(report.grossBasis)"有净退款或非正分类净额，图表和占比统一按消费原额展示；金额表仍为净消费，退款不遗漏。" else "图表和占比按退款后的净消费计算。")
}
@Composable private fun BudgetCard(value:BudgetComparison){TeaCard{Text("预算执行",fontSize=17.sp);BudgetCardLines(value);value.rate?.let{LinearProgressIndicator(progress={(it/100).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())}}}
@Composable private fun ColumnScope.BudgetCardLines(value:BudgetComparison) {
    if(value.planned==null){TeaSmall("尚未设置相应预算");AmountLine("未设预算时段净消费",value.unbudgetedActual)}
    else {AmountLine("预算金额",value.planned);AmountLine("预算覆盖月份实际支出",value.actual);AmountLine("剩余额度",value.remaining!!);TeaSmall("使用率 ${percent(value.rate)} · 覆盖 ${value.coveredMonths}/${value.allMonths} 个月");if(value.unbudgetedActual!=0L)AmountLine("未设预算月份净消费",value.unbudgetedActual);if(value.planned==0L)TeaSmall("预算为零，不计算使用率。")}
}
@Composable fun ReportDonut(outer:List<Pair<String,Long>>,inner:List<Pair<String,Long>>,description:String,onOuter:(String)->Unit,onInner:(String)->Unit,legend:Boolean=true,height:androidx.compose.ui.unit.Dp=190.dp) {
    val track=Pale
    val positive=outer.filter{it.second>0};val inside=inner.filter{it.second>0}
    val outerTap by rememberUpdatedState(onOuter);val innerTap by rememberUpdatedState(onInner)
    Canvas(Modifier.fillMaxWidth().height(height).testTag("analysis-donut").semantics{contentDescription=description}.pointerInput(positive,inside){detectTapGestures {point->
        val radius=minOf(size.width,size.height)/2f;val dx=point.x-size.width/2f;val dy=point.y-size.height/2f;val distance=hypot(dx,dy)
        val list=if(distance>radius*.68f&&distance<radius*.96f)positive else if(distance>radius*.31f&&distance<radius*.53f)inside else emptyList()
        val total=list.map{it.second}.exactSum();if(total>0){val angle=(Math.toDegrees(atan2(dy.toDouble(),dx.toDouble()))+450)%360;var cumulative=0.0;list.firstOrNull{cumulative+=it.second*360.0/total;angle<cumulative}?.let{if(list===positive)outerTap(it.first) else innerTap(it.first)}}
    }}) {
        val diameter=minOf(size.width,size.height);val x=(size.width-diameter)/2;val y=(size.height-diameter)/2
        fun ring(values:List<Pair<String,Long>>,inset:Float,width:Float) {
            val total=values.map{it.second}.exactSum();val origin=Offset(x+inset,y+inset);val bounds=Size(diameter-2*inset,diameter-2*inset)
            if(total==0L){drawArc(track,0f,360f,false,origin,bounds,style=Stroke(width));return}
            var angle=-90f;values.forEach{row->val sweep=(row.second*360.0/total).toFloat();drawArc(expenseChartColor(row.first),angle+.5f,(sweep-1).coerceAtLeast(0f),false,origin,bounds,style=Stroke(width));angle+=sweep}
        }
        ring(positive,diameter*.09f,diameter*.14f)
        if(inner.isNotEmpty())ring(inside,diameter*.29f,diameter*.11f)
    }
    fun label(id:String)=Necessity.entries.find{it.name==id}?.label ?: ExpenseTaxonomy.groups.find{it.id==id}?.name ?: ExpenseTaxonomy.legacyLeaves.find{it.id==id}?.name ?: id
    if(legend)positive.forEach{item->TextButton(onClick={onOuter(item.first)},contentPadding=PaddingValues(horizontal=4.dp,vertical=0.dp)) {
        Box(Modifier.size(9.dp).background(expenseChartColor(item.first)));Spacer(Modifier.width(8.dp));Text(label(item.first),Modifier.weight(1f),color=Ink);Text("¥${Money.format(item.second)}",color=Muted,fontSize=12.sp)
    }}
    if(legend)inside.forEach{item->TextButton(onClick={onInner(item.first)},contentPadding=PaddingValues(horizontal=4.dp,vertical=0.dp)) {
        Box(Modifier.size(9.dp).background(expenseChartColor(item.first)));Spacer(Modifier.width(8.dp));Text(label(item.first),Modifier.weight(1f),color=Ink);Text("¥${Money.format(item.second)}",color=Muted,fontSize=12.sp)
    }}
}
@Composable fun TrendPlot(points:List<TrendPoint>,selected:String,height:androidx.compose.ui.unit.Dp=170.dp,onSelect:(String)->Unit) {
    val positive=Forest;val negative=Blue;val highlight=Amber;val axis=Muted
    Canvas(Modifier.fillMaxWidth().height(height).testTag("analysis-trend").semantics{contentDescription="净消费趋势，负数为净退款；点击查看月份"}.pointerInput(points){detectTapGestures{p->if(points.isNotEmpty())onSelect(points[(p.x/size.width*points.size).toInt().coerceIn(0,points.lastIndex)].month)}}) {
        if(points.isEmpty())return@Canvas
        val low=minOf(0.0,points.minOf{it.amount}.toDouble());val high=maxOf(1.0,points.maxOf{it.amount}.toDouble());val height=size.height-27.dp.toPx()
        fun y(value:Double)=(height-(value-low)/(high-low)*height).toFloat()
        val zero=y(0.0);drawLine(axis,Offset(0f,zero),Offset(size.width,zero),1.dp.toPx())
        val slot=size.width/points.size;val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=android.graphics.Color.GRAY;textSize=10.sp.toPx();textAlign=android.graphics.Paint.Align.CENTER}
        points.forEachIndexed{i,p->val top=y(maxOf(0L,p.amount).toDouble());val bottom=y(minOf(0L,p.amount).toDouble());val width=minOf(24.dp.toPx(),slot*.60f)
            drawRect(if(p.month==selected)highlight else if(p.amount<0)negative else positive,Offset(slot*(i+.5f)-width/2,top),Size(width,(bottom-top).coerceAtLeast(if(p.amount==0L)1f else 2f)))
            drawContext.canvas.nativeCanvas.drawText(p.month.takeLast(2),slot*(i+.5f),size.height-6.dp.toPx(),paint)
        }
    }
}
@Composable fun AnalysisRecordsForm(s:Ledger,filter:ReportFilter,close:()->Unit,open:(Editor)->Unit) {
    var search by rememberSaveable{mutableStateOf("")};var page by rememberSaveable{mutableIntStateOf(0)}
    val all by produceState<List<Entry>?>(null,s.profile.revision,filter){value=withContext(Dispatchers.Default){ExpenseIndex(s).records(filter)}}
    val filtered=all?.filter{search.isBlank()||listOf(s.expenseCategory(it).label,it.note,s.accountName(it.accountId)).any{text->text.contains(search,true)}}
    Form("消费记录",close=close,confirm=null) {
        TeaSmall("${filter.range.start} — ${filter.range.end}")
        filter.groupId?.let{id->TeaSmall(ExpenseTaxonomy.groups.first{it.id==id}.name)}
        filter.leafId?.let{id->ExpenseTaxonomy.analysisLeaves.find{it.id==id}?.let{TeaSmall(it.name)}}
        filter.necessity?.let{TeaSmall(Necessity.valueOf(it).label)}
        Field("搜索分类、账户或备注",search,{search=it;page=0})
        if(filtered==null)TeaSmall("正在整理记录…") else {
            AmountLine("筛选后净消费",filtered.map{if(it.kind=="REFUND")-it.amount else it.amount}.exactSum())
            TeaSmall("${filtered.size} 条账户记录；组合扣款的消费笔数按整次统计。退款按到账日期冲减原消费分类。")
            if(filtered.isEmpty())TeaSmall("没有找到记录。")
            filtered.drop(page*15).take(15).forEach{e->TeaCard(padding=10.dp){EntryRow(e,s){open(Editor("detail",e.id))};if(e.note.isNotBlank())TeaSmall(e.note.take(80));TeaSmall("${s.expenseCategory(e).label} · ${Necessity.valueOf(s.necessityOf(e)).label}")}}
            if(filtered.size>15)Row{TextButton(onClick={page--},enabled=page>0){Text("上一组")};TeaSmall("${page+1} / ${(filtered.size+14)/15}");TextButton(onClick={page++},enabled=(page+1)*15<filtered.size){Text("下一组")}}
        }
    }
}
