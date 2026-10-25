package com.campusfinance.wallet

import android.app.DatePickerDialog
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun Form(title:String,busy:Boolean=false,close:()->Unit,confirm:String?="保存",submit:()->Unit={},content:@Composable ColumnScope.()->Unit) {
    PagedForm(title,busy,close,confirm,submit,content)
}
@Composable fun Field(label:String,value:String,onChange:(String)->Unit,money:Boolean=false,multi:Boolean=false,modifier:Modifier=Modifier) {
    OutlinedTextField(value=value,onValueChange={if(it.length<=5000)onChange(it)},label={Text(label,fontSize=12.sp)},modifier=modifier.fillMaxWidth(),
        singleLine=!multi,minLines=if(multi) 3 else 1,keyboardOptions=KeyboardOptions(keyboardType=if(money) KeyboardType.Decimal else KeyboardType.Text),shape=RoundedCornerShape(12.dp))
}
@Composable fun DateInput(label:String,value:String,onChange:(String)->Unit) {
    val context=LocalContext.current
    val dark=LocalWalletColors.current.dark
    Row(verticalAlignment=Alignment.CenterVertically) {
        Field(label,value,onChange,modifier=Modifier.weight(1f))
        IconButton(onClick={
            val date=runCatching{LocalDate.parse(value)}.getOrDefault(today())
            DatePickerDialog(context,if(dark)R.style.Theme_Wallet_DatePicker_Dark else R.style.Theme_Wallet_DatePicker_Light,
                {_,y,m,d->onChange(LocalDate.of(y,m+1,d).toString())},date.year,date.monthValue-1,date.dayOfMonth).show()
        },modifier=Modifier.testTag("date-picker-$label")) { Glyph("plan") }
    }
}
@Composable fun <T> Select(label:String,items:List<T>,selected:T?,name:(T)->String,onChange:(T)->Unit) {
    var expanded by remember{mutableStateOf(false)}
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Caption(label)
        Box {
            OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),contentPadding=PaddingValues(15.dp)) {
                Text(selected?.let(name) ?: "请选择",modifier=Modifier.weight(1f),color=Ink);Text("⌄",color=Muted)
            }
            DropdownMenu(expanded=expanded,onDismissRequest={expanded=false},modifier=Modifier.heightIn(max=260.dp)) {
                items.forEach{v->DropdownMenuItem(text={Text(name(v),fontSize=13.sp)},onClick={onChange(v);expanded=false})}
            }
        }
    }
}
@Composable fun EditorHost(e:Editor,s:Ledger,month:String,vm:WalletViewModel,busy:Boolean,close:()->Unit,open:(Editor)->Unit,export:()->Unit,restore:()->Unit,onSwitch:()->Unit) {
    key(e) {
        when(e.type) {
            "recurringList" -> RecurringList(s,e.kind ?: "EXPENSE",busy,close,open)
            "recurringDetail" -> RecurringDetail(s,e.id!!,vm,busy,close,open)
            "recurring" -> RecurringForm(s,s.recurringRules.find{it.id==e.id},e.kind ?: s.recurringRules.find{it.id==e.id}?.kind ?: "EXPENSE",vm,busy,close)
            "reminder" -> ReminderForm(s,vm,busy,close)
            "purchase" -> PurchaseForm(s,close,open)
            "purchaseResult" -> PurchaseResult(s,close,open)
            "purchaseImpact" -> PurchaseResult(s,close,open,e.kind)
            "monthBook" -> MonthBookForm(s,e.kind ?: month,close,open)
            "categoryRecords" -> MonthBookForm(s,e.kind!!.substringAfter('|'),close,open,e.id,e.kind.substringBefore('|'))
            "analysisCategories" -> AnalysisCategoriesForm(s,e.id ?: month,e.kind ?: "month",close,open)
            "analysisBudget" -> AnalysisBudgetForm(s,e.id ?: month,e.kind ?: "month",close,open)
            "analysisHub" -> AdvancedAnalysisForm(s,e.id ?: month,e.kind ?: "month",close,open)
            "analysisAlert" -> AlertDetailForm(s,e.kind!!,close,open)
            "analysisRecords" -> AnalysisRecordsForm(s,ReportFilter.decode(e.kind!!),close,open)
            "categoryBudget" -> CategoryBudgetForm(s,e.kind ?: month,vm,busy,close,open)
            "expenseMetadata" -> ExpenseMetadataForm(s,s.entries.first{it.id==e.id},vm,busy,close)
            "budgetOverview" -> BudgetOverviewForm(s,e.kind ?: month,close,open)
            "monthBudget" -> BudgetOverviewForm(s,e.id ?: month,close,open)
            "goalList" -> GoalListForm(s,close,open)
            "debtList" -> DebtListForm(s,close,open)
            "accounts" -> AccountsForm(s,busy,close,open)
            "account" -> if(e.id==null)AccountForm(null,vm,busy,close) else AccountDetailForm(s,e.id,vm,busy,close,open)
            "accountSettingsList" -> AccountSettingsList(s,close,open)
            "accountSettings" -> AccountForm(s.accounts.first{it.id==e.id},vm,busy,close)
            "incomePlan" -> RecurringForm(s,s.recurringRules.find{r->r.kind=="INCOME"&&s.recurringAllocations.any{it.ruleId==r.id&&it.accountId==e.id}},"INCOME",vm,busy,close,e.id)
            "monthlyIncome" -> MonthlyIncomeForm(s,month,busy,close,open)
            "receiveIncome" -> ReceiveIncomeForm(s,e.id!!,e.kind ?: month,vm,busy,close)
            "calibrate" -> CalibrationForm(s,e.id!!,vm,busy,close)
            "transaction" -> EntryForm(s,s.entries.find{it.id==e.id},e.kind ?: "EXPENSE",null,vm,busy,close)
            "refund" -> EntryForm(s,null,"REFUND",s.entries.first{it.id==e.id},vm,busy,close)
            "detail" -> EntryDetail(s,s.entries.first{it.id==e.id},vm,busy,close,open)
            "budget" -> BudgetForm(s,e.kind ?: month,vm,busy,close)
            "goal" -> GoalForm(s.goals.find{it.id==e.id},vm,busy,close)
            "debt" -> DebtForm(s,s.debts.find{it.id==e.id},vm,busy,close)
            "debtCategories" -> DebtCategoriesForm(s,vm,busy,close,open)
            "debtCategory" -> DebtCategoryForm(s.debtCategories.find{it.id==e.id},vm,busy,close)
            "debtDetail" -> DebtDetail(s,s.debts.first{it.id==e.id},busy,close,open)
            "repay" -> RepayForm(s,s.installments.first{it.id==e.id},vm,busy,close)
            "simulate" -> SimulatorForm(s,close)
            "profile" -> ProfileForm(s,vm,busy,close,onSwitch,{open(Editor("audits"))},{open(Editor("display"))},{open(Editor("about"))},{open(Editor("tutorial"))},
                {open(Editor("accountSettingsList"))},{open(Editor("backup"))})
            "backup" -> ImportExportForm(s,busy,close,export,restore)
            "tutorial" -> BeginnerTutorial(close,replay=true)
            "about" -> AboutForm(close)
            "audits" -> AuditForm(s.audits,close)
            "display" -> DisplayPreferencesForm(close,{open(Editor("layout"))})
            "layout" -> LayoutPreferencesForm(close)
            "drill" -> Form("${e.id ?: Kind.valueOf(e.kind ?: "EXPENSE").label}明细 · $month",close=close,confirm=null) {
                val rows=s.activeEntries.filter{it.date.startsWith(month)&&(e.kind==null||it.kind==e.kind)&&(e.id==null||s.categoryOf(it)==e.id)}.sortedByDescending{it.date}
                if(rows.isEmpty())Caption("本月没有对应记录。")
                rows.forEach{v->EntryRow(v,s){open(Editor("detail",v.id))}}
            }
        }
    }
}

