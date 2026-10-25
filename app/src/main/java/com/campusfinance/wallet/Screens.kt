package com.campusfinance.wallet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.YearMonth

@Composable fun OverviewScreen(s: Ledger, month: String, open:(Editor)->Unit, ledger:()->Unit, plan:(Int)->Unit) {
    val b=s.budget(month);val used=s.usage(month)
    val due=s.unpaid().filter{it.date.startsWith(month)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=22.dp,end=22.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(LocalWalletColors.current.hero)) {
                Canvas(Modifier.matchParentSize()) {
                    drawCircle(Color.White.copy(alpha=0.035f),size.width*0.40f,Offset(size.width*0.96f,size.height*0.80f))
                    drawCircle(Color.White.copy(alpha=0.05f),size.width*0.27f,Offset(size.width*0.96f,size.height*0.80f),style=Stroke(1.dp.toPx()))
                }
                Column(Modifier.padding(23.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        Text("总现金",color=Muted,fontSize=13.sp);Pill("截至今天",Forest,Card)
                    }
                    AnimatedMoney(s.cash(),size=37.sp,weight=FontWeight.SemiBold,color=Ink)
                    Text("真实账户余额合计 · 包含停用账户",fontSize=11.sp,color=Muted)
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant,modifier=Modifier.padding(vertical=4.dp))
                    Row(Modifier.fillMaxWidth().clickable{open(Editor("accounts"))},horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                        Text("${s.accounts.size} 个资金账户",color=Ink,fontSize=12.sp)
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) { Text("管理账户",color=MaterialTheme.colorScheme.onSecondaryContainer,fontSize=12.sp);Glyph("arrow",MaterialTheme.colorScheme.onSecondaryContainer,Modifier.size(16.dp)) }
                    }
                }
            }
        }
        if(s.accounts.any { s.balance(it.id)<0 }) item { Notice("存在负余额账户，请核对记录；负余额不代表可透支额度。",true) }
        item {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                listOf(Triple("记支出","expense","EXPENSE"),Triple("记收入","income","INCOME"),Triple("转账","transfer","TRANSFER"),Triple("买前想想","bag","simulate")).forEach { (label,icon,kind) ->
                    Column(Modifier.clickable{open(if(kind=="simulate") Editor("simulate") else Editor("transaction",kind=kind))}.padding(5.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(51.dp).clip(RoundedCornerShape(18.dp)).background(if(kind=="simulate") Lime else Card),contentAlignment=Alignment.Center) { Glyph(icon) }
                        Text(label,fontSize=12.sp,color=Ink)
                    }
                }
            }
        }
        item {
            CardBox(onClick={plan(0)}) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Text("月度预算",fontWeight=FontWeight.SemiBold,fontSize=16.sp);Caption("${YearMonth.parse(month).monthValue} 月 · 查看规划 ›")
                }
                if(b==null) { Text("本月还没有预算",fontSize=23.sp,fontWeight=FontWeight.Medium);Caption("先给必要和可选消费各留一份额度。") }
                else {
                    val left=b.total-used
                    Caption(if(left<0) "本月已超支" else "本月预算剩余",if(left<0) Red else Muted)
                    Text("¥ ${Money.format(if(left<0) -left else left)}",fontSize=30.sp,color=if(left<0) Red else Ink,fontWeight=FontWeight.SemiBold)
                    Progress(used,b.total)
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Caption("已用 ¥${Money.format(used)}");Caption("计划 ¥${Money.format(b.total)}") }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        MiniStat("必需剩余",b.necessary-s.usage(month,"NECESSARY"),Modifier.weight(1f))
                        MiniStat("可选剩余",b.free-s.usage(month,"FREE"),Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement=Arrangement.spacedBy(13.dp)) {
                SmallPlanCard("本月待还",due.map{it.due}.exactSum(),"${due.size} 期待还负债", "plan",Modifier.weight(1f)){plan(2)}
                SmallPlanCard("储蓄已存",s.goals.filterNot{it.archived}.map{it.saved}.exactSum(),"自己记录 · 查看进度","goal",Modifier.weight(1f)){plan(1)}
            }
        }
        item {
            val plans=s.incomePlans.filter{it.enabled&&it.startMonth<=month}
            CardBox(onClick={open(Editor("monthlyIncome"))}) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("每月固定收入",fontWeight=FontWeight.SemiBold);Caption("查看 / 确认到账 ›")}
                if(plans.isEmpty())Caption("为各个账户设置每月生活费、工资等收入。")
                else {
                    AmountLine("本月计划",plans.map{it.amount}.exactSum(),Forest)
                    Caption("${plans.count{s.incomeReceipt(it.accountId,month)!=null}} / ${plans.size} 项已确认，未确认金额不计余额。")
                }
            }
        }
        item {
            CardBox(color=LocalWalletColors.current.promo,onClick={open(Editor("simulate"))}) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    TileIcon("bag",Forest,Card)
                    Column(Modifier.weight(1f)) { Text("心动之前，先看一眼",fontSize=15.sp,fontWeight=FontWeight.SemiBold);Caption("一笔消费，会影响哪些计划？") }
                    Glyph("arrow")
                }
            }
        }
        item { Headline("最近的记录","全部记录",ledger) }
        item {
            val entries=s.activeEntries.filter{it.date.startsWith(month)}.sortedByDescending{it.date}.take(4)
            if(entries.isEmpty()) EmptyState("从第一笔开始","记录一笔真实收支，账本就会自动更新。","记一笔"){open(Editor("transaction",kind="EXPENSE"))}
            else CardBox { entries.forEach{e->EntryRow(e,s){open(Editor("detail",e.id))}} }
        }
        item { Caption("现金、预算、还款与储蓄计划分别展示，不合并为“可花金额”。") }
    }
}
@Composable private fun MiniStat(label:String,value:Long,modifier:Modifier) {
    Column(modifier.clip(RoundedCornerShape(13.dp)).background(Page).padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Caption(label);Text("¥ ${Money.format(value)}",fontSize=15.sp,fontWeight=FontWeight.Medium,color=if(value<0) Red else Ink)
    }
}
@Composable private fun SmallPlanCard(title:String,amount:Long,detail:String,icon:String,modifier:Modifier,onClick:()->Unit) {
    CardBox(modifier,onClick=onClick) {
        Glyph(icon);Caption(title);Text("¥ ${Money.format(amount)}",fontSize=20.sp,fontWeight=FontWeight.SemiBold);Caption(detail)
    }
}

