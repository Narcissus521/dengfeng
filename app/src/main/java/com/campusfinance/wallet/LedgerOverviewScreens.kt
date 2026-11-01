package com.campusfinance.wallet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import java.time.YearMonth

internal val LocalQuickEditor=staticCompositionLocalOf<((Editor)->Unit)?>{null}

@Composable private fun LedgerSection(title:String,action:String?=null,click:()->Unit={}) {
    Row(Modifier.fillMaxWidth().padding(top=6.dp),verticalAlignment=Alignment.CenterVertically){
        Text(title,Modifier.weight(1f),fontSize=15.sp,fontWeight=FontWeight.SemiBold,color=Ink)
        if(action!=null)TextButton(onClick=click,contentPadding=PaddingValues(horizontal=0.dp)){Text(action,fontSize=11.sp,color=Muted)}
    }
}

/** Standard home is a real ledger page. Existing custom-grid renderer remains available in settings. */
@Composable internal fun LedgerHome(s:Ledger,month:String,open:(Editor)->Unit) {
    val budget=s.budget(month);val used=s.usage(month)
    val quick=LocalQuickEditor.current
    val quickState=LocalLedgerQuickState.current
    val goals=s.goals.filterNot{it.archived};val pending=s.unpaid().filter{it.date.startsWith(month)}
    val latest=remember(s.profile.revision,month){s.activeEntries.asReversed().filter{it.date.startsWith(month)}.sortedByDescending{it.date}.take(2)}
    val purchase by rememberPurchase(s.profile.demo)
    var summary by rememberSaveable{mutableIntStateOf(0)}
    val ym=YearMonth.parse(month)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("ledger-home-scroll"),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("${ym.year} 年 ${ym.monthValue} 月",Modifier.fillMaxWidth().padding(top=10.dp,bottom=6.dp),fontSize=14.sp,color=Muted,textAlign=TextAlign.Center)
        TeaCard(card=OverviewCard.HOME_BUDGET,padding=16.dp,spacing=12.dp,onClick={open(Editor("budgetOverview",kind=month))}) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Text("${ym.monthValue}月",Modifier.ledgerMaterial(Forest,2f,button=true).padding(horizontal=6.dp,vertical=7.dp),fontSize=10.sp,color=MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(10.dp));Text(if(budget==null)"本月预算" else if(used>budget.total)"本月预算超出" else "本月预算剩余",Modifier.weight(1f),fontSize=12.sp,color=Muted)
                TextButton(onClick={val e=Editor("budget",kind=month);if(quick!=null)quick(e) else open(e)},contentPadding=PaddingValues(0.dp),modifier=Modifier.height(36.dp).testTag("home-adjust-budget")){Text("调整预算",fontSize=11.sp)}
            }
            if(budget==null){
                Text("先安排这个月的预算",fontSize=23.sp,fontWeight=FontWeight.Medium,color=Ink)
                TeaSmall("必要消费与非必要消费分别安排，未分配额度单独保留。")
            }else{
                GridMoney(kotlin.math.abs(budget.total-used),Modifier.testTag("home-budget-remaining"),44,if(used>budget.total)Red else Ink,TextAlign.Center)
                Progress(used,budget.total)
                HorizontalDivider(color=MaterialEdge)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){TeaSmall("本月已花");GridMoney(used,font=17,align=TextAlign.Center)}
                    Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally){TeaSmall("预算总额");GridMoney(budget.total,font=17,align=TextAlign.Center)}
                }
            }
        }
        LedgerSection("资金安排","还款与储蓄"){open(Editor("goalList"))}
        Row(Modifier.fillMaxWidth().ledgerMaterial(LocalWalletColors.current.hero,8f,inset=true).padding(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).ledgerQuickActions(QuickTarget(OverviewCard.HOME_DEBT)).clickable{if(quickState?.sourceClickAllowed()!=false)open(Editor("debtList"))}.padding(4.dp)){
                TeaSmall("本月待还");GridMoney(pending.map{it.due}.exactSum(),font=24);TeaSmall("查看还款计划 ›")
            }
            Column(Modifier.weight(1f).ledgerQuickActions(QuickTarget(OverviewCard.HOME_SAVINGS)).clickable{if(quickState?.sourceClickAllowed()!=false)open(Editor("goalList"))}.padding(4.dp)){
                TeaSmall("储蓄已存");GridMoney(goals.map{it.saved}.exactSum(),font=24);TeaSmall(if(goals.size==1)goals.first().name else "${goals.size} 个储蓄目标 ›")
            }
        }
        LedgerSection("最近记录","全部记录"){open(Editor("recordBook",kind=month))}
        TeaCard(padding=12.dp,spacing=0.dp){
            Row(Modifier.fillMaxWidth().padding(bottom=8.dp)){TeaSmall("收支项目",Modifier.weight(1f));TeaSmall("金额 / 元")}
            HorizontalDivider(color=MaterialEdge)
            if(latest.isEmpty())TeaSmall("本月还没有记录，点底部记账键开始。",Modifier.padding(vertical=16.dp))
            latest.forEachIndexed{i,e->
                Box(Modifier.ledgerQuickActions(QuickTarget(entryId=e.id)).testTag("home-record-${e.id}")){EntryRow(e,s){open(Editor("detail",e.id))}}
                if(i<latest.lastIndex)HorizontalDivider(color=MaterialEdge)
            }
        }
        TeaCard(card=OverviewCard.HOME_SUMMARY,padding=12.dp,spacing=8.dp){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("收支小结",Modifier.weight(1f),fontSize=14.sp,fontWeight=FontWeight.Medium);TextButton(onClick={summary=(summary+1)%3},modifier=Modifier.testTag("summary-period")){Text(listOf("月度","半年","年度")[summary],fontSize=12.sp)}}
            val text=remember(s.profile.revision,month,summary){s.teaSummary(listOf("month","half","year")[summary],ym).replace("像每日一盏茶，慢慢养成记账习惯。","从第一笔开始，慢慢养成记账习惯。")}
            Text(text,fontSize=13.sp,lineHeight=20.sp,color=Muted)
        }
        TeaCard(card=OverviewCard.HOME_PURCHASE,padding=12.dp,onClick={open(Editor("purchase"))}){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("买前想想",Modifier.weight(1f),fontSize=14.sp,fontWeight=FontWeight.Medium);Glyph("arrow",Muted,Modifier.size(16.dp))}
            if(purchase==null)TeaSmall("填好商品与价格，再看一次付清和分期的影响。")
            else{Text(purchase!!.name,fontSize=14.sp);GridMoney(purchase!!.price,font=21);TeaSmall(runCatching{s.review(purchase!!).onceAdvice}.getOrDefault("点击核对购买影响"))}
        }
        Spacer(Modifier.height(12.dp))
    }
}