@Composable private fun AccountsForm(s:Ledger,busy:Boolean,close:()->Unit,open:(Editor)->Unit) {
    Form("我的账户",busy,close,confirm="添加资金账户",submit={open(Editor("account"))}) {
        AmountLine("账户总资产",s.cash(),Forest)
        Caption("显示每个账户现在的余额。发现与实际余额不一致时，可手动校准；停用账户仍计入总资产。")
        s.accounts.forEach { a ->
            CardBox(Modifier.testTag("account-list-${a.id}"),color=Page,onClick={open(Editor("account",a.id))}) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(a.name,fontWeight=FontWeight.SemiBold);Pill(if(a.disabledOn==null) a.type else "已停用",if(a.disabledOn==null) Forest else Muted)}
                Caption("当前余额")
                Text("¥ ${Money.format(s.balance(a.id))}",fontSize=24.sp,color=if(s.balance(a.id)<0) Red else Ink,fontWeight=FontWeight.Medium)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    TextButton(onClick={open(Editor("account",a.id))},modifier=Modifier.testTag("account-open-${a.id}")){Text("查看账户")}
                    TextButton(onClick={open(Editor("calibrate",a.id))},enabled=!busy&&a.disabledOn==null,modifier=Modifier.testTag("account-calibrate-${a.id}")){Text("手动校准")}
                }
            }
        }
        if(s.accounts.isEmpty())Notice("先添加微信、支付宝、银行卡或现金账户，填写开始记账那天北京时间 00:00 的实际余额。")
    }
}
@Composable private fun AccountDetailForm(s:Ledger,id:String,vm:WalletViewModel,busy:Boolean,close:()->Unit,open:(Editor)->Unit) {
    val a=s.accounts.first{it.id==id}
    Form("账户详情",busy,close,confirm=null) {
        TeaCard(tint=Lime) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Text(a.name,Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.Medium)
                Pill(if(a.disabledOn==null)a.type else "已停用")
            }
            TeaSmall("当前余额")
            Text("¥ ${Money.format(s.balance(id))}",fontSize=30.sp,color=if(s.balance(id)<0)Red else Ink,
                fontWeight=FontWeight.Medium,modifier=Modifier.testTag("account-current-balance"))
            TeaSmall("已按记账记录更新，表示这个账户现在剩余的金额。")
        }
        Button(onClick={open(Editor("calibrate",id))},enabled=!busy&&a.disabledOn==null,modifier=Modifier.fillMaxWidth().testTag("account-calibrate")) { Text("手动校准余额") }
        TeaLink("账户设置","修改账户名称、开始余额与开始记账日期"){open(Editor("accountSettings",id))}
        val plans=s.recurringRules.filter{r->r.kind=="INCOME"&&s.recurringAllocations.any{it.ruleId==r.id&&it.accountId==id}}
        TeaLink("固定收入",if(plans.isEmpty())"尚未设置" else "${plans.size} 项收入计划"){open(Editor("incomePlan",id))}
        OutlinedButton(onClick={vm.execute({toggleAccount(id)})},enabled=!busy,modifier=Modifier.fillMaxWidth()) {Text(if(a.disabledOn==null)"停用账户" else "重新启用账户")}
        if(a.disabledOn!=null)Notice("账户已停用。原余额和记录会保留，重新启用后可以继续记账和校准。")
    }
}
@Composable private fun AccountSettingsList(s:Ledger,close:()->Unit,open:(Editor)->Unit) {
    Form("账户设置",close=close,confirm=null) {
        TeaSmall("选择账户，修改开始记账时的余额与日期。日常核对现在的余额，请使用账户详情里的手动校准。")
        s.accounts.forEach{a->TeaLink(a.name,if(a.disabledOn==null)a.type else "已停用"){open(Editor("accountSettings",a.id))}}
        if(s.accounts.isEmpty())TeaLink("添加资金账户","首次填写开始余额与记账日期"){open(Editor("account"))}
    }
}
@Composable private fun AccountForm(old:Account?,vm:WalletViewModel,busy:Boolean,close:()->Unit) {
    var name by rememberSaveable{mutableStateOf(old?.name ?: "")};var type by rememberSaveable{mutableStateOf(old?.type ?: "微信")}
    var opening by rememberSaveable{mutableStateOf(old?.opening?.let{Money.plain(it)} ?: "0.00")};var start by rememberSaveable{mutableStateOf(old?.start ?: today().withDayOfMonth(1).toString())}
    var reason by rememberSaveable{mutableStateOf(if(old==null)"首次核对开始记账时的余额" else "")}
    Form(if(old==null)"添加账户" else "账户设置",busy,close,submit={
        val a=Account(old?.id ?: newId(),name.trim(),type,Money.parse(opening,true),start,old?.disabledOn)
        vm.execute({account(a,reason)},close)
    }) {
        Field("账户名称",name,{name=it});Select("账户类型",listOf("微信","支付宝","银行卡","现金","其他现金账户"),type,{it}){type=it}
        Field("开始记账时的余额（元，可为负）",opening,{opening=it});DateInput("开始记账日 YYYY-MM-DD",start){start=it}
        Field("核对或修改原因",reason,{reason=it})
        Notice("开始余额指开始记账日北京时间 00:00 的余额，不计收入。当天已记的交易不要再次包含在开始余额里。已有流水后只能向前扩展开始记账日。",old!=null)
        if(old!=null)Caption("这里保存的是刚开始记账时的信息。修改开始余额会重算之后的余额；已有校准差额保持不变，请重新核对。只想改现在的余额，请返回账户详情选择手动校准。")
    }
}
@Composable private fun CalibrationForm(s:Ledger,id:String,vm:WalletViewModel,busy:Boolean,close:()->Unit) {
    var target by rememberSaveable{mutableStateOf(Money.plain(s.balance(id)))};var reason by rememberSaveable{mutableStateOf("")}
    Form("校准余额",busy,close,confirm="确认校准",submit={val amount=Money.parse(target,true);vm.execute({calibrate(id,amount,reason)},close)}) {
        Text(s.accountName(id),fontSize=18.sp,fontWeight=FontWeight.Medium)
        AmountLine("当前余额",s.balance(id));Field("实际余额（元，可为负）",target,{target=it});Field("校准原因",reason,{reason=it})
        runCatching{Math.subtractExact(Money.parse(target,true),s.balance(id))}.getOrNull()?.let{AmountLine("本次校准差额",it,if(it<0) Red else Forest)}
        Notice("填写你在微信、支付宝、银行卡或现金中核对的实际剩余金额。校准会单独记录差额，不计收入或消费，也不会改掉开始余额。之后补录早期交易时，请重新核对余额。")
    }
}

