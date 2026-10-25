package com.campusfinance.wallet

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

object PurchaseStore {
    private fun key(demo:Boolean)="purchase_${if(demo)"demo" else "personal"}"
    fun load(context:Context,demo:Boolean):PurchaseInput? = runCatching {
        val text=context.getSharedPreferences("wallet_settings",Context.MODE_PRIVATE).getString(key(demo),null) ?: return null
        val j=JSONObject(text);val a=j.getJSONArray("amounts")
        PurchaseInput(j.getString("name"),j.getLong("price"),j.getString("account"),j.getString("bucket"),j.getString("category"),j.getLong("down"),
            (0 until a.length()).map{a.getLong(it)},j.getString("firstDate"),j.optString("categoryGroupId"),j.optString("subcategoryId"))
    }.getOrNull()
    fun save(context:Context,demo:Boolean,p:PurchaseInput) {
        val j=JSONObject().put("name",p.name).put("price",p.price).put("account",p.accountId).put("bucket",p.bucket).put("category",p.category)
            .put("down",p.down).put("amounts",JSONArray(p.amounts)).put("firstDate",p.firstDate).put("categoryGroupId",p.categoryGroupId).put("subcategoryId",p.subcategoryId)
        context.getSharedPreferences("wallet_settings",Context.MODE_PRIVATE).edit().putString(key(demo),j.toString()).apply()
    }
}
@Composable fun rememberPurchase(demo:Boolean):State<PurchaseInput?> {
    val context=LocalContext.current
    val result=remember(demo){mutableStateOf(PurchaseStore.load(context,demo))}
    DisposableEffect(demo) {
        val prefs=context.getSharedPreferences("wallet_settings",Context.MODE_PRIVATE)
        val listener=SharedPreferences.OnSharedPreferenceChangeListener { _,_ -> result.value=PurchaseStore.load(context,demo) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return result
}

@Composable fun RecurringList(s:Ledger,kind:String,busy:Boolean,close:()->Unit,open:(Editor)->Unit) {
    val rules=s.recurringRules.filter{it.kind==kind}
    Form(if(kind=="EXPENSE")"固定消费" else "固定收入",busy,close,confirm=if(kind=="EXPENSE")"＋ 新增固定消费" else "＋ 新增固定收入",submit={open(Editor("recurring",kind=kind))}) {
        TeaCard(tint=LocalWalletColors.current.hero) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    TeaSmall("每月固定");Text("¥${Money.format(rules.filter{it.enabled&&it.frequency=="MONTHLY"}.map{it.amount}.exactSum())}",fontSize=25.sp)
                    TeaSmall("每日 ¥${Money.format(rules.filter{it.enabled&&it.frequency=="DAILY"}.map{it.amount}.exactSum())}")
                }
                TeaArt(if(kind=="EXPENSE")"pour" else "share",Modifier.size(96.dp,78.dp))
            }
        }
        TeaSmall("到期自动记账，可撤销。只更新账本，不操作真实付款或收款。")
        if(rules.isEmpty())TeaSmall("还没有固定计划。生活费、话费和每日早餐都可以分别安排。")
        rules.forEach { r ->
            TeaCard(onClick={open(Editor("recurringDetail",r.id))}) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(r.name,fontSize=15.sp);Text("¥${Money.format(r.amount)}",fontSize=17.sp)}
                TeaSmall("${if(r.frequency=="DAILY")"每天" else "每月 ${r.day} 日"} ${r.time} · ${if(r.enabled)"自动记账" else "已暂停"} ›")
            }
        }
    }
}
@Composable fun RecurringDetail(s:Ledger,id:String,vm:WalletViewModel,busy:Boolean,close:()->Unit,open:(Editor)->Unit) {
    val r=s.recurringRules.first{it.id==id}
    Form(r.name,busy,close,confirm="调整计划",submit={open(Editor("recurring",r.id,r.kind))}) {
        TeaCard(tint=LocalWalletColors.current.hero) {
            Text("¥${Money.format(r.amount)}",fontSize=31.sp)
            TeaSmall("${if(r.frequency=="DAILY")"每天" else "每月 ${r.day} 日"} ${r.time} · ${if(r.enabled)"到期自动记账" else "已暂停"}")
            TeaSmall("从 ${r.startDate} 开始 · ${r.category}${if(r.kind=="EXPENSE")" · ${Bucket.valueOf(r.bucket).label}" else ""}")
        }
        TeaCard {
            Text(if(r.kind=="EXPENSE")"付款账户" else "到账账户",fontSize=15.sp)
            s.recurringAllocations.filter{it.ruleId==id}.forEach { AmountLine(s.accountName(it.accountId),it.amount) }
        }
        val occurrences=s.recurringOccurrences.filter{it.ruleId==id}.sortedByDescending{it.date}
        if(occurrences.isEmpty())TeaSmall("下一次：${r.nextAfter(java.time.ZonedDateTime.now(financeZone)).toLocalDate()} ${r.time}")
        occurrences.forEach { o ->
            TeaCard {
                Text(o.date,fontSize=14.sp);TeaSmall(when(o.status){"POSTED"->"本次已自动记账";"UNDONE"->"本次已撤销，不会重复生成";else->"未记账：${o.reason}"})
                if(o.status=="POSTED")TextButton(onClick={vm.execute({undoRecurring(o.id)})},enabled=!busy){Text("撤销本次")}
            }
        }
    }
}

