package com.campusfinance.wallet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.YearMonth

private val homeCards=listOf(OverviewCard.HOME_SUMMARY,OverviewCard.HOME_PURCHASE,OverviewCard.HOME_BUDGET,OverviewCard.HOME_DEBT,OverviewCard.HOME_SAVINGS)

@Composable internal fun GridMoney(value:Long,modifier:Modifier=Modifier,font:Int=25,color:Color=Ink,align:TextAlign=TextAlign.Start) {
    val text="¥${Money.format(value)}"
    val measurer=rememberTextMeasurer()
    val baseStyle=LocalTextStyle.current
    fun moneyStyle(size:Int)=baseStyle.copy(fontSize=size.sp,lineHeight=(size+3).sp,fontWeight=FontWeight.Medium,textAlign=align)
    BoxWithConstraints(modifier) {
        val maxWidthPx=with(LocalDensity.current){maxWidth.toPx()}
        val sizes=(font downTo 14 step 1).toList().ifEmpty{listOf(14)}
        val selected=sizes.firstOrNull{measurer.measure(text,moneyStyle(it),softWrap=false,constraints=Constraints()).size.width<=maxWidthPx} ?: 14
        val measured=measurer.measure(text,moneyStyle(selected),softWrap=false,constraints=Constraints())
        val display=if(measured.size.width>maxWidthPx)text.replace(",",",\u200B") else text
        Text(display,Modifier.fillMaxWidth().testTag("grid-money").semantics{contentDescription=text},style=moneyStyle(selected),color=color,
            maxLines=2,textAlign=align)
    }
}
@Composable internal fun GridNote(text:String,modifier:Modifier=Modifier,lines:Int=2,color:Color=Muted) {
    Text(text,modifier,fontSize=11.sp,lineHeight=15.sp,color=color,maxLines=lines,overflow=TextOverflow.Ellipsis)
}
@Composable internal fun GridHeading(card:OverviewCard,info:GridCardInfo,title:String=card.title) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(title,Modifier.weight(1f),fontSize=if(info.compact)12.sp else 15.sp,lineHeight=19.sp,color=if(info.compact)Muted else Ink,maxLines=1,overflow=TextOverflow.Ellipsis)
        if(LocalResizingCard.current!=null)Text("${info.layout.columns}×${info.layout.rows}",fontSize=10.sp,color=Forest,maxLines=1)
    }
}
@Composable private fun GridDetails(label:String,value:Long) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        GridNote(label,Modifier.weight(1f));GridMoney(value,Modifier.weight(1.1f),14,align=TextAlign.End)
    }
}
@Composable internal fun GridTile(card:OverviewCard,info:GridCardInfo,tint:Color=Card,click:(()->Unit)?=null,content:@Composable BoxScope.()->Unit) {
    TeaCard(Modifier.fillMaxSize().semantics{stateDescription="${info.layout.columns}×${info.layout.rows}"},tint=tint,onClick=click,padding=if(info.compact)9.dp else 12.dp,spacing=0.dp,card=card) {
        Box(Modifier.fillMaxSize(),content=content)
    }
}
@Composable internal fun BoxScope.GridColumn(info:GridCardInfo,modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Column(modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(if(info.compact)2.dp else 6.dp),content=content)
}

