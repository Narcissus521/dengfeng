package com.campusfinance.wallet

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.YearMonth

@Composable fun MonthBookForm(s:Ledger,initial:String,close:()->Unit,open:(Editor)->Unit,category:String?=null,range:String?=null) {
    var month by rememberSaveable{mutableStateOf(initial)}
    var filter by rememberSaveable{mutableIntStateOf(0)}
    var search by rememberSaveable{mutableStateOf("")}
    var showVoided by rememberSaveable{mutableStateOf(false)}
    val periods=if(range==null)listOf(month) else analysisStats(s,month,range).months
    val entries=s.entries.filter { e -> e.date.take(7) in periods && (showVoided||!e.voided) &&
        (category==null||s.categoryOf(e)==category) &&
        (when(filter){1->e.kind=="EXPENSE";2->e.kind=="INCOME";3->e.kind=="TRANSFER";4->e.kind=="REFUND";5->e.recurringOccurrenceId!=null;else->true}) &&
        (search.isBlank()||listOf(entryTitle(e,s),e.note,s.accountName(e.accountId)).any{it.contains(search,true)}) }.sortedWith(compareByDescending<Entry>{it.date}.thenByDescending{it.id})
    Form(category?.let{"${it}消费记录"} ?: "本月账本",close=close,confirm=null) {
        if(range==null)MonthPicker(month,{month=it}) else TeaSmall("${periods.firstOrNull() ?: month} — ${periods.lastOrNull() ?: month}")
        TeaTabs(listOf("全部","支出","收入"),filter.coerceAtMost(2)){filter=it}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(3 to "转账",4 to "退款",5 to "自动记账").forEach{(value,label)->FilterChip(selected=filter==value,onClick={filter=if(filter==value)0 else value},label={Text(label,fontSize=11.sp)})}
        }
        Field("搜索分类、账户或备注",search,{search=it})
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){TeaSmall("${entries.size} 笔记录");Row(verticalAlignment=Alignment.CenterVertically){Checkbox(showVoided,{showVoided=it});TeaSmall("显示已撤销")}}
        if(entries.isEmpty())TeaSmall("没有找到记录，可以调整月份或筛选条件。")
        entries.forEach{e->TeaCard(padding=10.dp){EntryRow(e,s){open(Editor("detail",e.id))}}}
    }
}

@Composable fun AnalysisCategoriesForm(s:Ledger,month:String,range:String,close:()->Unit,open:(Editor)->Unit) {
    AdvancedAnalysisForm(s,month,"$range:categories",close,open)
}

@Composable fun AnalysisBudgetForm(s:Ledger,month:String,range:String,close:()->Unit,open:(Editor)->Unit) {
    val stats=analysisStats(s,month,range)
    Form("预算执行",close=close,confirm=null) {
        TeaCard(tint=LocalWalletColors.current.hero) {
            Text("超预算月份",fontSize=15.sp)
            Text(stats.overPercent?.let{String.format(java.util.Locale.ROOT,"%.1f%%",it)} ?: "暂无预算",fontSize=31.sp,color=Amber)
            TeaSmall("${stats.budgetMonths.size} 个有预算月份中，${stats.overMonths.size} 个月超过月度总预算")
        }
        if(stats.overMonths.isEmpty())TeaSmall("所选时段没有已超出总预算的月份。")
        stats.overMonths.forEach{b->TeaLink("${b.month} · 超出 ¥${Money.format(s.usage(b.month)-b.total)}","查看必要消费、自由消费与相关记录"){open(Editor("monthBudget",id=b.month))}}
        TeaCard {
            Text("每月花了多少",fontSize=15.sp)
            MonthlyBars(s,stats.months)
            TeaSmall("按退款后的净消费展示；无预算的月份不计入超预算占比。")
        }
        stats.months.forEach{period->TeaLink(period,if(s.budget(period)==null)"未设置预算" else "净消费 ¥${Money.format(s.usage(period))}"){open(Editor("monthBudget",id=period))}}
    }
}