@Composable fun RecurringForm(s:Ledger,old:RecurringRule?,kind:String,vm:WalletViewModel,busy:Boolean,close:()->Unit,defaultAccount:String?=null) {
    val id=rememberSaveable{old?.id ?: newId()}
    var step by rememberSaveable{mutableIntStateOf(0)}
    var name by rememberSaveable{mutableStateOf(old?.name ?: "")}
    var amount by rememberSaveable{mutableStateOf(old?.amount?.let{Money.plain(it)} ?: "")}
    var frequency by rememberSaveable{mutableStateOf(old?.frequency ?: "MONTHLY")}
    var day by rememberSaveable{mutableStateOf((old?.day ?: today().dayOfMonth).toString())}
    var time by rememberSaveable{mutableStateOf(old?.time ?: "08:00")}
    var start by rememberSaveable{mutableStateOf(old?.startDate ?: today().toString())}
    var category by rememberSaveable{mutableStateOf(old?.category ?: if(kind=="INCOME")"父母生活费" else "餐饮")}
    val initialCategory=old?.let{ExpenseTaxonomy.resolve(it.categoryGroupId,it.subcategoryId,it.category)} ?: ExpenseTaxonomy.default
    var categoryGroup by rememberSaveable{mutableStateOf(initialCategory.group.id)}
    var subcategory by rememberSaveable{mutableStateOf(if(initialCategory.group.children.isEmpty())"" else initialCategory.leaf.id)}
    var necessity by rememberSaveable{mutableStateOf(old?.necessity ?: Necessity.UNDECIDED.name)}
    var bucket by rememberSaveable{mutableStateOf(old?.bucket ?: "NECESSARY")}
    var enabled by rememberSaveable{mutableStateOf(old?.enabled ?: true)}
    val previous=s.recurringAllocations.filter{it.ruleId==old?.id}
    var selected by rememberSaveable{mutableStateOf<List<String>>(ArrayList(previous.map{it.accountId}.ifEmpty{listOfNotNull(defaultAccount ?: s.accounts.firstOrNull{it.disabledOn==null}?.id)}))}
    var values by rememberSaveable{mutableStateOf<Map<String,String>>(HashMap(previous.associate{it.accountId to Money.plain(it.amount)}))}
    fun allocations():List<RecurringAllocation> {
        val total=Money.parse(amount)
        return selected.map { account -> RecurringAllocation(id,account,if(kind=="INCOME"||selected.size==1)total else Money.parse(values[account] ?: "")) }
    }
    Form(if(kind=="INCOME")"固定收入设置" else "固定消费设置",busy,close,
        confirm=if(step==3)"保存计划" else "下一步",submit={
            when(step) {
                0->{require(name.isNotBlank()&&name.length<=40){"请填写计划名称"};require(Money.parse(amount)>0){"金额须大于零"};step++}
                1->{require(day.toIntOrNull() in 1..31){"每月日期须为 1–31 日"};LocalDate.parse(start);LocalTime.parse(time);require(Regex("\\d{2}:\\d{2}").matches(time)){"时间格式应为 HH:mm"};step++}
                2->{require(selected.isNotEmpty()){ "请选择资金账户" };require(allocations().map{it.amount}.exactSum()==Money.parse(amount)){"账户分摊合计须等于每次金额"};require(allocations().all{it.amount>0}){"各账户金额须大于零"};step++}
                else->{val r=RecurringRule(id,name.trim(),kind,Money.parse(amount),frequency,day.toInt(),time,start,if(kind=="EXPENSE")ExpenseTaxonomy.explicit(categoryGroup,subcategory)!!.group.name else category,bucket,enabled,
                    if(kind=="EXPENSE")categoryGroup else "",if(kind=="EXPENSE")subcategory else "",if(kind=="EXPENSE")necessity else Necessity.UNDECIDED.name);val a=allocations();vm.execute({recurring(r,a)},close)}
            }
        }) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){TeaSmall(listOf("名称与金额","日期与消费种类","资金账户","自动记账")[step]);TeaSmall("${step+1} / 4")}
        when(step) {
            0->{Field("计划名称",name,{name=it});Field("每次金额（元）",amount,{amount=it},money=true)}
            1->{
                Select("重复频率",listOf("MONTHLY","DAILY"),frequency,{if(it=="MONTHLY")"每月" else "每天"}){frequency=it}
                if(frequency=="MONTHLY")Field("每月几号（1–31）",day,{day=it},money=true)
                Field(if(frequency=="DAILY")"每天几点 HH:mm" else "记账时间 HH:mm",time,{time=it})
                DateInput("开始日期",start){start=it}
                if(kind=="EXPENSE") {
                    ExpenseCategoryPicker(categoryGroup,subcategory){categoryGroup=it.group.id;subcategory=it.leaf.id}
                    NecessityPicker(necessity){necessity=it}
                } else Select("收入来源",incomeCategories,category,{it}){category=it}
                if(kind=="EXPENSE")Select("消费归属",Bucket.entries.toList(),Bucket.valueOf(bucket),{it.label}){bucket=it.name}
            }
            2->{
                TeaSmall(if(kind=="INCOME")"为不同到账账户分别建立收入计划。" else "可组合多个账户，各账户金额相加等于每次消费金额。")
                selected.forEach { account ->
                    TeaCard {
                        Select(if(kind=="INCOME")"到账账户" else "付款账户",s.accounts.filter{it.disabledOn==null&&(it.id==account||it.id !in selected)},s.accounts.find{it.id==account},{it.name}) { next ->
                            selected=ArrayList(selected.map{if(it==account)next.id else it});values=HashMap(values).apply{put(next.id,values[account] ?: amount)}
                        }
                        if(kind=="EXPENSE"&&selected.size>1)Field("分摊金额（元）",values[account] ?: "",{v->values=HashMap(values).apply{put(account,v)}},money=true,modifier=Modifier.testTag("recurring-split-$account"))
                        else TeaSmall("本账户每次 ¥$amount")
                        if(kind=="EXPENSE"&&selected.size>1)TextButton(onClick={selected=ArrayList(selected-account)}){Text("移除此账户")}
                    }
                }
                if(kind=="EXPENSE")OutlinedButton(onClick={
                    if(selected.size==1&&!values.containsKey(selected.first()))values=HashMap(values).apply{put(selected.first(),amount)}
                    s.accounts.firstOrNull{it.disabledOn==null&&it.id !in selected}?.let { selected=ArrayList(selected+it.id) }
                },enabled=s.accounts.any{it.disabledOn==null&&it.id !in selected}){Text("添加付款账户")}
                if(selected.isEmpty())Notice("先到账本添加资金账户，再设置固定收支。",true)
            }
            else->{
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Text("到期自动记账");Switch(checked=enabled,onCheckedChange={enabled=it})}
                TeaSmall("按北京时间安排，遇到当月没有的日期，在月末记账。每一期只记一次，撤销后不自动重记。")
                TeaSmall("暂停后重新启用，从当天或你设定的未来日期恢复，不补记暂停期间的记录。")
                TeaSmall("系统可能延迟后台唤醒，重新打开 App 会补记已到期的计划。只操作账本，不发起实际扣款。")
                if(start<today().toString())Notice("开始日期在过去，保存后会补记已到期的固定收支，请核对日期。",true)
                TeaArt(if(kind=="INCOME")"share" else "pour",Modifier.size(120.dp,85.dp).align(Alignment.End))
            }
        }
        if(step>0)TextButton(onClick={step--}){Text("上一步")}
    }
}

