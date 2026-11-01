package com.campusfinance.wallet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

data class QuickTarget(val card:OverviewCard?=null,val entryId:String?=null,val kind:String?=null,val debtId:String?=null)
internal data class LedgerQuickAction(val title:String,val editor:Editor?=null,val overlay:Boolean=false,val layout:Boolean=false)
internal class LedgerQuickState {
    var target by mutableStateOf<QuickTarget?>(null)
    var editCard by mutableStateOf<OverviewCard?>(null)
    var anchor by mutableStateOf<Offset?>(null)
    var point by mutableStateOf<Offset?>(null)
    val areas=mutableMapOf<Int,Rect>()
    var execute:((Int)->Unit)?=null
    private var sourceBlockedUntil=0L
    fun sourceClickAllowed()=target==null&&android.os.SystemClock.uptimeMillis()>=sourceBlockedUntil
    fun show(value:QuickTarget,at:Offset?=null){sourceBlockedUntil=android.os.SystemClock.uptimeMillis()+350;areas.clear();target=value;anchor=at;point=null}
    fun dismiss(){sourceBlockedUntil=android.os.SystemClock.uptimeMillis()+350;target=null;point=null;areas.clear()}
    fun release(){point?.let{p->areas.entries.firstOrNull{it.value.contains(p)}?.key?.let{execute?.invoke(it)}};point=null}
}
internal val LocalLedgerQuickState=staticCompositionLocalOf<LedgerQuickState?>{null}
val LocalQuickForm=staticCompositionLocalOf{false}

@Composable fun Modifier.ledgerQuickActions(target:QuickTarget):Modifier {
    val state=LocalLedgerQuickState.current ?: return this
    var bounds by remember{mutableStateOf(Rect.Zero)}
    val current by rememberUpdatedState(target)
    return onGloballyPositioned{bounds=it.boundsInRoot()}.semantics {
        onLongClick("快捷调整"){state.show(current,bounds.center);true}
    }.pointerInput(state){detectDragGesturesAfterLongPress(
        onDragStart={state.show(current,bounds.topLeft+it)},onDragCancel={state.point=null},onDragEnd={state.release()}){change,_->
        change.consume();state.point=bounds.topLeft+change.position
    }}
}

@Composable internal fun LedgerQuickMenu(state:LedgerQuickState,s:Ledger,month:String,onAction:(LedgerQuickAction)->Unit) {
    val target=state.target ?: return
    val actions=remember(target,s.profile.revision,month){buildList {
        val c=target.card
        when {
            target.debtId!=null->{val debt=s.debts.firstOrNull{it.id==target.debtId}
                if(debt!=null){add(LedgerQuickAction(if(debt.archived)"恢复计划" else "删除该计划",Editor(if(debt.archived)"restoreDebt" else "removeDebt",debt.id),true));add(LedgerQuickAction("查看合同与历史还款",Editor("debtDetail",debt.id)))}}
            target.entryId!=null->{val e=s.entries.firstOrNull{it.id==target.entryId}
                if(e!=null&&!e.voided){
                    if(e.kind!="REPAYMENT"&&e.recurringOccurrenceId==null)add(LedgerQuickAction("修改这笔记录",Editor("transaction",e.id,e.kind),true))
                    if(e.kind=="EXPENSE"||e.kind=="REPAYMENT")add(LedgerQuickAction("修改分类与消费性质",Editor("expenseMetadata",e.id),true))
                    add(LedgerQuickAction("查看完整详情",Editor("detail",e.id)))
                }
            }
            c==OverviewCard.HOME_BUDGET->{add(LedgerQuickAction("修改预算额度",Editor("budget",kind=month),true));add(LedgerQuickAction("查看预算分析",Editor("budgetOverview",kind=month)))}
            c==OverviewCard.HOME_SAVINGS||c==OverviewCard.PLAN_SAVINGS->{
                s.goals.firstOrNull{!it.archived}?.let{add(LedgerQuickAction("修改已存金额",Editor("quickSaving",it.id),true))}
                add(LedgerQuickAction("查看储蓄计划",Editor("goalList")))
            }
            c==OverviewCard.LEDGER_FIXED_EXPENSE||c==OverviewCard.LEDGER_FIXED_INCOME->{val kind=if(c==OverviewCard.LEDGER_FIXED_INCOME)"INCOME" else "EXPENSE"
                add(LedgerQuickAction("启停固定收支",Editor("quickRecurring",kind=kind),true));add(LedgerQuickAction("查看固定计划",Editor("recurringList",kind=kind)))}
            c==OverviewCard.HOME_DEBT||c==OverviewCard.PLAN_DEBT->{
                add(LedgerQuickAction("查看还款计划",Editor("debtList")))
                val ids=s.unpaid().filter{it.date.startsWith(month)}.map{it.debtId}.distinct()
                s.debts.filter{!it.archived&&(c==OverviewCard.PLAN_DEBT||it.id in ids)}.take(3).forEach{d->add(LedgerQuickAction("删除计划：${d.name}",Editor("removeDebt",d.id),true))}
            }
            c==OverviewCard.LEDGER_ASSETS->add(LedgerQuickAction("管理资金账户",Editor("accounts")))
            c==OverviewCard.HOME_PURCHASE->add(LedgerQuickAction("修改拟购商品",Editor("purchase")))
            else->add(LedgerQuickAction("查看记账记录",Editor("recordBook",kind=month)))
        }
        if(c!=null)add(LedgerQuickAction("调整布局",layout=true))
    }}
    val run:(Int)->Unit={i->actions.getOrNull(i)?.let{onAction(it)}}
    SideEffect{state.execute=run}
    BackHandler{state.dismiss()}
    BoxWithConstraints(Modifier.fillMaxSize().testTag("quick-action-overlay")) {
        val density=LocalDensity.current
        var height by remember{mutableIntStateOf(200)}
        val w=minOf(maxWidth-32.dp,328.dp)
        val widthPx=with(density){w.toPx()};val margin=with(density){16.dp.toPx()}
        val maxX=with(density){maxWidth.toPx()}-widthPx-margin
        val x=((state.anchor?.x ?: with(density){maxWidth.toPx()/2})-widthPx/2).coerceIn(margin,maxOf(margin,maxX))
        val safeTop=WindowInsets.statusBars.getTop(density)+margin
        val maxY=with(density){maxHeight.toPx()}-height-margin-WindowInsets.navigationBars.getBottom(density)
        val y=((state.anchor?.y ?: maxY+height)-height-margin).coerceIn(safeTop,maxOf(safeTop,maxY))
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha=.16f)).clickable(interactionSource=remember{MutableInteractionSource()},indication=null){state.dismiss()})
        Column(Modifier.offset{IntOffset(x.roundToInt(),y.roundToInt())}.width(w).onSizeChanged{height=it.height}
            .ledgerMaterial(Card,9f).padding(12.dp).semantics{paneTitle="快捷操作"}.testTag("quick-action-menu")) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("快捷操作",Modifier.weight(1f),fontSize=16.sp,fontWeight=FontWeight.Medium);TextButton(onClick={state.dismiss()}){Text("关闭")}}
            actions.forEachIndexed{i,a->
                val highlighted=state.point?.let{state.areas[i]?.contains(it)}==true
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp).onGloballyPositioned{state.areas[i]=it.boundsInRoot()}
                    .ledgerMaterial(if(highlighted)Lime else Card,5f,inset=highlighted)
                    .clickable{run(i)}.padding(horizontal=12.dp,vertical=12.dp).testTag("quick-action-$i")) {
                    Text(a.title,fontSize=14.sp,color=if(highlighted)Forest else Ink)
                }
            }
        }
    }
}