@Composable private fun EntryForm(s:Ledger,old:Entry?,kindText:String,original:Entry?,vm:WalletViewModel,busy:Boolean,close:()->Unit) {
    val context=LocalContext.current
    val accountMemory=remember(context){ExpenseAccountMemory(context.getSharedPreferences("wallet_settings",android.content.Context.MODE_PRIVATE))}
    var kind by rememberSaveable{mutableStateOf(old?.kind ?: kindText)}
    var amount by rememberSaveable{mutableStateOf(old?.amount?.let{Money.plain(it)} ?: "")}
    var expenseAccountId by rememberSaveable{mutableStateOf(accountMemory.selected(s,today().toString()))}
    var accountId by rememberSaveable{mutableStateOf(old?.accountId ?: if(kindText=="EXPENSE")expenseAccountId else s.accounts.firstOrNull{it.disabledOn==null}?.id ?: "")}
    var targetId by rememberSaveable{mutableStateOf(old?.toAccountId ?: s.accounts.firstOrNull{it.id!=accountId&&it.disabledOn==null}?.id ?: "")}
    var date by rememberSaveable{mutableStateOf(old?.date ?: today().toString())}
    var category by rememberSaveable{mutableStateOf(old?.category ?: if(kindText=="INCOME") incomeCategories.first() else expenseCategories.first())}
    val initialCategory=old?.let{s.expenseCategory(it)} ?: ExpenseTaxonomy.default
    var categoryGroup by rememberSaveable{mutableStateOf(initialCategory.group.id)}
    var subcategory by rememberSaveable{mutableStateOf(if(initialCategory.group.children.isEmpty())"" else initialCategory.leaf.id)}
    var necessity by rememberSaveable{mutableStateOf(old?.necessity ?: Necessity.UNDECIDED.name)}
    var bucket by rememberSaveable{mutableStateOf(old?.bucket ?: if(kindText=="EXPENSE") "NECESSARY" else "FREE")}
    var note by rememberSaveable{mutableStateOf(old?.note ?: "")};var fee by rememberSaveable{mutableStateOf("0.00")}
    var reason by rememberSaveable{mutableStateOf(if(old==null)"手动记录实际发生交易" else "")}
    val origin=original ?: old?.originalId?.let{id->s.entries.find{it.id==id}}
    Form("${if(old!=null) "编辑" else "记录"}${Kind.valueOf(kind).label}",busy,close,submit={
        require(accountId.isNotBlank()){ "请先添加资金账户" }
        val selectedCategory=ExpenseTaxonomy.explicit(categoryGroup,subcategory) ?: ExpenseTaxonomy.default
        val e=Entry(old?.id ?: newId(),kind,Money.parse(amount,kind=="ADJUSTMENT"),accountId,date,if(kind=="EXPENSE")selectedCategory.group.name else category,bucket,
            if(kind=="TRANSFER") targetId else null,if(kind=="REFUND") origin?.id else null,old?.installmentId,note,false,old?.transferId,old?.incomePlanId,old?.incomePlanMonth,old?.repaymentId,old?.recurringOccurrenceId,
            if(kind=="EXPENSE")categoryGroup else "",if(kind=="EXPENSE")subcategory else "",if(kind=="EXPENSE")necessity else Necessity.UNDECIDED.name)
        val charge=if(kind=="TRANSFER"&&old==null)Money.parse(fee) else 0L
        vm.execute({entry(e,reason,charge)}) {
            accountMemory.rememberSaved(s.profile.demo,e,old==null)
            close()
        }
    }) {
        if(old==null&&original==null&&kind!="REFUND") ChoiceRow(listOf("EXPENSE","INCOME","TRANSFER"),kind,{Kind.valueOf(it).label}){
            kind=it;category=if(it=="INCOME")incomeCategories.first() else expenseCategories.first()
            if(it=="EXPENSE")accountId=expenseAccountId
        }
        if(s.accounts.isEmpty())Notice("请先在“我的资金账户”中添加账户。",true)
        Field(if(kind=="ADJUSTMENT") "余额修正金额（元，可为负）" else "金额（元）",amount,{amount=it},money=kind!="ADJUSTMENT")
        Select(if(kind=="INCOME"||kind=="REFUND")"到账账户" else "付款 / 转出账户",s.accounts,s.accounts.find{it.id==accountId},{it.name+if(it.disabledOn!=null)"（已停用）" else ""}){accountId=it.id;if(kind=="EXPENSE")expenseAccountId=it.id}
        if(kind=="TRANSFER")Select("转入账户",s.accounts.filter{it.id!=accountId},s.accounts.find{it.id==targetId},{it.name}){targetId=it.id}
        if(kind=="INCOME")Select("收入来源",incomeCategories,category,{it}){category=it}
        if(kind=="EXPENSE") {
            ExpenseCategoryPicker(categoryGroup,subcategory){categoryGroup=it.group.id;subcategory=it.leaf.id}
            NecessityPicker(necessity){necessity=it}
        }
        if(kind=="EXPENSE"||(kind=="TRANSFER"&&old==null)){Caption("预算归属（沿用原月度额度）");BucketChoiceRow(bucket){bucket=it}}
        if(kind=="TRANSFER"&&old==null){Field("另付手续费（元，无则为 0）",fee,{fee=it},money=true);Caption("手续费会单独记为消费，计入所选的日常必需或可选消费预算。")}
        if(kind=="REFUND") {
            requireNotNull(origin)
            AmountLine("原始消费",origin.amount);AmountLine("其他有效退款",s.refunds(origin.id)-(old?.amount ?: 0L))
            Caption("${origin.date} · ${origin.category} · ${Bucket.valueOf(origin.bucket).label}\n退款继承原消费预算类型，按实际到账月份抵减预算。")
            Caption("${s.expenseCategory(origin).label} · ${Necessity.valueOf(s.necessityOf(origin)).label}，随原消费调整。")
        }
        DateInput("实际发生日期 YYYY-MM-DD",date){date=it};Field("备注（可选）",note,{note=it})
        if(old!=null) { Field("修改原因",reason,{reason=it});Notice("历史修改会重算余额与关联月份。已有修正差额不会自动变化，请复核。",true) }
        if(kind=="TRANSFER")Caption("转出和转入会一起记录；转账金额不计收支。编辑或撤销转账不会自动删除单独记录的手续费。")
    }
}

