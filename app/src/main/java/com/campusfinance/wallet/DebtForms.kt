package com.campusfinance.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun DebtCategoriesForm(s:Ledger,vm:WalletViewModel,busy:Boolean,close:()->Unit,open:(Editor)->Unit) {
    Form("欠款种类",busy,close,confirm="添加欠款种类",submit={open(Editor("debtCategory"))}) {
        Caption("可自己添加、改名或停用。停用后，已有负债仍保留原种类。")
        s.debtCategories.sortedBy{it.archived}.forEach { c ->
            CardBox(color=Page) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                    Text(c.name,fontWeight=FontWeight.Medium,modifier=Modifier.weight(1f));Pill(if(c.archived)"已停用" else "使用中")
                }
                Row {
                    TextButton(onClick={open(Editor("debtCategory",c.id))},enabled=!busy,modifier=Modifier.testTag("debtCategoryEdit-${c.id}")){Text("修改名称")}
                    TextButton(onClick={vm.execute({debtCategory(c.copy(archived=!c.archived))})},enabled=!busy,modifier=Modifier.testTag("debtCategoryToggle-${c.id}")){Text(if(c.archived)"重新启用" else "停用种类")}
                }
            }
        }
    }
}

@Composable fun DebtCategoryForm(old:DebtCategory?,vm:WalletViewModel,busy:Boolean,close:()->Unit) {
    var name by rememberSaveable{mutableStateOf(old?.name ?: "")}
    Form(if(old==null)"添加欠款种类" else "修改欠款种类",busy,close,submit={
        val c=DebtCategory(old?.id ?: newId(),name.trim(),old?.archived ?: false)
        vm.execute({debtCategory(c)},close)
    }){Field("种类名称",name,{name=it});Caption("例如：助学贷款、朋友借款、花呗、信用卡。名称不能重复。")}
}

@Composable private fun DebtCategoryPicker(s:Ledger,selected:String?,onSelect:(String)->Unit,vm:WalletViewModel,busy:Boolean) {
    var adding by rememberSaveable{mutableStateOf(false)}
    var name by rememberSaveable{mutableStateOf("")}
    val available=s.debtCategories.filter{!it.archived||it.id==selected}
    Select("欠款种类",available,available.find{it.id==selected},{it.name+if(it.archived)"（已停用）" else ""}){onSelect(it.id)}
    TextButton(onClick={adding=true},enabled=!busy){Text("＋ 自定义欠款种类")}
    if(adding)AlertDialog(onDismissRequest={if(!busy)adding=false},title={Text("添加欠款种类")},text={Field("种类名称",name,{name=it})},
        confirmButton={TextButton(enabled=!busy&&name.isNotBlank(),onClick={
            val c=DebtCategory(name=name.trim())
            vm.execute({debtCategory(c)}){onSelect(c.id);adding=false;name=""}
        }){Text("添加并选用")}},dismissButton={TextButton(enabled=!busy,onClick={adding=false}){Text("取消")}})
}