@Composable fun LedgerScreen(s: Ledger,month:String,open:(Editor)->Unit) {
    var filter by rememberSaveable{mutableStateOf("全部")};var search by rememberSaveable{mutableStateOf("")};var showVoided by rememberSaveable{mutableStateOf(false)}
    val entries=s.entries.filter{it.date.startsWith(month) && (showVoided || !it.voided) && (filter=="全部" || Kind.valueOf(it.kind).label==filter) &&
        (search.isBlank() || listOf(entryTitle(it,s),it.note,s.accountName(it.accountId)).any{v->v.contains(search,true)})}.sortedWith(compareByDescending<Entry>{it.date}.thenByDescending{it.id})
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=22.dp,end=22.dp,bottom=90.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {
            CardBox(onClick={open(Editor("accounts"))}) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    TileIcon("wallet");Column(Modifier.weight(1f)){Text("我的资金账户",fontWeight=FontWeight.SemiBold);Caption("${s.accounts.size} 个账户 · 余额、修正和启停")};Glyph("arrow")
                }
            }
        }
        item { OutlinedTextField(value=search,onValueChange={search=it},modifier=Modifier.fillMaxWidth(),placeholder={Text("搜索分类、账户或备注",fontSize=13.sp)},singleLine=true,shape=RoundedCornerShape(16.dp)) }
        item { ChoiceRow(listOf("全部","支出","收入","转账","退款","还款","修正"),filter,{it}){filter=it} }
        item {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Caption("${entries.size} 笔记录 · $month")
                Row(verticalAlignment=Alignment.CenterVertically){Checkbox(checked=showVoided,onCheckedChange={showVoided=it},modifier=Modifier.size(30.dp));Caption("显示已撤销")}
            }
        }
        if(entries.isEmpty()) item { EmptyState("没有找到记录","可切换月份或筛选条件，也可以添加一笔新交易。") }
        items(entries,key={it.id}) { e ->
            val motion=LocalDisplayState.current
            CardBox(modifier=Modifier.animateItem(fadeInSpec=androidx.compose.animation.core.tween(motion.duration(180)),placementSpec=androidx.compose.animation.core.tween(motion.duration(220)),fadeOutSpec=androidx.compose.animation.core.tween(motion.duration(120)))) {
                EntryRow(e,s){open(Editor("detail",e.id))};if(e.note.isNotBlank()) Caption(e.note)
            }
        }
    }
}