@Composable private fun EntryDetail(s:Ledger,e:Entry,vm:WalletViewModel,busy:Boolean,close:()->Unit,open:(Editor)->Unit) {
    var voiding by remember{mutableStateOf(false)};var reason by rememberSaveable{mutableStateOf("")}
    Form("交易详情",busy,close,confirm=null) {
        Pill(if(e.voided)"已撤销" else Kind.valueOf(e.kind).label,if(e.voided) Muted else Forest)
        Text("¥ ${Money.format(e.amount)}",fontSize=32.sp,fontWeight=FontWeight.SemiBold)
        Text(entryTitle(e,s),fontSize=17.sp,fontWeight=FontWeight.Medium)
        if(e.kind=="EXPENSE"||e.kind=="REFUND") {
            Caption("${s.expenseCategory(e).label}\n${Necessity.valueOf(s.necessityOf(e)).label} · 预算归属：${Bucket.valueOf(s.bucketOf(e)).label}")
            if(e.kind=="EXPENSE"&&e.categoryGroupId.isEmpty())Caption("原分类：${e.category}，可重新细分；不会根据金额或类别判断必要性。")
        }
        Caption("日期 ${e.date}\n账户 ${s.accountName(e.accountId)}${if(e.toAccountId!=null) " → ${s.accountName(e.toAccountId)}" else ""}")
        if(e.note.isNotBlank())Text(e.note,fontSize=13.sp,lineHeight=21.sp)
        e.incomePlanMonth?.let{Caption("对应 $it 的固定收入；修改这笔记录不会新增一次到账。")}
        if(e.kind=="REPAYMENT") {
            val group=if(e.repaymentId==null)listOf(e) else s.entries.filter{it.repaymentId==e.repaymentId}
            AmountLine("这次还款合计",group.map{it.amount}.exactSum(),Forest)
            group.forEach{p->AmountLine("${s.accountName(p.accountId)}扣款",p.amount)}
            if(group.size>1)Caption("上方大金额是当前账户扣款；这 ${group.size} 笔属于同一次组合还款，撤销时会一起恢复。")
        }
        Caption("记录编号\n${e.id}")
        if(e.kind=="EXPENSE") {
            AmountLine("累计有效退款",s.refunds(e.id));AmountLine("这笔消费实际花费",e.amount-s.refunds(e.id))
            s.activeEntries.filter{it.originalId==e.id}.forEach{r->TextButton(onClick={open(Editor("detail",r.id))}){Text("${r.date} · 退款 ¥${Money.format(r.amount)} ›")}}
        }
        e.originalId?.let{id->TextButton(onClick={open(Editor("detail",id))}){Text("查看原消费 ›")}}
        e.transferId?.let{id->TextButton(onClick={open(Editor("detail",id))}){Text("查看关联转账 ›")}}
        val charges=s.entries.filter{it.transferId==e.id}
        charges.forEach{c->TextButton(onClick={open(Editor("detail",c.id))}){Text("查看独立手续费 ¥${Money.format(c.amount)} ›")}}
        if(!e.voided) {
            if(e.kind=="EXPENSE")OutlinedButton(onClick={open(Editor("expenseMetadata",e.id))},modifier=Modifier.fillMaxWidth()){Text("修改分类与必要性")}
            if(e.kind!="REPAYMENT"&&e.recurringOccurrenceId==null)OutlinedButton(onClick={open(Editor("transaction",e.id,e.kind))},modifier=Modifier.fillMaxWidth()){Text("修改记录")}
            if(e.recurringOccurrenceId!=null)Notice("这笔记录来自固定收支。需要纠错时，请先撤销这次自动记账，再手动补记；组合扣款会一起撤销。")
            if(e.kind=="EXPENSE"&&s.refunds(e.id)<e.amount)OutlinedButton(onClick={open(Editor("refund",e.id))},modifier=Modifier.fillMaxWidth()){Text("登记到账退款")}
            if(!voiding)TextButton(onClick={voiding=true}){Text(if(e.kind=="REPAYMENT")"撤销错误还款记录" else "撤销这笔记录",color=Red)}
            else {
                Notice(when {
                    e.kind=="REPAYMENT" -> "这将撤销该期整笔还款，一起恢复所有付款账户的账本余额，并保留历史。"
                    e.recurringOccurrenceId!=null -> "这将撤销该次固定收支的全部账户记录，并恢复账本余额；同一次自动记账不会再次生成。"
                    e.kind=="EXPENSE"&&s.refunds(e.id)>0 -> "已有有效退款，请先撤销错误退款记录，才能撤销原消费。"
                    else -> "撤销后会恢复相应余额并保留历史。转账的独立手续费保持有效。"
                },true)
                Field("撤销原因",reason,{reason=it})
                Button(onClick={vm.execute({voidEntry(e.id,reason)},close)},enabled=!busy&&reason.isNotBlank(),colors=ButtonDefaults.buttonColors(containerColor=Red,contentColor=MaterialTheme.colorScheme.onError)){Text("确认撤销")}
            }
        }
        val audits=s.audits.filter{it.entityId==e.id}
        if(audits.isNotEmpty()){Text("变更历史",fontWeight=FontWeight.SemiBold);audits.sortedByDescending{it.time}.forEach{AuditRow(it)}}
    }
}