@Composable fun DebtForm(s:Ledger,old:Debt?,vm:WalletViewModel,busy:Boolean,close:()->Unit) {
    val existing=s.installments.filter{it.debtId==old?.id}.sortedBy{it.number}
    var name by rememberSaveable{mutableStateOf(old?.name ?: "")}
    var category by rememberSaveable{mutableStateOf(old?.categoryId ?: s.debtCategories.firstOrNull{it.id=="debt-kind-other"&&!it.archived}?.id ?: s.debtCategories.firstOrNull{!it.archived}?.id)}
    var note by rememberSaveable{mutableStateOf(old?.note ?: "")}
    if(old?.scheduleType=="LEGACY") {
        Form("更新负债",busy,close,submit={val d=old.copy(name=name.trim(),categoryId=category,note=note);vm.execute({debt(d,existing)},close)}) {
            Field("负债名称",name,{name=it});DebtCategoryPicker(s,category,{category=it},vm,busy);Field("备注（可选）",note,{note=it})
            AmountLine("应还合计",existing.map{it.due}.exactSum())
            Notice("这是旧版记录，原借款金额、费用、期数和还款历史均保留。这里可修改名称、欠款种类和备注。")
        }
        return
    }
    val id=rememberSaveable{old?.id ?: newId()}
    var occurred by rememberSaveable{mutableStateOf(old?.purchaseDate ?: today().toString())}
    var firstDate by rememberSaveable{mutableStateOf(existing.firstOrNull()?.date ?: today().plusMonths(1).toString())}
    var countText by rememberSaveable{mutableStateOf(if(existing.isEmpty())"3" else existing.size.toString())}
    var type by rememberSaveable{mutableStateOf(old?.scheduleType ?: "EQUAL")}
    var equalAmount by rememberSaveable{mutableStateOf(existing.firstOrNull()?.due?.let{Money.plain(it)} ?: "")}
    var amounts by rememberSaveable{mutableStateOf<List<String>>(ArrayList(existing.map{Money.plain(it.due)}.ifEmpty{List(3){""}}))}
    var page by rememberSaveable{mutableIntStateOf(0)}
    val count=countText.toIntOrNull()?.takeIf{it in 1..MAX_DEBT_INSTALLMENTS}
    val locked=existing.filter{row->s.entries.any{it.installmentId==row.id}}.map{it.id}.toSet()
    fun build():List<Installment> {
        val n=count ?: error("还款总期数须为 1–$MAX_DEBT_INSTALLMENTS 期")
        val values=if(type=="EQUAL")List(n){Money.parse(equalAmount)} else List(n){Money.parse(amounts.getOrElse(it){""})}
        return manualDebtSchedule(id,firstDate,values,existing)
    }
    val preview=runCatching{build()}.getOrNull()
    Form(if(old==null)"添加负债" else "更新负债",busy,close,submit={
        val rows=build()
        val d=Debt(id=id,name=name.trim(),principal=0,purchaseDate=occurred,note=note,categoryId=category,scheduleType=type)
        vm.execute({debt(d,rows)},close)
    }) {
        Field("负债名称",name,{name=it});DebtCategoryPicker(s,category,{category=it},vm,busy)
        DateInput("欠款发生日 YYYY-MM-DD",occurred){occurred=it}
        Field("还款总期数（1–$MAX_DEBT_INSTALLMENTS）",countText,{value->
            countText=value
            value.toIntOrNull()?.takeIf{it in 1..MAX_DEBT_INSTALLMENTS}?.let{n->if(n>amounts.size)amounts=ArrayList(amounts+List(n-amounts.size){equalAmount})}
        },money=true)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf(10,20,30).forEach{years->FilterChip(selected=count==years*12,onClick={
                val n=years*12;countText=n.toString();page=0
                if(n>amounts.size)amounts=ArrayList(amounts+List(n-amounts.size){equalAmount})
            },label={Text("${years}年",fontSize=12.sp)})}
        }
        Caption("每月一期，120期为10年、240期为20年、360期为30年；请按实际合同设置。")
        if(locked.isEmpty())DateInput("首期还款日期 YYYY-MM-DD",firstDate){firstDate=it}
        else Caption("首期日期 $firstDate；已有还款历史，日期固定，未还金额可调整。")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("EQUAL","CUSTOM").forEach{next->
                FilterChip(selected=type==next,onClick={
                    if(next=="CUSTOM")amounts=ArrayList(List(maxOf(amounts.size,count ?: 0)){index->amounts.getOrElse(index){""}.ifBlank{equalAmount}})
                    type=next
                },label={Text(DebtSchedule.valueOf(next).label,fontSize=12.sp)})
            }
        }
        if(type=="EQUAL") {
            if(locked.isEmpty())Field("每月还款金额（元）",equalAmount,{equalAmount=it},money=true)
            else {AmountLine("每期金额",existing.first().due);Caption("要调整后续金额，请选择“逐期设置”；已登记过还款的金额不能改。")}
        } else if(count!=null) {
            val actualPage=page.coerceIn(0,(count-1)/12)
            Caption("分别设置每期金额；日期按首期日期逐月安排。第 ${actualPage+1} / ${(count+11)/12} 页")
            (actualPage*12 until minOf((actualPage+1)*12,count)).forEach{index->
                val row=existing.getOrNull(index)
                val dueDate=runCatching{java.time.LocalDate.parse(firstDate).plusMonths(index.toLong()).toString()}.getOrDefault("请检查首期日期")
                if(row?.id in locked){AmountLine("第 ${index+1} 期 · $dueDate",row!!.due);Caption("曾登记过还款，金额保持不变。")}
                else Field("第 ${index+1} 期金额（元） · $dueDate",amounts.getOrElse(index){""},{value->
                    amounts=ArrayList(List(maxOf(amounts.size,index+1)){i->if(i==index)value else amounts.getOrElse(i){""}})
                },money=true,modifier=Modifier.testTag("debtAmount${index+1}"))
            }
            if(count>12)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                TextButton(enabled=actualPage>0,onClick={page=actualPage-1}){Text("上一组期数")}
                TextButton(enabled=actualPage<(count-1)/12,onClick={page=actualPage+1}){Text("下一组期数")}
                TextButton(enabled=actualPage<(count-1)/12,onClick={page=(count-1)/12},modifier=Modifier.testTag("debt-edit-last-group")){Text("末组")}
            }
        }
        if(preview!=null)CardBox(color=Page){
            AmountLine("应还合计",preview.map{it.due}.exactSum(),Forest)
            Caption("共 ${preview.size} 期 · 首期 ${preview.first().date} · 最后一期 ${preview.last().date}")
        }else Caption("填完期数、日期和每期金额后，会自动汇总应还总额。")
        Field("备注（可选）",note,{note=it})
        Notice("填写每期实际应还的总金额，含需要支付的利息或手续费。不自动计算利率，也不会在到期日自动扣款；实际支付后再选择付款账户登记还款。")
    }
}