@Composable fun ReminderForm(s:Ledger,vm:WalletViewModel,busy:Boolean,close:()->Unit) {
    val context=LocalContext.current
    var enabled by rememberSaveable{mutableStateOf(s.reminder.enabled)}
    var start by rememberSaveable{mutableStateOf(s.reminder.startTime)}
    var end by rememberSaveable{mutableStateOf(s.reminder.endTime)}
    var selected by rememberSaveable{mutableStateOf<List<String>>(ArrayList(ExpenseTaxonomy.groups.filter{it.id in s.reminder.selectedGroupIds()}.map{it.name}))}
    var allowed by remember{mutableStateOf(WalletAutomation.notificationsAllowed(context))}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){allowed=WalletAutomation.notificationsAllowed(context)}
    Form("每日记账提醒",busy,close,confirm="保存提醒",submit={
        val r=ReminderSettings(enabled=enabled,startTime=start,endTime=end,categories=selected.joinToString(","));LocalTime.parse(start);LocalTime.parse(end)
        require(!enabled||selected.isNotEmpty()){"请至少选择一种消费"};vm.execute({reminder(r)},close)
    }) {
        TeaSmall("每日一盏茶，也记下今天的开销。")
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Text("每天提醒我");Switch(enabled,onCheckedChange={enabled=it})}
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Field("开始时间 HH:mm",start,{start=it},modifier=Modifier.weight(1f));Field("结束时间 HH:mm",end,{end=it},modifier=Modifier.weight(1f))}
        TeaSmall("时段内提醒一次；当日已手动记录所选消费则不再提醒。结束早于开始时，表示跨到次日。")
        Text("提醒记录哪些消费",fontSize=15.sp)
        expenseCategories.filterNot{it=="转账手续费"}.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth()) { row.forEach { category ->
                Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(category in selected,onCheckedChange={selected=ArrayList(if(it)selected+category else selected-category)})
                    Text(category,fontSize=12.sp)
                }
            };if(row.size==1)Spacer(Modifier.weight(1f)) }
        }
        if(!allowed)OutlinedButton(onClick={if(Build.VERSION.SDK_INT>=33)permission.launch(Manifest.permission.POST_NOTIFICATIONS) else {
            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,context.packageName))
        }}){Text("允许记账通知")}
        TeaSmall("提醒只提醒你记录；固定消费才会按计划自动记账。系统省电设置可能延迟通知。")
    }
}