@Composable fun PlanningScreen(s:Ledger,month:String,section:Int,onSection:(Int)->Unit,open:(Editor)->Unit,vm:WalletViewModel) {
    var categoryFilter by rememberSaveable{mutableStateOf("*")}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=22.dp,end=22.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item { ChoiceRow(listOf(0,1,2),section,{listOf("月度预算","储蓄目标","负债")[it]},onSection) }
        when(section) {
            0 -> {
                val b=s.budget(month)
                item { Headline("$month 预算","${if(b==null) "设置" else "调整"}预算"){open(Editor("budget"))} }
                if(b==null) item { EmptyState("给这个月一份计划","预算由你手动决定，与实际收入和账户余额分别计算。","设置月度预算"){open(Editor("budget"))} }
                else {
                    item { CardBox {
                        Caption("总预算剩余");Text("¥ ${Money.format(b.total-s.usage(month))}",fontSize=32.sp,fontWeight=FontWeight.SemiBold,color=if(s.usage(month)>b.total) Red else Forest)
                        Progress(s.usage(month),b.total);AmountLine("总预算",b.total);AmountLine("实际已花",s.usage(month));AmountLine("未分配预算",b.total-b.necessary-b.free)
                    } }
                    listOf(Triple("日常必需",b.necessary,"NECESSARY"),Triple("可选消费",b.free,"FREE")).forEach { (name,total,bucket) ->
                        item { CardBox {
                            Text(name,fontWeight=FontWeight.SemiBold);AmountLine("计划额度",total);AmountLine("实际已花",s.usage(month,bucket));Progress(s.usage(month,bucket),total)
                            AmountLine("剩余额度",total-s.usage(month,bucket),if(total<s.usage(month,bucket)) Red else Forest)
                        } }
                    }
                    if(b.total>s.cash()) item { Notice("本月预算高于当前总现金，请留意资金支持情况。",true) }
                }
                item { Notice("未分配额度独立保留，不自动补自由或日常必需的超支；预算不自动结转。退款按到账月抵减。分期还款暂未计入日常预算。") }
            }
            1 -> {
                item { Headline("想实现的小目标","新建目标"){open(Editor("goal"))} }
                item { Notice("填写目标金额、预计日期和目前已存金额，自动计算还差多少、每月建议存多少。更新进度不会改变账户余额。") }
                if(s.goals.isEmpty()) item { EmptyState("为想要的生活存一点","电脑、旅行或应急金，都可以先写下目标。","创建目标"){open(Editor("goal"))} }
                items(s.goals.sortedBy{it.archived},key={it.id}) { g ->
                    CardBox(onClick={open(Editor("goal",g.id))}) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) { TileIcon("goal");Pill(if(g.archived)"已收起" else "储蓄计划") }
                        Text(g.name,fontSize=18.sp,fontWeight=FontWeight.SemiBold)
                        AmountLine("目标金额",g.target);SavingsDetails(g)
                        Caption("预计存够 ${g.date} · 点击更新已存金额")
                        if(g.intention>0)Caption("旧版计划预留 ¥${Money.format(g.intention)}，请核对后填写实际已存金额。")
                    }
                }
                val saved=s.goals.filterNot{it.archived}.map{it.saved}.exactSum()
                if(saved>s.cash()) item { Notice("各计划填写的已存合计高于当前现金，请核对进度，避免同一笔钱重复用于多个目标。",true) }
            }
            else -> {
                item { Headline("我的负债","添加负债"){open(Editor("debt"))} }
                item { CardBox {
                    AmountLine("全部剩余待还",s.unpaid().map{it.due}.exactSum(),Forest)
                    TextButton(onClick={open(Editor("debtCategories"))}){Text("管理欠款种类")}
                    val options=listOf(DebtCategory("*","全部种类"),DebtCategory("","未分类"))+s.debtCategories
                    Select("按欠款种类查看",options,options.find{it.id==categoryFilter},{it.name}){categoryFilter=it.id}
                } }
                item { Notice("支持每期相同或逐期设置金额。付款后可用一个或多个账户组合登记，计划本身不会自动扣款。") }
                val debts=s.debts.filter{categoryFilter=="*"||(it.categoryId ?: "")==categoryFilter}
                if(debts.isEmpty()) item { EmptyState("还没有这类负债","添加欠款种类、金额、总期数和首期日期，即可安排还款。","新建负债"){open(Editor("debt"))} }
                items(debts,key={it.id}) { d ->
                    val rows=s.installments.filter{it.debtId==d.id};val unpaid=rows.filter{s.payment(it)==null}
                    CardBox(onClick={open(Editor("debtDetail",d.id))}) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){TileIcon("plan");Pill(if(unpaid.isEmpty()) "已还清" else "${rows.size-unpaid.size} / ${rows.size} 期已还")}
                        Text(d.name,fontSize=18.sp,fontWeight=FontWeight.SemiBold);Caption(s.debtCategoryName(d))
                        AmountLine("应还合计",rows.map{it.due}.exactSum());AmountLine("剩余待还",unpaid.map{it.due}.exactSum())
                        unpaid.minByOrNull{it.date}?.let { Caption("下期 ${it.date} · ¥${Money.format(it.due)}",if(it.date<today().toString()) Red else Muted) }
                        Text("查看负债与逐期还款 ›",fontSize=12.sp,color=Forest)
                    }
                }
            }
        }
    }
}