@Composable fun DebtDetail(s:Ledger,d:Debt,busy:Boolean,close:()->Unit,open:(Editor)->Unit) {
    val rows=s.installments.filter{it.debtId==d.id}.sortedBy{it.number}
    val unpaid=rows.filter{s.payments(it).isEmpty()}
    var page by rememberSaveable{mutableIntStateOf(0)}
    val actualPage=page.coerceIn(0,(rows.size-1).coerceAtLeast(0)/12)
    Form("负债 · ${d.name}",busy,close,confirm=null) {
        Pill(s.debtCategoryName(d));AmountLine("应还合计",rows.map{it.due}.exactSum());AmountLine("剩余待还",unpaid.map{it.due}.exactSum(),Forest)
        Caption("${rows.size-unpaid.size} / ${rows.size} 期已还 · 欠款发生日 ${d.purchaseDate}")
        if(d.note.isNotBlank())Caption(d.note)
        OutlinedButton(onClick={open(Editor("debt",d.id))},modifier=Modifier.fillMaxWidth()){Text("修改负债与还款计划")}
        Caption("还款只扣减所选账户的账本余额，不会重复记为日常消费。")
        rows.drop(actualPage*12).take(12).forEach { i ->
            val payments=s.payments(i)
            CardBox(color=Page) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("第 ${i.number} 期",fontWeight=FontWeight.SemiBold);Pill(if(payments.isNotEmpty())"已还" else if(i.date<today().toString())"已过还款日" else "待还",if(payments.isEmpty()&&i.date<today().toString())Red else Forest)}
                Caption("还款日期 ${i.date}");AmountLine("当期应还",i.due)
                if(i.scheduledAmount==null){AmountLine("原借款金额",i.principal);AmountLine("原记录利息或费用",i.fee)}
                if(payments.isNotEmpty()) {
                    Caption("${payments.first().date} 实际还款${if(payments.size>1) " · ${payments.size} 个账户组合" else ""}")
                    payments.forEach{p->TextButton(onClick={open(Editor("detail",p.id))}){Text("${s.accountName(p.accountId)} ¥${Money.format(p.amount)} · 查看记录")}}
                } else OutlinedButton(onClick={open(Editor("repay",i.id))},modifier=Modifier.testTag("repay${i.number}")){Text("登记还款")}
            }
        }
        if(rows.size>12)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            TextButton(enabled=actualPage>0,onClick={page=actualPage-1}){Text("上一组期数")}
            Caption("${actualPage+1} / ${(rows.size+11)/12}")
            TextButton(enabled=actualPage<(rows.size-1)/12,onClick={page=actualPage+1}){Text("下一组期数")}
            TextButton(enabled=actualPage<(rows.size-1)/12,onClick={page=(rows.size-1)/12},modifier=Modifier.testTag("debt-detail-last-group")){Text("末组")}
        }
    }
}