@Composable fun GridTeaHome(s:Ledger,month:String,open:(Editor)->Unit) {
    var summary by rememberSaveable{mutableIntStateOf(0)}
    val purchase by rememberPurchase(s.profile.demo)
    val b=s.budget(month);val used=s.usage(month)
    val pending=s.unpaid().filter{it.date.startsWith(month)}.sortedBy{it.date}
    val goals=s.goals.filterNot{it.archived}
    OverviewGrid(homeCards) { card,info->
        val tall=info.detailed
        val wide=!info.narrow
        when(card) {
            OverviewCard.HOME_SUMMARY->GridTile(card,info,Lime) {GridColumn(info) {
                GridHeading(card,info)
                val text=s.teaSummary(listOf("month","half","year")[summary],YearMonth.parse(month))
                if(!info.compact)TeaTabs(listOf("月度","半年","年度"),summary){summary=it}
                val lines=if(info.compact)2 else if(info.narrow&&!tall)2 else if(tall)5 else 3
                Text(text,fontSize=if(info.compact)12.sp else 13.sp,lineHeight=if(info.compact)17.sp else 20.sp,color=Ink,maxLines=lines,overflow=TextOverflow.Ellipsis)
                if(tall){Spacer(Modifier.weight(1f));GridNote("每一笔记清楚，也给未来留一点余地。",lines=2);TeaArt("steep",Modifier.align(Alignment.End).size(72.dp,45.dp))}
            }}
            OverviewCard.HOME_PURCHASE->GridTile(card,info,click={open(Editor("purchase"))}) {GridColumn(info) {
                GridHeading(card,info,if(info.compact&&purchase!=null)purchase!!.name else card.title)
                if(purchase==null) {
                    Text(if(info.compact)"尚未添加商品" else "还没有心动清单",fontSize=if(info.compact)14.sp else 17.sp,maxLines=if(info.compact)1 else 2)
                    if(!info.compact)GridNote("点开填写商品，先看影响再决定 ›",lines=if(info.narrow)2 else 1)
                } else {
                    if(!info.compact)Text(purchase!!.name,fontSize=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                    GridMoney(purchase!!.price,Modifier.fillMaxWidth(),if(info.compact)21 else 25)
                    if(!info.compact)GridNote(runCatching{s.review(purchase!!).onceAdvice}.getOrDefault("点开重新核对购买影响"),lines=if(info.narrow&&!tall)1 else 3,color=Amber)
                    if(tall){GridDetails("自由消费额度",s.budget(month)?.free ?: 0);GridNote("一次付与分期方案在详情里分别比较。",lines=3)}
                }
                if(tall){Spacer(Modifier.weight(1f));TeaArt("steep",Modifier.align(Alignment.End).size(78.dp,48.dp))}
            }}
            OverviewCard.HOME_BUDGET->GridTile(card,info,click={open(Editor("budgetOverview",kind=month))}) {GridColumn(info) {
                GridHeading(card,info,if(info.compact)"预算${if(b!=null&&used>b.total)"超出" else "剩余"}" else card.title)
                if(b==null){Text(if(info.compact)"先设置预算" else "安排这个月的预算",fontSize=if(info.compact)14.sp else 18.sp,maxLines=2);if(!info.compact)GridNote("必要消费、自由消费各留一份额度",lines=2)}
                else {
                    if(!info.compact&&wide)Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        GridNote(if(used>b.total)"本月超出" else "本月剩余",Modifier.weight(.8f),lines=1)
                        GridMoney(kotlin.math.abs(b.total-used),Modifier.weight(1.2f),27,if(used>b.total)Red else Ink,TextAlign.End)
                    } else {
                        if(!info.compact)GridNote(if(used>b.total)"本月超出" else "本月剩余",lines=1)
                        GridMoney(kotlin.math.abs(b.total-used),Modifier.fillMaxWidth(),if(info.compact)21 else 27,if(used>b.total)Red else Ink)
                    }
                    if(!info.compact){Progress(used,b.total);GridNote("已用 ¥${Money.format(used)}",lines=1)}
                    if(tall||(wide&&!info.compact&&info.height>170.dp)) {
                        GridDetails("必要剩余",b.necessary-s.usage(month,"NECESSARY"))
                        GridDetails("自由剩余",b.free-s.usage(month,"FREE"))
                    }
                    if(tall){GridNote("总预算 ¥${Money.format(b.total)}",lines=2);GridNote("另有 ¥${Money.format(b.total-b.necessary-b.free)} 尚未分配",lines=2)}
                }
            }}
            OverviewCard.HOME_DEBT->GridTile(card,info,click={open(Editor("debtList"))}) {
                val art=tall&&info.height>280.dp
                GridColumn(info,Modifier.padding(bottom=if(art)54.dp else 0.dp)) {
                    GridHeading(card,info);GridMoney(pending.map{it.due}.exactSum(),Modifier.fillMaxWidth(),if(info.compact)21 else 26)
                    if(!info.compact) {
                        if(pending.isEmpty())GridNote("本月没有待还款项")
                        else if(tall)pending.take(if(info.height>330.dp)3 else 2).forEach { p->
                            GridDetails(p.date.substring(5),p.due)
                            if(info.height>320.dp)GridNote(s.debts.firstOrNull{it.id==p.debtId}?.name ?: "还款计划",lines=1)
                        } else GridNote("最近 ${pending.first().date.substring(5)} · 共 ${pending.size} 期待还",lines=2)
                    }
                }
                if(tall)GridNote("还款安排 ›",Modifier.align(Alignment.BottomStart).padding(end=if(art)92.dp else 0.dp),lines=2)
                if(art)TeaArt("pour",Modifier.align(Alignment.BottomEnd).size(88.dp,58.dp).testTag("debt-corner-tea"))
            }
            OverviewCard.HOME_SAVINGS->GridTile(card,info,Lime,click={open(Editor("goalList"))}) {GridColumn(info) {
                GridHeading(card,info);GridMoney(goals.map{it.saved}.exactSum(),Modifier.fillMaxWidth(),if(info.compact)21 else 26)
                if(!info.compact) {
                    if(goals.isEmpty())GridNote("为喜欢的未来设置目标") else GridNote(if(goals.size==1)goals.first().name else "${goals.size} 个储蓄目标",lines=1)
                    if(goals.size==1){val g=goals.first();Progress(g.saved,g.target);if(tall){GridDetails("目标金额",g.target);GridDetails("还差",g.progress().remaining);GridDetails("每月需存",g.progress().monthly)}}
                    else if(tall)goals.take(3).forEach{GridDetails(it.name,it.saved)}
                }
                if(tall){Spacer(Modifier.weight(1f));TeaArt("simmer",Modifier.align(Alignment.End).size(82.dp,54.dp))}
            }}
            else->Unit
        }
    }
}