@Composable fun AnalyticsScreen(s: Ledger,month:String,open:(Editor)->Unit) {
    val entries=s.activeEntries.filter{it.date.startsWith(month)}
    val expense=entries.filter{it.kind=="EXPENSE"}.map{it.amount}.exactSum()
    val refunds=entries.filter{it.kind=="REFUND"}.map{it.amount}.exactSum()
    val repayments=entries.filter{it.kind=="REPAYMENT"}.map{it.amount}.exactSum()
    val categories=entries.filter{it.kind=="EXPENSE"}.groupBy{it.category}.mapValues{(_,v)->v.map{it.amount}.exactSum()}.entries.sortedByDescending{it.value}
    val palette=if(LocalWalletColors.current.dark)
        listOf(Forest,Color(0xFF8FAAD0),Color(0xFFC9BCE1),Color(0xFF92C5D6),Color(0xFFD8B88B),Color(0xFFCDB4C3),Color(0xFFA7B4C6))
    else listOf(Forest,Color(0xFF8CA5C6),Color(0xFFAEA2C5),Color(0xFF78AABD),Color(0xFFC8A779),Color(0xFF988994),Color(0xFF8993A4))
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=22.dp,end=22.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        item { CardBox {
            Caption("实际消费 · 已减去本月收到的退款")
            Text("¥ ${Money.format(expense-refunds)}",fontSize=34.sp,fontWeight=FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) { MiniStat("实际收入",s.income(month),Modifier.weight(1f));MiniStat("收到退款",refunds,Modifier.weight(1f)) }
            Caption("还款单列为现金流，不重复算作日常消费。")
        } }
        item { Headline("消费去向","查看明细"){open(Editor("drill",kind="EXPENSE"))} }
        item { CardBox {
            if(categories.isEmpty()) { Caption("本月还没有日常消费记录。") }
            else {
                Box(Modifier.fillMaxWidth().height(185.dp),contentAlignment=Alignment.Center) {
                    Canvas(Modifier.size(165.dp)) {
                        var start=-90f
                        categories.forEachIndexed { i,c ->
                            val sweep=(c.value.toDouble()/expense*360).toFloat()
                            drawArc(palette[i%palette.size],start,sweep.coerceAtLeast(0f),false,Offset(10.dp.toPx(),10.dp.toPx()),Size(size.width-20.dp.toPx(),size.height-20.dp.toPx()),style=Stroke(18.dp.toPx()))
                            start+=sweep
                        }
                    }
                    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)){Caption("退款前消费");Text("¥ ${Money.format(expense)}",fontSize=18.sp,fontWeight=FontWeight.SemiBold)}
                }
                categories.forEachIndexed { i,c ->
                    Row(Modifier.fillMaxWidth().clickable{open(Editor("drill",id=c.key,kind="EXPENSE"))}.padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(palette[i%palette.size]));Text(c.key,Modifier.weight(1f),fontSize=13.sp)
                        Text("¥ ${Money.format(c.value)}",fontSize=14.sp,fontWeight=FontWeight.Medium)
                    }
                }
            }
        } }
        item { Headline("这个月的钱如何变化") }
        item { CardBox {
            AmountLine("实际到账收入",s.income(month),Forest);AmountLine("日常消费付款",expense);AmountLine("实际到账退款",refunds,Forest);AmountLine("负债实际还款",repayments)
            val adjustments=entries.filter{it.kind=="ADJUSTMENT"}.map{it.amount}.exactSum()
            AmountLine("余额修正差额",adjustments)
            HorizontalDivider(color=Pale)
            AmountLine("本月现金增减",s.income(month)-expense+refunds-repayments+adjustments,Forest)
            val ym=YearMonth.parse(month);val end=minOf(ym.atEndOfMonth(),today()).toString()
            AmountLine("本月新账户的开始余额",s.accounts.filter{it.start.startsWith(month)&&it.start<=end}.map{it.opening}.exactSum())
            AmountLine("月末现金 / 截至今天",s.cash(end))
            Caption("账户之间转账不改变总现金。账户开始余额不是收入，单独纳入资金变化。欠款不算作你拥有的现金。")
        } }
        item { Headline("收入来源","查看收入"){open(Editor("drill",kind="INCOME"))} }
        item { CardBox {
            val sources=entries.filter{it.kind=="INCOME"}.groupBy{it.category}
            if(sources.isEmpty()) Caption("本月还没有实际到账收入。")
            sources.forEach{(category,rows)->AmountLine(category,rows.map{it.amount}.exactSum(),Forest)}
            Caption("学期生活费按实际到账只记录一次，不按规划月份重复增加。")
        } }
        item { CardBox {
            Text("负债与实际还款",fontWeight=FontWeight.SemiBold)
            val newDebtIds=s.debts.filter{it.purchaseDate.startsWith(month)}.map{it.id}.toSet()
            AmountLine("本月登记负债应还合计",s.installments.filter{it.debtId in newDebtIds}.map{it.due}.exactSum())
            val paidRows=entries.filter{it.kind=="REPAYMENT"}.map{it.installmentId}.distinct().map{id->s.installments.first{it.id==id}}
            AmountLine("本月实际还款",repayments)
            val legacyRows=paidRows.filter{it.scheduledAmount==null}
            if(legacyRows.isNotEmpty()){
                AmountLine("旧记录已还借款金额",legacyRows.map{it.principal}.exactSum());AmountLine("旧记录已付利息或费用",legacyRows.map{it.fee}.exactSum())
            }
            if(paidRows.any{it.scheduledAmount!=null})AmountLine("未拆分借款和费用的还款",paidRows.filter{it.scheduledAmount!=null}.map{it.due}.exactSum())
            Caption("自定还款按填写的金额统计，不推算借款本金或利息。多个账户还同一期只统计一次整期金额。还款暂未计入日常预算。")
        } }
    }
}
