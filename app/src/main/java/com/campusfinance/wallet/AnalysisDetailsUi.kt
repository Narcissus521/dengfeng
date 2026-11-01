package com.campusfinance.wallet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs

private fun calendarAmount(value:Long):String {
    val magnitude=abs(value)
    return when {
        magnitude>=1_000_000->"≈"+BigDecimal.valueOf(value,6).setScale(1,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()+"万"
        magnitude>=100_000->"≈"+BigDecimal.valueOf(value,5).setScale(1,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()+"千"
        else->Money.plain(value)
    }
}

@Composable fun ExpenseCalendar(s:Ledger,report:ExpenseReport,open:(Editor)->Unit,onMonthChange:((YearMonth)->Unit)?=null) {
    var monthKey by rememberSaveable(report.range.key){mutableStateOf(YearMonth.from(report.range.end).toString())}
    var selectedKey by rememberSaveable(report.range.key){mutableStateOf(report.range.end.toString())}
    val month=YearMonth.parse(monthKey)
    val days=remember(s.profile.revision,report,monthKey){s.calendarFlows(report,month)}
    val byDate=days.associateBy{it.date}
    val largest=days.maxOfOrNull{maxOf(it.spending.gross,it.income)}?.coerceAtLeast(1) ?: 1
    val firstMonth=YearMonth.from(report.range.start);val lastMonth=YearMonth.from(report.range.end)
    val selected=LocalDate.parse(selectedKey).takeIf{it in byDate} ?: days.firstOrNull()?.date
    val shade=Forest;val refund=Muted
    val expense=Color(0xFFC98D5E);val income=Color(0xFF6F97AF)
    val expenseInk=if(LocalWalletColors.current.dark)Color(0xFFE9C09E) else Color(0xFF9B6039)
    val incomeInk=if(LocalWalletColors.current.dark)Color(0xFFBED5E3) else Color(0xFF496E87)
    fun move(delta:Long){val next=month.plusMonths(delta);if(onMonthChange!=null)onMonthChange(next) else {monthKey=next.toString();selectedKey=report.calendar(next).firstOrNull()?.date?.toString() ?: next.atDay(1).toString()}}
    TeaCard(modifier=Modifier.testTag("expense-calendar"),spacing=6.dp) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick={move(-1)},enabled=if(onMonthChange!=null)month>YearMonth.of(2000,1) else month>firstMonth,modifier=Modifier.size(36.dp).testTag("calendar-previous")){Text("‹",fontSize=25.sp)}
            Text("${month.year} 年 ${month.monthValue} 月",Modifier.weight(1f),textAlign=TextAlign.Center,fontSize=18.sp,color=Ink)
            IconButton(onClick={move(1)},enabled=if(onMonthChange!=null)month<YearMonth.from(today()) else month<lastMonth,modifier=Modifier.size(36.dp).testTag("calendar-next")){Text("›",fontSize=25.sp)}
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(17.dp,Alignment.CenterHorizontally)) {
            listOf("支出" to expense,"收入" to income,"退款" to refund).forEach{(label,color)->Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)){Box(Modifier.size(5.dp).clip(RoundedCornerShape(3.dp)).background(color));Text(label,fontSize=10.sp,color=Muted)}}
        }
        Row(Modifier.fillMaxWidth()){listOf("一","二","三","四","五","六","日").forEach{Text(it,Modifier.weight(1f),fontSize=11.sp,color=Muted,textAlign=TextAlign.Center)}}
        val offset=month.atDay(1).dayOfWeek.value-1
        repeat((offset+month.lengthOfMonth()+6)/7){week->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                repeat(7){weekday->
                    val number=week*7+weekday-offset+1
                    val date=number.takeIf{it in 1..month.lengthOfMonth()}?.let{month.atDay(it)}
                    val day=byDate[date];val active=date!=null&&date==selected
                    val hasExpense=day?.spending?.gross?.let{it>0}==true;val hasIncome=day?.income?.let{it>0}==true
                    val tint=when{active->shade.copy(alpha=.12f);hasExpense&&hasIncome->shade.copy(alpha=.07f);hasExpense->expense.copy(alpha=.08f+.14f*(day!!.spending.gross.toDouble()/largest).toFloat());hasIncome->income.copy(alpha=.08f+.14f*(day!!.income.toDouble()/largest).toFloat());day?.spending?.refunds?.let{it>0}==true->refund.copy(alpha=.08f);else->Color.Transparent}
                    val ink=if(day==null)Muted.copy(alpha=.4f) else Ink
                    Column(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(11.dp)).background(tint).then(if(active)Modifier.border(1.dp,shade,RoundedCornerShape(11.dp)) else Modifier)
                        .then(if(date!=null)Modifier.testTag("calendar-day-$date").semantics{this.selected=active;contentDescription="$date，${if(day==null)"不在统计范围" else "支出 ¥${Money.format(day.spending.gross)}，收入 ¥${Money.format(day.income)}，退款 ¥${Money.format(day.spending.refunds)}"}"}.clickable(enabled=day!=null){selectedKey=date.toString()} else Modifier),
                        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                        if(date!=null){Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(2.dp)){Text(number.toString(),fontSize=12.sp,lineHeight=14.sp,color=ink,fontWeight=if(active)FontWeight.Bold else FontWeight.Normal);if(day?.spending?.refunds?.let{it>0}==true&&(hasExpense||hasIncome))Box(Modifier.size(3.dp).clip(RoundedCornerShape(2.dp)).background(refund))}
                            if(hasExpense)Text("−"+calendarAmount(day!!.spending.gross),fontSize=8.sp,lineHeight=10.sp,color=expenseInk,maxLines=1)
                            if(hasIncome)Text("+"+calendarAmount(day!!.income),fontSize=8.sp,lineHeight=10.sp,color=incomeInk,maxLines=1)
                            if(!hasExpense&&!hasIncome)Text(if(day==null)"" else if(day.spending.refunds>0)"退"+calendarAmount(day.spending.refunds) else "—",fontSize=8.sp,lineHeight=10.sp,color=Muted,maxLines=1)}
                    }
                }
            }
        }
        selected?.let{date->val day=byDate.getValue(date)
            HorizontalDivider(color=Muted.copy(alpha=.15f))
            TeaSmall("${date.monthValue} 月 ${date.dayOfMonth} 日 · ${day.spending.count} 笔支出 / ${day.incomeCount} 笔收入")
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)){Text("支出",fontSize=10.sp,color=expenseInk);GridMoney(day.spending.gross,Modifier.fillMaxWidth(),17,color=expenseInk)}
                Column(Modifier.weight(1f)){Text("收入",fontSize=10.sp,color=incomeInk);GridMoney(day.income,Modifier.fillMaxWidth(),17,color=incomeInk)}
            }
            if(day.spending.refunds>0)TeaSmall("另有退款 ¥${Money.format(day.spending.refunds)}，不计作收入。")
            TeaLink("查看当天明细",icon="arrow"){open(Editor("calendarRecords",kind=ReportFilter(ReportRange(date,date)).encode()))}
        }
    }
}