@Composable fun GridTeaLedger(s:Ledger,month:String,open:(Editor)->Unit) {
    var accounts by rememberSaveable{mutableIntStateOf(0)}
    val cards=listOf(OverviewCard.LEDGER_ASSETS,OverviewCard.LEDGER_FIXED_EXPENSE,OverviewCard.LEDGER_FIXED_INCOME,OverviewCard.LEDGER_MONTH)
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        TeaTitle("我的账本","一壶分几盏，每笔钱各有去处。")
        TeaLink("记账提醒  ${if(s.reminder.enabled)"${s.reminder.startTime}—${s.reminder.endTime}" else "未开启"}"){open(Editor("reminder"))}
        OverviewGrid(cards,Modifier.weight(1f)) { card,info->
            when(card) {
                OverviewCard.LEDGER_ASSETS->GridTile(card,info,LocalWalletColors.current.hero,click={open(Editor("accounts"))}) {GridColumn(info) {
                    GridHeading(card,info)
                    if(!info.compact)TeaTabs(listOf("总金额","各账户"),accounts){accounts=it}
                    if(accounts==0||info.compact){GridMoney(s.cash(),Modifier.fillMaxWidth(),if(info.compact)21 else 30);if(info.detailed){GridNote("${s.accounts.size} 个账户 · 账户余额合计");Spacer(Modifier.weight(1f));TeaArt("share",Modifier.align(Alignment.End).size(100.dp,65.dp))}}
                    else {
                        val shown=s.accounts.take(if(info.narrow&&!info.detailed)1 else 4)
                        if(shown.isEmpty())GridNote("先添加资金账户")
                        if(info.narrow)shown.take(if(info.detailed)3 else 1).forEach{a->Column(Modifier.clickable{open(Editor("account",a.id))}){GridNote(a.name,lines=1);GridMoney(s.balance(a.id),Modifier.fillMaxWidth(),17)}}
                        else shown.chunked(2).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){row.forEach{a->Column(Modifier.weight(1f).clickable{open(Editor("account",a.id))}){GridNote(a.name,lines=1);GridMoney(s.balance(a.id),Modifier.fillMaxWidth(),17)}};if(row.size==1)Spacer(Modifier.weight(1f))}}
                    }
                }}
                OverviewCard.LEDGER_FIXED_EXPENSE,OverviewCard.LEDGER_FIXED_INCOME->{
                    val income=card==OverviewCard.LEDGER_FIXED_INCOME;val kind=if(income)"INCOME" else "EXPENSE";val rules=s.recurringRules.filter{it.kind==kind}
                    GridTile(card,info,if(income)Lime else Pale,click={open(Editor("recurringList",kind=kind))}) {GridColumn(info) {
                        GridHeading(card,info);Text("${rules.size} 项计划 ›",fontSize=if(info.compact)14.sp else 21.sp,maxLines=1)
                        if(!info.compact&&rules.isEmpty())GridNote(if(income)"设置固定到账收入" else "支持组合多个账户扣款",lines=3)
                        if(info.detailed)rules.take(3).forEach{GridDetails(it.name,it.amount);GridNote(if(it.enabled)if(it.frequency=="DAILY")"每天" else "每月 ${it.day} 日" else "已暂停",lines=1)}
                    }}
                }
                OverviewCard.LEDGER_MONTH->GridTile(card,info,click={open(Editor("monthBook",kind=month))}) {GridColumn(info) {
                    GridHeading(card,info,if(info.compact)"本月消费" else card.title)
                    if(info.compact){GridMoney(s.usage(month),Modifier.fillMaxWidth(),21)}
                    else if(info.narrow){GridMoney(s.usage(month),Modifier.fillMaxWidth(),23);GridDetails("收入",s.income(month));if(info.detailed)GridDetails("退款",s.activeEntries.filter{it.kind=="REFUND"&&it.date.startsWith(month)}.map{it.amount}.exactSum())}
                    else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        listOf("收入" to s.income(month),"消费" to s.usage(month),"退款" to s.activeEntries.filter{it.kind=="REFUND"&&it.date.startsWith(month)}.map{it.amount}.exactSum()).forEach{(label,value)->Column(Modifier.weight(1f)){GridNote(label);GridMoney(value,Modifier.fillMaxWidth(),18)}}
                    }
                    if(info.detailed){Spacer(Modifier.weight(1f));GridNote("点击查看本月账本明细 ⌄",lines=2)}
                }}
                else->Unit
            }
        }
    }
}