@Composable private fun MonthlyBars(s:Ledger,months:List<String>) {
    val tint=Forest;val warn=Amber;val neutral=Muted;val edge=MaterialTheme.colorScheme.outlineVariant
    val values=months.map{s.usage(it)};val maximum=(values+months.mapNotNull{s.budget(it)?.total}).maxOrNull()?.coerceAtLeast(1) ?: 1
    Canvas(Modifier.fillMaxWidth().height(145.dp)) {
        val slot=size.width/months.size.coerceAtLeast(1);val bottom=size.height-23.dp.toPx();val height=bottom-17.dp.toPx()
        val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=android.graphics.Color.GRAY;textSize=11.sp.toPx();textAlign=android.graphics.Paint.Align.CENTER}
        months.forEachIndexed { i,m ->
            val value=values[i];val x=slot*(i+.5f);val w=minOf(22.dp.toPx(),slot*.55f)
            val bar=(value.coerceAtLeast(0).toDouble()/maximum*height).toFloat()
            drawRoundRect(if(s.budget(m)?.let{value>it.total}==true)warn else tint,Offset(x-w/2,bottom-bar),Size(w,bar),androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()))
            s.budget(m)?.let { b -> val y=bottom-(b.total.toDouble()/maximum*height).toFloat();drawLine(edge,Offset(x-w*.75f,y),Offset(x+w*.75f,y),1.dp.toPx()) }
            drawContext.canvas.nativeCanvas.drawText(YearMonth.parse(m).monthValue.toString(),x,size.height-5.dp.toPx(),paint)
        }
    }
}

@Composable fun BudgetOverviewForm(s:Ledger,month:String,close:()->Unit,open:(Editor)->Unit) {
    val b=s.budget(month)
    Form("$month 月度预算",close=close,confirm=if(b==null)"设置预算" else "调整预算",submit={open(Editor("budget",kind=month))}) {
        if(b==null)TeaSmall("为必要消费与自由消费分别设置预算，未分配额度单独保留。")
        else {
            TeaCard(tint=LocalWalletColors.current.hero){AmountLine("总预算",b.total);AmountLine("已用",s.usage(month));AmountLine("剩余",b.total-s.usage(month));Progress(s.usage(month),b.total)}
            listOf("NECESSARY" to b.necessary,"FREE" to b.free).forEach{(key,total)->TeaCard {
                Text(Bucket.valueOf(key).label,fontSize=15.sp);AmountLine("预算",total);AmountLine("已用",s.usage(month,key));AmountLine(if(s.usage(month,key)>total)"超出" else "剩余",kotlin.math.abs(total-s.usage(month,key)),if(s.usage(month,key)>total)Red else Ink);Progress(s.usage(month,key),total)
            }}
            TeaSmall("未分配预算 ¥${Money.format(b.total-b.necessary-b.free)}，不自动挪用。")
        }
        TeaLink("查看该月消费记录"){open(Editor("monthBook",kind=month))}
        TeaLink("一级分类预算","在总预算内安排各类额度"){open(Editor("categoryBudget",kind=month))}
    }
}

@Composable fun GoalListForm(s:Ledger,close:()->Unit,open:(Editor)->Unit) {
    Form("储蓄计划",close=close,confirm="＋ 新建目标",submit={open(Editor("goal"))}) {
        TeaSmall("小火慢煮，每月慢慢积攒。")
        s.goals.sortedBy{it.archived}.forEach{g->TeaCard(onClick={open(Editor("goal",g.id))}){
            Text(g.name,fontSize=17.sp);AmountLine("目前已存",g.saved);AmountLine("还差",g.progress().remaining);TeaSmall("${g.date} · ${if(g.archived)"已收起" else "每月需存 ¥${Money.format(g.progress().monthly)}"}")
        }}
        if(s.goals.isEmpty())TeaSmall("写下目标金额和日期，就会计算每月需要存多少。")
    }
}
@Composable fun DebtListForm(s:Ledger,close:()->Unit,open:(Editor)->Unit) {
    var kind by rememberSaveable{mutableStateOf("*")}
    val options=listOf(DebtCategory("*","全部种类"),DebtCategory("","未分类"))+s.debtCategories
    Form("负债",close=close,confirm="＋ 添加负债",submit={open(Editor("debt"))}) {
        TeaCard(tint=LocalWalletColors.current.hero){TeaSmall("全部剩余待还");Text("¥${Money.format(s.unpaid().map{it.due}.exactSum())}",fontSize=31.sp)}
        Select("欠款种类",options,options.find{it.id==kind},{it.name}){kind=it.id}
        TextButton(onClick={open(Editor("debtCategories"))}){Text("管理自定义欠款种类")}
        s.debts.filter{kind=="*"||(it.categoryId ?: "")==kind}.forEach{d->TeaCard(onClick={open(Editor("debtDetail",d.id))}){
            Text(d.name,fontSize=17.sp);TeaSmall(s.debtCategoryName(d));AmountLine("剩余待还",s.unpaid().filter{it.debtId==d.id}.map{it.due}.exactSum());TeaSmall("查看逐期金额与付款账户 ›")
        }}
        if(s.debts.isEmpty())TeaSmall("可设置总期数、每期相同或不同金额，并组合多个账户登记实际还款。")
    }
}