@Composable private fun BudgetForm(s:Ledger,month:String,vm:WalletViewModel,busy:Boolean,close:()->Unit) {
    val b=s.budget(month)
    var total by rememberSaveable{mutableStateOf(b?.total?.let{Money.plain(it)} ?: "")};var necessary by rememberSaveable{mutableStateOf(b?.necessary?.let{Money.plain(it)} ?: "")};var free by rememberSaveable{mutableStateOf(b?.free?.let{Money.plain(it)} ?: "")}
    Form("$month 月度预算",busy,close,submit={val budget=Budget(month,Money.parse(total),Money.parse(necessary),Money.parse(free));vm.execute({budget(budget)},close)}) {
        Field("总预算（元）",total,{total=it},true);Field("必要消费预算（元）",necessary,{necessary=it},true);Field("自由消费预算（元）",free,{free=it},true)
        runCatching{Money.parse(total)-Money.parse(necessary)-Money.parse(free)}.getOrNull()?.let{AmountLine("未分配",it,if(it<0) Red else Forest)}
        Notice("必要消费与自由消费之和不能超过总额。预算不增加现金，不自动结转；未分配额度不会自动填补类别超支。")
    }
}
@Composable private fun GoalForm(old:Goal?,vm:WalletViewModel,busy:Boolean,close:()->Unit) {
    var name by rememberSaveable{mutableStateOf(old?.name ?: "")};var target by rememberSaveable{mutableStateOf(old?.target?.let{Money.plain(it)} ?: "")}
    var saved by rememberSaveable{mutableStateOf(Money.plain(old?.saved ?: 0))};var date by rememberSaveable{mutableStateOf(old?.date ?: today().plusMonths(6).toString())};var archived by rememberSaveable{mutableStateOf(old?.archived ?: false)}
    Form(if(old==null)"新建储蓄计划" else "更新储蓄计划",busy,close,submit={val g=Goal(old?.id ?: newId(),name.trim(),Money.parse(target),date,old?.intention ?: 0,archived,Money.parse(saved));vm.execute({goal(g)},close)}) {
        Field("目标名称",name,{name=it});Field("目标金额（元）",target,{target=it},true);DateInput("预计存够日期 YYYY-MM-DD",date){date=it};Field("目前已存（元）",saved,{saved=it},true)
        val preview=runCatching{Goal(name=name,target=Money.parse(target).also{require(it>0)},date=date,saved=Money.parse(saved)).also{it.progress()}}.getOrNull()
        if(preview!=null)CardBox(color=Page){SavingsDetails(preview)} else Caption("填写金额和日期后，这里会自动算出每月需要存多少钱。")
        if(old!=null)Row(verticalAlignment=Alignment.CenterVertically){Switch(checked=archived,onCheckedChange={archived=it});Spacer(Modifier.width(10.dp));Text("收起这个计划",fontSize=13.sp)}
        if((old?.intention ?: 0)>0)Notice("旧版计划预留 ¥${Money.format(old!!.intention)} 已保留供核对，不会自动转为已存。储蓄进度以你填写的“目前已存”为准。")
        Notice("已存金额由你记录，不会重复记收入或扣款。账户之间实际转钱，请到“账本”记转账。各计划的已存金额请避免重复填写同一笔钱。")
    }
}

