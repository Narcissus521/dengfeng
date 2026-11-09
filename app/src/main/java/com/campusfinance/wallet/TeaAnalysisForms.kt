package com.campusfinance.wallet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class BookResult(val revision:Long,val query:RecordQuery,val rows:List<Entry>,val summary:RecordSummary)
@Composable fun MonthBookForm(s:Ledger,initial:String,close:()->Unit,open:(Editor)->Unit,category:String?=null,range:String?=null,title:String?=null) {
    val quickState=LocalLedgerQuickState.current
    var month by rememberSaveable{mutableStateOf(initial)}
    var categoryGroup by rememberSaveable{mutableStateOf("")}
    var necessity by rememberSaveable{mutableStateOf("")}
    var account by rememberSaveable{mutableStateOf("")}
    var search by rememberSaveable{mutableStateOf("")}
    var showVoided by rememberSaveable{mutableStateOf(false)}
    var filtersOpen by rememberSaveable{mutableStateOf(false)}
    var group by rememberSaveable{mutableIntStateOf(0)}
    val periods=if(range==null)listOf(month) else analysisStats(s,month,range).months
    val query=RecordQuery(periods,account,search,showVoided,category,categoryGroup.ifEmpty{null},necessity.ifEmpty{null})
    val loaded by produceState<BookResult?>(null,s.profile.revision,query) {
        value=withContext(Dispatchers.Default){val rows=s.bookRecords(query);BookResult(s.profile.revision,query,rows,recordSummary(rows))}
    }
    val result=loaded?.takeIf{it.query==query&&it.revision==s.profile.revision}
    val entries=result?.rows.orEmpty()
    val selectedAccount=if(account.isEmpty()) "全部账户" else s.accountName(account)
    val selectedCategory=ExpenseTaxonomy.groups.find{it.id==categoryGroup}
    val natureLabel=Necessity.entries.find{it.name==necessity}?.label ?: "全部性质"
    val filtering=categoryGroup.isNotEmpty()||necessity.isNotEmpty()||account.isNotEmpty()||search.isNotBlank()||showVoided
    Form(title ?: category?.let{"${it}消费记录"} ?: "本月账本",close=close,confirm=null) {
        TeaCard(tint=LocalWalletColors.current.hero,modifier=Modifier.testTag("records-summary").semantics{result?.summary?.let{contentDescription="收入 ¥${Money.format(it.income)}，支出 ¥${Money.format(it.expense)}，退款 ¥${Money.format(it.refunds)}，净消费 ¥${Money.format(it.net)}，还款 ¥${Money.format(it.repayments)}"}}) {
            if(range==null)MonthPicker(month,{month=it;group=0}) else TeaSmall("${periods.firstOrNull() ?: month} — ${periods.lastOrNull() ?: month}")
            result?.summary?.let{summary->
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)){TeaSmall("${if(filtering)"筛选后" else if(range!=null)"本时段" else "本月"}净消费");GridMoney(summary.net,Modifier.fillMaxWidth(),27)}
                    TeaArt("steep",Modifier.size(58.dp,46.dp))
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    listOf("收入" to summary.income,"支出" to summary.expense,"退款" to summary.refunds).forEach{(label,amount)->Column(Modifier.weight(1f)){TeaSmall(label);GridMoney(amount,Modifier.fillMaxWidth(),14)}}
                }
                if(summary.repayments>0){AmountLine("还款合计",summary.repayments);if(summary.consumptionRepayments>0)TeaSmall("其中 ¥${Money.format(summary.consumptionRepayments)} 已包含在消费中，请勿重复相加。")}
                TeaSmall("净消费为支出减退款；转账不算收支，还款单独列出。")
            } ?: TeaSmall("正在整理记录…",Modifier.testTag("records-loading"))
        }
        TeaCard(modifier=Modifier.testTag("records-filters")) {
            Field("搜索分类、账户或备注",search,{search=it;group=0})
            TextButton(onClick={filtersOpen=!filtersOpen},modifier=Modifier.fillMaxWidth().testTag("records-filter-toggle")) {
                Text("${selectedCategory?.name ?: category ?: "全部分类"} · $natureLabel · $selectedAccount${if(showVoided)" · 含已撤销" else ""}",Modifier.weight(1f),fontSize=12.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Start)
                Text(if(filtersOpen)"收起 ⌃" else "筛选 ⌄",fontSize=12.sp)
            }
            if(filtersOpen) {
                if(category==null)Box(Modifier.fillMaxWidth().testTag("records-category-select")){Select("一级消费分类",listOf("")+ExpenseTaxonomy.groups.map{it.id},categoryGroup,{id->ExpenseTaxonomy.groups.find{it.id==id}?.name ?: "全部分类"}){categoryGroup=it;group=0}}
                Box(Modifier.fillMaxWidth().testTag("records-nature-select")){Select("消费性质",listOf("")+Necessity.entries.map{it.name},necessity,{id->Necessity.entries.find{it.name==id}?.label ?: "全部性质"}){necessity=it;group=0}}
                TeaSmall("分类与性质可组合筛选支出及退款，退款跟随原消费；不把收入、转账或还款当作消费。")
            }
            if(filtering)TextButton(onClick={search="";categoryGroup="";necessity="";account="";showVoided=false;group=0},modifier=Modifier.testTag("records-clear")){Text("清除筛选",fontSize=12.sp)}
        }
        if(filtersOpen)TeaCard(modifier=Modifier.testTag("records-account-filters")) {
            val creditAccount=if(s.entries.any{it.huabeiDueDate!=null}) listOf(HUABEI_ACCOUNT) else emptyList()
            Select("资金账户",listOf("")+s.accounts.map{it.id}+creditAccount,account,{id->
                if(id.isEmpty())"全部账户" else s.accountName(id)+if(s.accounts.any{it.id==id&&it.disabledOn!=null})"（已停用）" else ""
            }){account=it;group=0}
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Checkbox(showVoided,{showVoided=it;group=0},Modifier.testTag("records-show-voided"));TeaSmall("显示已撤销记录（不计入金额）")}
        }
        result?.let{book->
            TeaSmall("${book.summary.activeCount} 条有效记录${if(showVoided)" · ${book.summary.voidedCount} 条已撤销" else ""}，点按查看详情。",Modifier.testTag("records-count"))
            val last=(entries.size-1).coerceAtLeast(0)/15;val current=group.coerceAtMost(last)
            if(entries.size>15)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                TextButton(onClick={group=(current-1).coerceAtLeast(0)},enabled=current>0,modifier=Modifier.testTag("records-newer")){Text("较新记录")}
                TeaSmall("${current*15+1}–${minOf((current+1)*15,entries.size)} / ${entries.size}")
                TextButton(onClick={group=(current+1).coerceAtMost(last)},enabled=current<last,modifier=Modifier.testTag("records-older")){Text("更早记录")}
            }
            if(entries.isEmpty())TeaCard(modifier=Modifier.testTag("records-empty")) {
                Text(if(filtering)"没有符合条件的记录" else "这个月还没有记账",fontSize=17.sp)
                TeaSmall(if(filtering)"试试其他月份、分类、消费性质或账户，也可以清除筛选。" else "从记账菜单记下第一笔，收入和支出都会收录在这里。")
            }
            if(entries.isNotEmpty())TeaCard(padding=12.dp,spacing=0.dp){
                entries.drop(current*15).take(15).forEachIndexed{i,e->
                    Column(Modifier.fillMaxWidth().testTag("record-${e.id}").clickable{if(quickState?.sourceClickAllowed()!=false)open(Editor("detail",e.id))}){
                        EntryRow(e,s){open(Editor("detail",e.id))}
                        if(e.note.isNotBlank())Text(e.note,Modifier.fillMaxWidth().padding(bottom=8.dp),fontSize=11.sp,lineHeight=17.sp,color=Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                    if(i<minOf(15,entries.size-current*15)-1)HorizontalDivider(color=MaterialEdge)
                }
            }
        }
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
        stats.overMonths.forEach{b->TeaLink("${b.month} · 超出 ¥${Money.format(s.usage(b.month)-b.total)}","查看必要消费、非必要消费与相关记录"){open(Editor("monthBudget",id=b.month))}}
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
        if(b==null)TeaSmall("为必要消费与非必要消费分别设置预算，未分配额度单独保留。")
        else {
            TeaCard(tint=LocalWalletColors.current.hero){AmountLine("总预算",b.total);AmountLine("已用",s.usage(month));AmountLine("剩余",b.total-s.usage(month));Progress(s.usage(month),b.total)}
            listOf("NECESSARY" to b.necessary,"FREE" to b.free).forEach{(key,total)->TeaCard {
                Text(Bucket.valueOf(key).label,fontSize=15.sp);AmountLine("预算",total);AmountLine("已用",s.usage(month,key));AmountLine(if(s.usage(month,key)>total)"超出" else "剩余",kotlin.math.abs(total-s.usage(month,key)),if(s.usage(month,key)>total)Red else Ink);Progress(s.usage(month,key),total)
            }}
            TeaSmall("未分配预算 ¥${Money.format(b.total-b.necessary-b.free)}，不自动挪用。")
            TeaCard {
                AmountLine("未判断消费",s.usage(month,Bucket.UNDECIDED.name))
                TeaSmall("已计入总预算，暂不计入必要与非必要两个分项；不会自动使用未分配额度。")
            }
        }
        ConsumptionReviewLink(s,month,open)
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
@Composable fun DebtListForm(s:Ledger,close:()->Unit,open:(Editor)->Unit,removed:Boolean=false) {
    var kind by rememberSaveable{mutableStateOf("*")}
    val options=listOf(DebtCategory("*","全部种类"),DebtCategory("","未分类"))+s.debtCategories
    Form(if(removed)"已移除计划" else "负债",close=close,confirm=if(removed)null else "＋ 添加负债",submit={open(Editor("debt"))}) {
        TeaCard(tint=LocalWalletColors.current.hero){if(removed){TeaSmall("合同与历史流水保留");Text("${s.debts.count{it.archived}} 个已移除计划",fontSize=24.sp)}else{TeaSmall("全部剩余待还");Text("¥${Money.format(s.pendingRepayments().map{it.due}.exactSum())}",fontSize=31.sp)}}
        Select("欠款种类",options,options.find{it.id==kind},{it.name}){kind=it.id}
        TextButton(onClick={open(Editor("debtCategories"))}){Text("管理自定义欠款种类")}
        if(!removed&&kind=="*")s.huabeiBills().forEach{HuabeiBillLink(it,open)}
        s.debts.filter{it.archived==removed&&(kind=="*"||(it.categoryId ?: "")==kind)}.forEach{d->TeaCard(modifier=Modifier.ledgerQuickActions(QuickTarget(debtId=d.id)).testTag("debt-plan-${d.id}"),onClick={open(Editor("debtDetail",d.id))}){
            Text(d.name,fontSize=17.sp);TeaSmall(s.debtCategoryName(d));AmountLine(if(removed)"原计划未还（不计当前待还）" else "剩余待还",s.installments.filter{it.debtId==d.id&&s.payments(it).isEmpty()}.map{it.due}.exactSum());TeaSmall("查看逐期金额与付款账户 ›")
        }}
        if(!removed&&s.debts.any{it.archived})TeaLink("已移除计划（${s.debts.count{it.archived}}）","历史流水保留，可在这里恢复"){open(Editor("removedDebtList"))}
        if(s.debts.none{it.archived==removed})TeaSmall("可设置总期数、每期相同或不同金额，并组合多个账户登记实际还款。")
    }
}