@Composable fun SpendingRhythmCard(report:ExpenseReport,groupId:String?,leafId:String?,open:(Editor)->Unit) {
    var mode by rememberSaveable{mutableIntStateOf(0)}
    val all=remember(report,groupId,leafId){report.rhythm(groupId,leafId)}
    val parts=if(mode==0)all.take(2) else all.drop(2)
    TeaCard(modifier=Modifier.testTag("spending-rhythm")) {
        Text("消费节奏",fontSize=17.sp,color=Ink)
        TeaSmall("${report.range.start} — ${report.range.end} · 沿用上方分类")
        TeaTabs(listOf("工作日与周末","月内节奏"),mode){mode=it}
        parts.forEach{p->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).clickable(enabled=p.days>0){
                open(Editor("analysisRecords",kind=ReportFilter(report.range,groupId,leafId,pattern=p.pattern).encode()))
            }.padding(vertical=8.dp,horizontal=3.dp).testTag("rhythm-${p.pattern.name}"),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)){Text(p.pattern.label,fontSize=13.sp,color=Ink);TeaSmall(if(p.days==0)"所选时段尚未覆盖" else "覆盖 ${p.days} 天 · 日均 ¥${Money.format(p.average!!)}")}
                if(p.days>0)Text("¥${Money.format(p.totals.net)} ›",fontSize=13.sp,color=Ink)
            }
        }
        TeaSmall("日均按所选时段日历天数计算，含无记录日；工作日按周一至周五，负数表示净退款。")
    }
}