@Composable fun SavingsDetails(g:Goal) {
    val p=g.progress()
    AmountLine("目前已存",g.saved,Forest);AmountLine("还差",p.remaining);Progress(g.saved,g.target)
    when {
        p.remaining==0L -> Pill("目标已达成")
        p.overdue -> Notice("预计日期已过，还需要补存 ¥${Money.format(p.remaining)}。可调整日期重新安排。",true)
        else -> {
            AmountLine("每月建议存",p.monthly,Forest)
            Caption("按当前月份到 ${g.date.take(7)} 的月份差估算，共 ${p.months} 个月；目标在本月时按 1 个月计算。剩余金额均分，向上取整到分，更新已存后会重算。")
        }
    }
}

@Composable private fun IncomePlanForm(s:Ledger,accountId:String,vm:WalletViewModel,busy:Boolean,close:()->Unit) {
    val old=s.incomePlans.find{it.accountId==accountId}
    var amount by rememberSaveable{mutableStateOf(old?.amount?.let{Money.plain(it)} ?: "")}
    var day by rememberSaveable{mutableStateOf((old?.day ?: 1).toString())}
    var source by rememberSaveable{mutableStateOf(old?.source ?: "父母生活费")}
    var start by rememberSaveable{mutableStateOf(old?.startMonth ?: currentMonth())}
    var enabled by rememberSaveable{mutableStateOf(old?.enabled ?: true)}
    Form("每月固定收入 · ${s.accountName(accountId)}",busy,close,submit={
        val p=IncomePlan(accountId,Money.parse(amount),day.toIntOrNull() ?: error("请输入 1–31 的到账日"),source.trim(),start,enabled)
        vm.execute({incomePlan(p)},close)
    }) {
        Field("每月到账金额（元）",amount,{amount=it},true);Field("每月几号到账（1–31）",day,{day=it})
        Field("收入来源",source,{source=it});Field("开始月份 YYYY-MM",start,{start=it})
        Row(verticalAlignment=Alignment.CenterVertically){Switch(checked=enabled,onCheckedChange={enabled=it});Spacer(Modifier.width(10.dp));Text("启用每月计划",fontSize=13.sp)}
        Notice("每个账户可设置一项月收入。遇到没有该日期的月份，按月末安排，例如 2 月没有 31 日，就按 2 月最后一天。")
        Caption("保存后到总览的“每月固定收入”确认到账。系统不会提前增加余额，也不会读取银行、微信或支付宝流水。修改或暂停计划不会改动已记收入。")
    }
}