@Composable fun GridTeaPlanning(s:Ledger,month:String,open:(Editor)->Unit) {
    val g=s.goals.firstOrNull{!it.archived}
    val due=s.unpaid().filter{it.date.startsWith(month)}.sortedBy{it.date}
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        TeaTitle("资金规划","慢煮一壶茶，也慢慢攒到喜欢。")
        OverviewGrid(listOf(OverviewCard.PLAN_SAVINGS,OverviewCard.PLAN_DEBT),Modifier.weight(1f)) {card,info->
            GridTile(card,info,click={open(if(card==OverviewCard.PLAN_DEBT)Editor("debtList") else if(g==null)Editor("goal") else Editor("goal",g.id))}) {GridColumn(info) {
                GridHeading(card,info)
                if(card==OverviewCard.PLAN_SAVINGS) {
                    if(g==null){Text(if(info.compact)"设置目标" else "为喜欢的未来留一点",fontSize=if(info.compact)15.sp else 21.sp,maxLines=2);if(info.detailed){Spacer(Modifier.weight(1f));TeaArt("simmer",Modifier.align(Alignment.End).size(90.dp,62.dp))}}
                    else {
                        if(!info.compact)GridNote(g.name,lines=1)
                        GridMoney(g.saved,Modifier.fillMaxWidth(),if(info.compact)21 else 27)
                        if(!info.compact)Progress(g.saved,g.target)
                        if(info.detailed){GridDetails("目标",g.target);GridDetails("还差",g.progress().remaining);GridDetails("每月需存",g.progress().monthly);GridNote(if(g.progress().overdue)"原定 ${g.date} · 请重新安排日期" else "预计 ${g.date}",lines=2)}
                    }
                    if(!info.compact)TextButton(onClick={open(Editor("goalList"))},contentPadding=PaddingValues(0.dp)){Text("查看全部储蓄计划",fontSize=11.sp,maxLines=2)}
                } else {
                    GridMoney(due.map{it.due}.exactSum(),Modifier.fillMaxWidth(),if(info.compact)21 else 28)
                    if(!info.compact)GridNote("本月待还 · 组合账户安排",lines=2)
                    if(info.detailed)due.take(2).forEach{GridDetails(it.date.substring(5),it.due)}
                    if(info.detailed){Spacer(Modifier.weight(1f));GridNote("查看还款安排 ›")}
                }
            }}
        }
        TeaLink("月度预算","${month} · 必要消费与自由消费分别安排"){open(Editor("budgetOverview",kind=month))}
    }
}

@Composable fun GridTeaAnalytics(s:Ledger,month:String,onMonth:(String)->Unit,open:(Editor)->Unit)=AnalyticsOverview(s,month,onMonth,open)