@Composable fun RepayForm(s:Ledger,i:Installment,vm:WalletViewModel,busy:Boolean,close:()->Unit) {
    val first=s.accounts.firstOrNull{it.disabledOn==null}?.id
    var selected by rememberSaveable{mutableStateOf<List<String>>(ArrayList(listOfNotNull(first)))}
    var values by rememberSaveable{mutableStateOf<Map<String,String>>(hashMapOf<String,String>().apply{if(first!=null)put(first,Money.plain(i.due))})}
    var date by rememberSaveable{mutableStateOf(today().toString())}
    val total=runCatching{selected.map{Money.parse(values[it] ?: "")}.exactSum()}.getOrNull()
    Form("登记第 ${i.number} 期还款",busy,close,confirm="确认记录还款",submit={
        val allocations=selected.map{it to Money.parse(values[it] ?: "")}
        vm.execute({repay(i,allocations,date)},close)
    }) {
        AmountLine("当期应还",i.due,Forest);DateInput("实际付款日 YYYY-MM-DD",date){date=it}
        Text("从哪些账户扣款？",fontWeight=FontWeight.SemiBold)
        Caption("勾选一个或多个账户，分别填写本次已支付的金额。")
        if(s.accounts.isEmpty())Notice("请先添加付款账户。",true)
        s.accounts.forEach{a->
            val allowed=a.disabledOn==null||date<a.disabledOn
            CardBox(color=Page) {
                Row(verticalAlignment=Alignment.CenterVertically){
                    Checkbox(checked=a.id in selected,enabled=allowed&&!busy,onCheckedChange={checked->
                        selected=ArrayList(if(checked)selected+a.id else selected-a.id)
                        if(checked&&!values.containsKey(a.id))values=HashMap(values).apply{put(a.id,"")}
                    },modifier=Modifier.testTag("repayAccount-${a.id}"))
                    Column(Modifier.weight(1f)){Text(a.name,fontWeight=FontWeight.Medium);Caption("当前余额 ¥${Money.format(s.balance(a.id))}${if(!allowed) " · 已停用" else ""}")}
                }
                if(a.id in selected){
                    Field("${a.name}扣款（元）",values[a.id] ?: "",{v->values=HashMap(values).apply{put(a.id,v)}},money=true,modifier=Modifier.testTag("repayAmount-${a.id}"))
                    runCatching{Money.parse(values[a.id] ?: "")}.getOrNull()?.let{n->if(n>s.balance(a.id))Caption("按当前余额，本次扣款后将小于零，请核对实际支付情况。",Red)}
                }
            }
        }
        if(total!=null){
            AmountLine("各账户扣款合计",total,if(total==i.due)Forest else Red)
            if(total<i.due)AmountLine("还差",i.due-total,Red) else if(total>i.due)AmountLine("超出",total-i.due,Red)
        }
        Notice("合计必须等于当期应还金额。确认后各账户账本余额一起扣减；撤销也会整笔恢复。这只是记录已发生的付款，不会实际操作银行或支付账户。")
    }
}