@Composable private fun MonthlyIncomeForm(s:Ledger,month:String,busy:Boolean,close:()->Unit,open:(Editor)->Unit) {
    val plans=s.incomePlans.filter{(it.enabled&&it.startMonth<=month)||s.incomeReceipt(it.accountId,month)!=null}
    Form("$month 每月固定收入",busy,close,confirm="管理账户与月收入",submit={open(Editor("accounts"))}) {
        Caption("预计收入不会提前计入余额。确认已经收到后，才会记为一笔收入。若已手动记过，请先核对原记录，避免再记一次。")
        if(plans.isEmpty())Notice("这个月还没有固定收入计划。先选择账户，再设置每月金额和到账日。")
        plans.forEach { p ->
            val receipt=s.incomeReceipt(p.accountId,month)
            val disabled=s.accounts.first{it.id==p.accountId}.disabledOn!=null
            CardBox(color=Page) {
                Text(s.accountName(p.accountId),fontWeight=FontWeight.SemiBold)
                Caption(if(receipt==null) "${p.source} · 预计 ${p.expectedDate(month)}" else receipt.category)
                AmountLine(if(receipt==null)"预计到账" else "实际已到账",receipt?.amount ?: p.amount,Forest)
                if(receipt!=null)TextButton(onClick={open(Editor("detail",receipt.id))}){Text("${receipt.date} 已到账 · 查看记录")}
                else if(month>currentMonth())Caption("未来月份，仅作计划。")
                else if(disabled)Caption("账户已停用，重新启用后可登记。")
                else OutlinedButton(onClick={open(Editor("receiveIncome",p.accountId,month))},enabled=!busy){Text("确认实际到账")}
            }
        }
    }
}

@Composable private fun ReceiveIncomeForm(s:Ledger,accountId:String,month:String,vm:WalletViewModel,busy:Boolean,close:()->Unit) {
    val p=s.incomePlans.first{it.accountId==accountId}
    var amount by rememberSaveable{mutableStateOf(Money.plain(p.amount))}
    var date by rememberSaveable{mutableStateOf(today().toString())}
    Form("确认 $month 固定收入",busy,close,confirm="已收到，记入账本",submit={val n=Money.parse(amount);vm.execute({receiveIncome(accountId,month,n,date)},close)}) {
        Text("到账账户：${s.accountName(accountId)}");Caption("${p.source} · 计划 ¥${Money.format(p.amount)} · 预计 ${p.expectedDate(month)}")
        Field("实际收到金额（元）",amount,{amount=it},true);DateInput("实际到账日期 YYYY-MM-DD",date){date=it}
        Notice("请核对钱已收到，且尚未手动记过。这会增加账户余额；同一账户同一月份只能保留一笔有效的固定收入。")
    }
}

@Composable private fun SimulatorForm(s:Ledger,close:()->Unit) {
    var name by rememberSaveable{mutableStateOf("")};var price by rememberSaveable{mutableStateOf("")};var bucket by rememberSaveable{mutableStateOf("FREE")};var account by rememberSaveable{mutableStateOf(s.accounts.firstOrNull{it.disabledOn==null}?.id ?: "")}
    var result by remember{mutableStateOf<Long?>(null)}
    Form("买前想想",close=close,confirm="模拟今天现金购买",submit={val p=Money.parse(price);require(p>0){"价格须大于零"};require(account.isNotBlank()){ "请先添加付款账户" };result=p}) {
        Text("心动，也可以有计划。",fontSize=22.sp,fontWeight=FontWeight.SemiBold)
        Caption("仅模拟一次性现金购买，不实际记账。")
        Field("想买什么（可选）",name,{name=it;result=null});Field("商品价格（元）",price,{price=it;result=null},true)
        Select("假设付款账户",s.accounts.filter{it.disabledOn==null},s.accounts.find{it.id==account},{it.name}){account=it.id;result=null}
        BucketChoiceRow(bucket){bucket=it;result=null}
        result?.let { p ->
            val b=s.budget(currentMonth());val cashAfter=s.cash()-p
            CardBox(color=Page) {
                Text("${name.ifBlank{"这次购买"}}的影响",fontWeight=FontWeight.SemiBold)
                AmountLine("总现金：购买前",s.cash());AmountLine("总现金：购买后",cashAfter,if(cashAfter<0)Red else Forest)
                AmountLine("付款账户：购买后",s.balance(account)-p,if(s.balance(account)<p)Red else Forest)
                if(b!=null) {
                    AmountLine("本月总预算：购买后剩余",b.total-s.usage(currentMonth())-p)
                    val planned=if(bucket=="FREE")b.free else b.necessary
                    AmountLine("${Bucket.valueOf(bucket).label}：购买后剩余",planned-s.usage(currentMonth(),bucket)-p,if(planned-s.usage(currentMonth(),bucket)<p)Red else Forest)
                } else Notice("本月未设置预算，无法判断预算影响。",true)
            }
            if(s.balance(account)<p)Notice("付款账户余额不足。请先核对资金；其他账户的余额不代表此账户能直接支付。",true)
            val due=s.unpaid().filter{it.date<=today().plusDays(30).toString()}
            Text("已有义务与计划",fontWeight=FontWeight.SemiBold)
            if(due.isEmpty())Caption("未登记逾期及未来 30 天的待还款。这不代表不存在其他义务。")
            due.forEach{i->AmountLine("${i.date} · ${s.debts.first{it.id==i.debtId}.name} 第${i.number}期",i.due)}
            s.goals.filterNot{it.archived}.forEach{g->AmountLine("${g.name} · 已存",g.saved);AmountLine("${g.name} · 还差",g.progress().remaining)}
            if(s.goals.filterNot{it.archived}.map{it.saved}.exactSum()>cashAfter)Notice("购买后现金会低于各计划填写的已存合计，可能影响储蓄安排，请核对。",true)
            Notice("预算、待还款和储蓄分别展示，尚未算成统一的可花金额。请结合接下来的生活开销决定是否购买。",true)
        }
    }
}