private data class ComparisonBranch(val title:String,val amount:Long?,val color:Color)

/** Matching zero-origin bars on connected branches; no missing budget is synthesized. */
@Composable fun BudgetBranchPanel(title:String,value:BudgetComparison,actual:Long=value.actual) {
    val branches=listOf(ComparisonBranch("预算金额",value.planned,Forest),ComparisonBranch("实际净消费",actual,Blue))
    TeaCard(modifier=Modifier.testTag("budget-branch-diagram")) {
        ComparisonTree(title,branches)
        value.remaining?.let{AmountLine(if(it<0)"超出预算" else "剩余预算",if(it<0)-it else it)}
        if(value.planned==null)TeaSmall("尚未设置对应预算，不计算剩余额度和使用率。")
        else {value.rate?.let{TeaSmall("预算使用 ${String.format(java.util.Locale.ROOT,"%.1f%%",it)}")}
            TeaSmall("预算覆盖 ${value.coveredMonths}/${value.allMonths} 个月；所涉月份使用整月计划，不按天折算。")}
        if(value.planned!=null&&value.unbudgetedActual!=0L)AmountLine("未设预算月份另有净消费",value.unbudgetedActual)
    }
}
@Composable private fun ComparisonTree(title:String,branches:List<ComparisonBranch>,onBranch:((Int)->Unit)?=null) {
    val line=Muted.copy(alpha=.45f)
    val maximum=branches.mapNotNull{it.amount}.maxOrNull()?.coerceAtLeast(1) ?: 1
    val branchHeight=84.dp*LocalDensity.current.fontScale.coerceAtLeast(1f)
    Row(Modifier.fillMaxWidth().height(branchHeight*branches.size+8.dp*(branches.size-1)),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.weight(.85f).teaGlass(Lime,radius=17f).padding(horizontal=7.dp,vertical=16.dp),contentAlignment=Alignment.Center){Text(title,fontSize=13.sp,lineHeight=19.sp,color=Ink,textAlign=TextAlign.Center)}
        Canvas(Modifier.width(23.dp).fillMaxHeight()) {
            val mid=size.width*.5f;val ys=branches.indices.map{size.height*(it+.5f)/branches.size}
            drawLine(line,Offset(0f,size.height/2),Offset(mid,size.height/2),1.dp.toPx())
            if(ys.isNotEmpty())drawLine(line,Offset(mid,ys.first()),Offset(mid,ys.last()),1.dp.toPx())
            ys.forEach{y->drawLine(line,Offset(mid,y),Offset(size.width,y),1.dp.toPx())}
        }
        Column(Modifier.weight(2.4f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            branches.forEachIndexed{i,b->
                Column(Modifier.fillMaxWidth().height(branchHeight).teaGlass(Card,radius=16f).then(if(onBranch!=null)Modifier.clickable{onBranch(i)} else Modifier).padding(horizontal=11.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(3.dp,Alignment.CenterVertically)) {
                    Text(b.title,fontSize=11.sp,color=Muted)
                    if(b.amount==null)Text("未设置",fontSize=16.sp,color=Ink) else GridMoney(b.amount,Modifier.fillMaxWidth(),17)
                    LinearProgressIndicator(progress={b.amount?.let{(it.toDouble()/maximum).toFloat().coerceIn(0f,1f)} ?: 0f},modifier=Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),color=b.color,trackColor=b.color.copy(alpha=.12f))
                }
            }
        }
    }
}
@Composable fun CategorySpendBranches(report:ExpenseReport,group:ExpenseGroup,open:(Editor)->Unit) {
    if(group.children.isEmpty())return
    val populated=group.children.filter{report.leaves.getValue(it.id).let{t->t.count>0||t.refundCount>0}}
    if(populated.isEmpty()){TeaSmall("本时段暂无二级分类消费。");return}
    Text("二级消费去向",fontSize=17.sp,color=Ink)
    TeaSmall("预算设在一级分类；以下仅展示二级实际净消费。")
    populated.chunked(3).forEach{chunk->TeaCard {
        ComparisonTree(group.name,chunk.map{ComparisonBranch(it.name,report.leaves.getValue(it.id).net,expenseChartColor(it.id))}){i->open(Editor("analysisRecords",kind=ReportFilter(report.range,group.id,chunk[i].id).encode()))}
    }}
}
