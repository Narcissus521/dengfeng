package com.campusfinance.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.ZonedDateTime

@Composable fun PendingRecurringForm(s:Ledger,busy:Boolean,close:()->Unit,open:(Editor)->Unit,kind:String?=null) {
    val pending=pendingRecurring(s,ZonedDateTime.now(financeZone)).filter{kind==null||it.first.kind==kind}
    var page by rememberSaveable{mutableIntStateOf(0)}
    val current=page.coerceIn(0,(pending.size-1).coerceAtLeast(0)/12)
    Form("待确认固定收支",busy,close,confirm=null) {
        Caption("未确认不会改变余额。修改只用于本次，后续计划保持原样。关闭页面后仍可回来处理。")
        if(pending.isEmpty())TeaCard{Text("没有待确认的固定收支")}
        pending.drop(current*12).take(12).forEach{(rule,date)->
            TeaCard(onClick={open(Editor("confirmRecurring",rule.id,date.toString()))},modifier=Modifier.testTag("pending-${rule.id}@$date")) {
                Text(rule.name);AmountLine(Kind.valueOf(rule.kind).label,rule.amount)
                Caption("计划日期 $date · 点按核对本次金额与账户")
            }
        }
        if(pending.size>12)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            TextButton(enabled=current>0,onClick={page=current-1}){Text("上一组")}
            Caption("${current+1} / ${(pending.size+11)/12}")
            TextButton(enabled=(current+1)*12<pending.size,onClick={page=current+1}){Text("下一组")}
        }
        if(pending.size==400)Caption("本批最多显示400笔，处理后会继续列出后续到期期次。")
    }
}

@Composable fun ConfirmRecurringForm(s:Ledger,ruleId:String,scheduledDate:String,vm:WalletViewModel,busy:Boolean,close:()->Unit) {
    val rule=s.recurringRules.firstOrNull{it.id==ruleId} ?: return
    val original=s.recurringAllocations.filter{it.ruleId==ruleId}.map{it.accountId to it.amount}
    var amount by rememberSaveable(ruleId,scheduledDate){mutableStateOf(Money.plain(rule.amount))}
    var date by rememberSaveable(ruleId,scheduledDate){mutableStateOf(scheduledDate)}
    var selected by rememberSaveable(ruleId,scheduledDate){mutableStateOf<List<String>>(ArrayList(original.map{it.first}))}
    var values by rememberSaveable(ruleId,scheduledDate){mutableStateOf<Map<String,String>>(HashMap(original.associate{it.first to Money.plain(it.second)}))}
    val handled=s.recurringOccurrences.any{it.id=="$ruleId@$scheduledDate"}
    Form(if(handled)"本次已处理" else "确认本次${Kind.valueOf(rule.kind).label}",busy,close,confirm=if(handled)null else "确认记账",submit={
        val splits=selected.map{it to Money.parse(values[it] ?: "")}
        vm.execute({confirmRecurring(ruleId,scheduledDate,date,Money.parse(amount),splits)},close)
    }) {
        Text(rule.name);Caption("原计划日期 $scheduledDate · 原计划金额 ¥${Money.format(rule.amount)}")
        Caption(if(rule.kind=="INCOME")"收入来源：${rule.category}" else "${ExpenseTaxonomy.resolve(rule.categoryGroupId,rule.subcategoryId,rule.category).label} · ${Necessity.valueOf(rule.necessity).label}")
        if(handled)Caption("这期已确认、撤销或跳过，不会再次生成流水。")
        else {
            Field("本次金额（元）",amount,{value->
                amount=value
                runCatching{Money.parse(value)}.getOrNull()?.takeIf{it>0&&selected.isNotEmpty()}?.let{total->
                    val weights=selected.map{account->account to (original.firstOrNull{it.first==account}?.second ?: runCatching{Money.parse(values[account] ?: "")}.getOrDefault(1L).coerceAtLeast(1L))}
                    val allocated=proportionalAllocation(total,weights)
                    values=HashMap(allocated.associate{it.first to Money.plain(it.second)})
                    selected=ArrayList(allocated.map{it.first})
                }
            },money=true,modifier=Modifier.testTag("confirm-recurring-amount"))
            DateInput("实际发生日期 YYYY-MM-DD",date){date=it}
            if(rule.kind=="INCOME")Select("到账账户",s.accounts,s.accounts.find{it.id==selected.firstOrNull()},{it.name}){a->selected=arrayListOf(a.id);values=hashMapOf(a.id to amount)}
            else s.accounts.forEach{account->TeaCard {
                Row {
                    Checkbox(account.id in selected,{checked->selected=ArrayList(if(checked)selected+account.id else selected-account.id)},enabled=!busy,modifier=Modifier.testTag("confirm-account-${account.id}"))
                    Text(account.name)
                }
                if(account.id in selected)Field("${account.name}本次付款（元）",values[account.id] ?: "",{v->values=HashMap(values).apply{put(account.id,v)}},money=true,modifier=Modifier.testTag("confirm-share-${account.id}"))
            }}
            if(rule.kind=="EXPENSE")Caption("修改总额后先按原比例分配，可逐项修改；账户合计正确后才会记账。")
            runCatching{selected.map{Money.parse(values[it] ?: "")}.exactSum()}.getOrNull()?.let{AmountLine("账户合计",it)}
            TextButton(onClick={vm.execute({skipRecurring(ruleId,scheduledDate)},close)},enabled=!busy,modifier=Modifier.testTag("skip-recurring")){Text("跳过本次，不再询问这一期")}
        }
    }
}