@Composable private fun ProfileForm(s:Ledger,vm:WalletViewModel,busy:Boolean,close:()->Unit,onSwitch:()->Unit,audits:()->Unit,display:()->Unit,about:()->Unit,tutorial:()->Unit,accounts:()->Unit,backup:()->Unit) {
    var name by rememberSaveable{mutableStateOf(s.profile.name)}
    Form("设置",busy,close,confirm="保存账本名称",submit={vm.execute({profile(name)},close)}) {
        Pill(if(s.profile.demo)"示例数据 · 独立账本" else "个人账本 · 本机离线")
        Field("账本名称",name,{name=it});Caption("人民币 · 北京时间 · 每月 1 日至月底\n无需实名或联网；请定期手动导出备份。")
        OutlinedButton(onClick=display,modifier=Modifier.fillMaxWidth()){Text("主题、显示与动效")}
        OutlinedButton(onClick=tutorial,modifier=Modifier.fillMaxWidth()){Text("新手教程")}
        OutlinedButton(onClick=about,modifier=Modifier.fillMaxWidth()){Text("共同设计与免责声明")}
        OutlinedButton(onClick=accounts,modifier=Modifier.fillMaxWidth()){Text("账户设置")}
        OutlinedButton(onClick=backup,modifier=Modifier.fillMaxWidth()){Text("导入与导出")}
        OutlinedButton(onClick=audits,modifier=Modifier.fillMaxWidth()){Text("查看全部变更历史")}
        OutlinedButton(onClick=onSwitch,modifier=Modifier.fillMaxWidth()){Text(if(s.profile.demo)"切换到我的个人账本" else "切换到独立示例账本")}
        Notice("切换会保留两个账本的数据；示例数据不会进入个人账本。卸载会移除本机数据，请先备份。")
        Caption("${stringResource(R.string.app_name)} 0.11.0 体验版 · 白茶柔雾\n固定收支支持自动记入账本、撤销和多账户组合扣款；负债按你填写的还款计划手动登记。储蓄不会冻结现金，App 不会操作真实支付账户。")
    }
}
@Composable private fun ImportExportForm(s:Ledger,busy:Boolean,close:()->Unit,export:()->Unit,restore:()->Unit) {
    Form("导入与导出",busy,close,confirm=null) {
        TeaCard(tint=Lime) {
            Text(s.profile.name,fontSize=19.sp,fontWeight=FontWeight.Medium)
            TeaSmall("${s.accounts.size} 个账户 · ${s.entries.size} 笔记录")
            TeaSmall("导出当前账本的本地备份，或从备份文件导入。")
        }
        OutlinedButton(onClick=export,enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("backup-export")){Text("导出账本备份（JSON）")}
        TeaSmall("备份包含账本与财务记录，请妥善保管。主题、卡片布局和通知权限为本机设置，不包含在财务备份中。")
        OutlinedButton(onClick=restore,enabled=!busy,modifier=Modifier.fillMaxWidth().testTag("backup-import")){Text("导入账本备份（仅限空账本）")}
        Notice("为保护已有数据，导入仅能恢复到空账本，不会覆盖已有账本。文件读取、校验后仍需你确认恢复。")
    }
}
@Composable fun AuditRow(a:Audit) {
    val time=Instant.ofEpochMilli(a.time).atZone(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    var expanded by remember{mutableStateOf(false)}
    Column(Modifier.fillMaxWidth().background(Page,RoundedCornerShape(12.dp)).clickable{expanded=!expanded}.padding(12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Text(a.action,fontSize=13.sp,fontWeight=FontWeight.SemiBold);Caption("$time\n原因：${a.reason}")
        if(expanded){Caption("ID：${a.entityId}\n修改前：${a.before}\n修改后：${a.after}")}else Caption("点击查看关键字段前后值")
    }
}
@Composable private fun AuditForm(audits:List<Audit>,close:()->Unit) {
    Form("全部变更历史",close=close,confirm=null){if(audits.isEmpty())Caption("还没有变更记录。");audits.sortedByDescending{it.time}.forEach{AuditRow(it)}}
}
