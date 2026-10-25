package com.campusfinance.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// A coordinated botanical palette: distinct hues and alternating lightness, with
// stable category assignments in every ring and legend, in either app theme.
private val expensePalette=listOf(
    0xFFCE843D, // 餐饮食品 · 杏橙
    0xFF328C87, // 生活日用 · 湖绿
    0xFFB7638D, // 服饰鞋包 · 玫瑰
    0xFF487BB7, // 学习教育 · 雾蓝
    0xFF8E63B6, // 数码电子 · 鸢尾
    0xFF469DB9, // 交通出行 · 晴青
    0xFF88883D, // 居住通讯 · 橄榄
    0xFFB590CA, // 休闲娱乐 · 藤紫
    0xFFD36C59, // 人情社交 · 珊瑚
    0xFF659C50, // 医疗健康 · 嫩叶
    0xFF92545F, // 生活服务 · 莓红
    0xFF4D6086, // 金融费用 · 靛蓝
    0xFFC6A83C, // 公益捐赠 · 麦金
    0xFF89929E  // 其他支出 · 石灰蓝
).map{Color(it)}
private val necessityPalette=mapOf(
    Necessity.NECESSARY to Color(0xFF4F7DB5),
    Necessity.NONESSENTIAL to Color(0xFFD0804F),
    Necessity.UNDECIDED to Color(0xFF89929E)
)
fun expenseChartColor(id:String):Color {
    Necessity.entries.find{it.name==id}?.let{return necessityPalette.getValue(it)}
    val group=ExpenseTaxonomy.groups.indexOfFirst{it.id==id||it.name==id}
    if(group>=0)return expensePalette[group]
    val leaf=ExpenseTaxonomy.groups.firstOrNull{g->g.children.any{it.id==id}}?.children?.indexOfFirst{it.id==id}
    return leaf?.let{expensePalette[it]} ?: expensePalette.last()
}

@Composable fun ExpenseCategoryPicker(groupId:String,leafId:String,onChange:(ExpenseCategory)->Unit) {
    val category=ExpenseTaxonomy.explicit(groupId,leafId) ?: ExpenseTaxonomy.default
    Column(Modifier.testTag("expense-category-picker"),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Select("一级分类",ExpenseTaxonomy.groups,category.group,{it.name}){onChange(ExpenseCategory(it,it.fallback))}
        if(category.group.children.isNotEmpty())Select("二级分类",category.group.children,category.leaf,{it.name}){onChange(ExpenseCategory(category.group,it))}
        TeaSmall(category.group.guide)
    }
}
@Composable fun NecessityPicker(value:String,onChange:(String)->Unit) {
    Column(Modifier.testTag("necessity-picker"),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Caption("消费必要性（可不选）")
        ChoiceRow(Necessity.entries.map{it.name},value,{Necessity.valueOf(it).label},onChange)
        TeaSmall("由你判断，默认未判断；与预算归属分别记录。")
    }
}
@Composable fun CategoryBudgetForm(s:Ledger,month:String,vm:WalletViewModel,busy:Boolean,close:()->Unit,open:(Editor)->Unit) {
    var values by rememberSaveable{mutableStateOf<Map<String,String>>(HashMap(s.categoryBudgets.filter{it.month==month}.associate{it.categoryGroupId to Money.plain(it.amount)}))}
    val monthly=s.budget(month)
    Form("$month 分类预算",busy,close,confirm=if(monthly==null)null else "保存分类预算",submit={
        val caps=ExpenseTaxonomy.groups.mapNotNull{g->values[g.id]?.takeIf{it.isNotBlank()}?.let{CategoryBudget(month,g.id,Money.parse(it))}}
        vm.execute({categoryBudgets(month,caps)},close)
    }) {
        if(monthly==null)TeaLink("先设置月度总预算"){open(Editor("budget",kind=month))}
        else {
            AmountLine("月度总预算",monthly.total)
            TeaSmall("这是总预算的另一种细分，不额外增加预算，也不改动必要／自由预算额度。留空表示未设置，0 表示本月不安排该类消费。")
            runCatching{values.values.filter{it.isNotBlank()}.map{Money.parse(it)}.exactSum()}.getOrNull()?.let{AmountLine("已分配分类额度",it)}
            ExpenseTaxonomy.groups.forEach{g->Field("${g.name}（元）",values[g.id] ?: "",{v->values=HashMap(values).apply{put(g.id,v)}},money=true,modifier=Modifier.testTag("category-budget-${g.id}"))}
        }
    }
}
@Composable fun ExpenseMetadataForm(s:Ledger,e:Entry,vm:WalletViewModel,busy:Boolean,close:()->Unit) {
    val initial=s.expenseCategory(e)
    var group by rememberSaveable{mutableStateOf(initial.group.id)}
    var leaf by rememberSaveable{mutableStateOf(if(initial.group.children.isEmpty())"" else initial.leaf.id)}
    var necessity by rememberSaveable{mutableStateOf(e.necessity)}
    Form("修改分类与必要性",busy,close,submit={
        val category=ExpenseTaxonomy.explicit(group,leaf)!!
        vm.execute({entry(e.copy(category=category.group.name,categoryGroupId=group,subcategoryId=leaf,necessity=necessity),"用户调整分类与必要性，金额和付款记录保持原样")},close)
    }) {
        AmountLine("原消费金额",e.amount)
        ExpenseCategoryPicker(group,leaf){group=it.group.id;leaf=it.leaf.id}
        NecessityPicker(necessity){necessity=it}
        TeaSmall("只调整这条记录的分析属性，相关退款随原消费更新。固定计划以后的记录请在计划设置中调整。")
    }
}