@Composable fun PurchaseForm(s:Ledger,close:()->Unit,open:(Editor)->Unit) {
    val context=LocalContext.current;val old=remember{PurchaseStore.load(context,s.profile.demo)}
    var step by rememberSaveable{mutableIntStateOf(0)}
    var name by rememberSaveable{mutableStateOf(old?.name ?: "")};var price by rememberSaveable{mutableStateOf(old?.price?.let{Money.plain(it)} ?: "")}
    var account by rememberSaveable{mutableStateOf(old?.accountId ?: s.accounts.firstOrNull{it.disabledOn==null}?.id ?: "")}
    val initialCategory=old?.let{ExpenseTaxonomy.resolve(it.categoryGroupId,it.subcategoryId,it.category)} ?: ExpenseTaxonomy.default
    var categoryGroup by rememberSaveable{mutableStateOf(initialCategory.group.id)}
    var subcategory by rememberSaveable{mutableStateOf(if(initialCategory.group.children.isEmpty())"" else initialCategory.leaf.id)}
    var bucket by rememberSaveable{mutableStateOf(old?.bucket ?: "FREE")}
    var down by rememberSaveable{mutableStateOf(old?.down?.let{Money.plain(it)} ?: "0.00")};var count by rememberSaveable{mutableStateOf((old?.amounts?.size?.takeIf{it>0} ?: 3).toString())}
    var equal by rememberSaveable{mutableStateOf((old?.amounts?.distinct()?.size ?: 0)<=1)}
    var equalAmount by rememberSaveable{mutableStateOf(old?.amounts?.firstOrNull()?.let{Money.plain(it)} ?: "")}
    var values by rememberSaveable{mutableStateOf<List<String>>(ArrayList(old?.amounts?.map{Money.plain(it)} ?: emptyList()))}
    var first by rememberSaveable{mutableStateOf(old?.firstDate?.takeIf{it>=today().toString()} ?: today().plusMonths(1).toString())}
    var compare by rememberSaveable{mutableStateOf(true)}
    fun input():PurchaseInput {
        val n=if(!compare)1 else count.toIntOrNull()?.takeIf{it in 1..120} ?: error("还款总期数须为 1–120 期")
        return PurchaseInput(name.trim(),Money.parse(price),account,bucket,ExpenseTaxonomy.explicit(categoryGroup,subcategory)!!.group.name,if(compare)Money.parse(down) else 0,
            if(!compare)emptyList() else if(equal)List(n){Money.parse(equalAmount)} else List(n){Money.parse(values.getOrElse(it){""})},first,categoryGroup,subcategory)
    }
    Form("买前想想",close=close,confirm=if(step<2)"下一步" else "看看购买建议",submit={
        when(step){
            0->{require(name.isNotBlank()){ "请填写商品名称" };require(Money.parse(price)>0){"价格须大于零"};require(s.accounts.any{it.id==account&&it.disabledOn==null}){"请先选择付款账户"};step++}
            1->{if(compare){val n=count.toIntOrNull();require(n in 1..120){"还款总期数须为 1–120 期"};LocalDate.parse(first);require(Money.parse(down)<=Money.parse(price)){"首付不能超过商品价格"}};step++}
            else->{val p=input();s.review(p);PurchaseStore.save(context,s.profile.demo,p);open(Editor("purchaseResult"))}
        }
    }) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){TeaSmall("等一盏茶，给心动留点时间。",Modifier.weight(1f));TeaArt("steep",Modifier.size(59.dp,43.dp))}
        TeaSmall("${listOf("商品与付款账户","分期购买方案","各期金额")[step]} · ${step+1} / 3")
        when(step){
            0->{Field("商品名称",name,{name=it});Field("商品价格（元）",price,{price=it},money=true);Select("付款账户",s.accounts.filter{it.disabledOn==null},s.accounts.find{it.id==account},{it.name}){account=it.id}
                ExpenseCategoryPicker(categoryGroup,subcategory){categoryGroup=it.group.id;subcategory=it.leaf.id};Select("预算归属",Bucket.entries.toList(),Bucket.valueOf(bucket),{it.label}){bucket=it.name}}
            1->{
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){Text("同时比较分期购买");Switch(compare,onCheckedChange={compare=it})}
                if(compare){Field("首付（元）",down,{down=it},money=true);Field("还款总期数（1–120）",count,{count=it},money=true);DateInput("首期还款日",first){first=it};TeaTabs(listOf("每月相同","每期分别填写"),if(equal)0 else 1){equal=it==0}}
            }
            else->{
                if(!compare)TeaSmall("本次仅模拟一次性购买。以后可以补充商家的分期报价。")
                else if(equal)Field("每期应付（含费用，元）",equalAmount,{equalAmount=it},money=true)
                else (0 until (count.toIntOrNull() ?: 0).coerceIn(0,120)).forEach { index ->
                    Field("第 ${index+1} 期应付（元）",values.getOrElse(index){""},{v->values=ArrayList(List(maxOf(values.size,index+1)){i->if(i==index)v else values.getOrElse(i){equalAmount}})},money=true)
                }
                TeaSmall("按商家给出的应付金额填写，不自行推算利率。商品全价占用本次消费预算，还款不重复算商品消费。")
            }
        }
        if(step>0)TextButton(onClick={step--}){Text("上一步")}
    }
}