/** Write through the same repository functions as full forms; no parallel financial model. */
@Composable internal fun QuickSavingForm(s:Ledger,id:String,vm:WalletViewModel,busy:Boolean,close:()->Unit) {
    val currentGoal=s.goals.firstOrNull{it.id==id} ?: return
    var saved by rememberSaveable(id){mutableStateOf(Money.plain(currentGoal.saved))}
    Form("修改已存金额",busy,close,submit={val value=Money.parse(saved);vm.execute({goal(currentGoal.copy(saved=value))},close)}) {
        Text(currentGoal.name,fontWeight=FontWeight.Medium)
        Field("目前已存（元）",saved,{saved=it},true)
        Caption("只更新目标进度，不改变账户余额，不重复记收入或扣款。")
    }
}
@Composable internal fun QuickRecurringForm(s:Ledger,kind:String,vm:WalletViewModel,busy:Boolean,close:()->Unit) {
    Form(if(kind=="INCOME")"固定收入启停" else "固定消费启停",busy,close,confirm=null) {
        val rules=s.recurringRules.filter{it.kind==kind}
        if(rules.isEmpty())Caption("尚未设置固定计划。可在账本的固定收支中添加。")
        rules.forEach{r->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Text(r.name,fontSize=15.sp);Caption("¥${Money.format(r.amount)} / ${if(r.frequency=="DAILY")"每天" else "每月"}")}
            Switch(r.enabled,{enabled->vm.execute({recurring(r.copy(enabled=enabled),s.recurringAllocations.filter{it.ruleId==r.id})})},enabled=!busy,modifier=Modifier.testTag("quick-recurring-${r.id}"))
        }}
        Caption("切换立即保存；已生成的账目和历史期次保持不变。")
    }
}

@Composable internal fun DebtRemovalForm(s:Ledger,id:String,remove:Boolean,vm:WalletViewModel,busy:Boolean,close:()->Unit){
    val d=s.debts.firstOrNull{it.id==id} ?: return
    val rows=s.installments.filter{it.debtId==id};val count=rows.count{s.payments(it).isNotEmpty()}
    Form(if(remove)"删除负债计划" else "恢复负债计划",busy,close,confirm=if(remove)"确认移除计划" else "确认恢复计划",submit={vm.execute({archiveDebt(id,remove)},close)}){
        Text(d.name,fontSize=19.sp,fontWeight=FontWeight.Medium)
        Caption("共 ${rows.size} 期，已有 $count 期还款记录。")
        Notice(if(remove)"将从计划列表和待还统计中移除；合同、期次和所有历史还款保留，账户余额与消费统计不变。可在“已移除计划”中恢复。" else "恢复后，尚未还款的期次会重新计入待还；不会生成、重复记账或扣款。")
        Caption("这项操作只管理本地账本，不能取消真实借款合同或实际还款义务。")
    }
}