@Composable fun PurchaseResult(s:Ledger,close:()->Unit,open:(Editor)->Unit,mode:String?=null) {
    val context=LocalContext.current;val p=PurchaseStore.load(context,s.profile.demo)
    val result=p?.let{runCatching{s.review(it)}.getOrNull()}
    Form(when(mode){"cash"->"余额与预算";"install"->"分期付款安排";"existing"->"已有资金安排";else->"购买建议"},close=close,confirm=null) {
        if(p==null||result==null){TeaSmall("账户或报价已变化，请重新填写购买设置。");TeaLink("重新设置"){open(Editor("purchase"))}}
        else when(mode) {
            "cash"->{AmountLine("购买后账户总资产",result.cashAfter);AmountLine(s.accountName(p.accountId),result.accountAfter)
                result.budgetLeft?.let{AmountLine("本月总预算剩余",it,if(it<0)Red else Ink)};result.bucketLeft?.let{AmountLine("${Bucket.valueOf(p.bucket).label}剩余",it,if(it<0)Red else Ink)}
                TeaSmall("预算按整件商品价格计算。这里仅模拟，不生成交易。")}
            "install"->{
                if(p.amounts.isEmpty())TeaSmall("尚未填写分期报价。") else {
                    AmountLine("现在需付",result.payNow);AmountLine("购买后账户总资产",result.installmentCashAfter);AmountLine("所选付款账户余额",result.installmentAccountAfter)
                    TeaSmall("首期 ${p.firstDate} · 商品消费预算仍按全价计算")
                    p.amounts.forEachIndexed { i,amount -> AmountLine("第 ${i+1} 期 · ${LocalDate.parse(p.firstDate).plusMonths(i.toLong())}",amount) }
                }
            }
            "existing"->{AmountLine("未来30天内及逾期待还",result.pendingDebt);AmountLine("储蓄已存需保留",result.savings)
                s.unpaid().filter{LocalDate.parse(it.date)<=today().plusDays(30)}.forEach{TeaSmall("${s.debts.first{d->d.id==it.debtId}.name} · ${it.date} · ¥${Money.format(it.due)}")}
                TeaSmall("还款、预算和储蓄安排分别列示，不合并成一个可花金额。")}
            else->{
                TeaSmall("茶有适温，消费也有合适时机。")
                TeaCard(tint=LocalWalletColors.current.warning){Text("一次付：${result.onceAdvice}",fontSize=14.sp,color=Amber)}
                TeaCard(tint=Pale){Text("分期付：${result.installmentAdvice}",fontSize=14.sp)}
                Row(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                    TeaCard(Modifier.weight(1f),tint=Pale){TeaSmall("一次性付款");Text("¥${Money.format(p.price)}",fontSize=23.sp);TeaSmall("现在需付 / 付款合计")}
                    TeaCard(Modifier.weight(1f),tint=Pale){TeaSmall(if(p.amounts.isEmpty())"尚无分期报价" else "分 ${p.amounts.size} 期");Text(if(p.amounts.isEmpty())"—" else "¥${Money.format(result.quotedTotal)}",fontSize=23.sp);TeaSmall(if(p.amounts.isEmpty())"填真实报价后比较" else "比一次付多付 ¥${Money.format(result.quoteExtra)}")}
                }
                TeaLink("一次付：余额与预算"){open(Editor("purchaseImpact",kind="cash"))}
                TeaLink("分期付：后续付款安排"){open(Editor("purchaseImpact",kind="install"))}
                TeaLink("已有还款与储蓄预留"){open(Editor("purchaseImpact",kind="existing"))}
                TeaSmall("建议仅反映已记录的账户、预算和计划，请结合未记录的必要开销决定。")
            }
        }
    }
}
