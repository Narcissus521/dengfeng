package com.campusfinance.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

// Legacy percentage preferences remain readable; the grid uses independent sizes and positions.
enum class LayoutSize(val label: String, val preset: Int?) {
    SMALL("小",90), STANDARD("标准",100), LARGE("大",110), CUSTOM("自定义",null);
    companion object { fun from(value: String?) = entries.firstOrNull { it.name==value } ?: STANDARD }
}
object LayoutPolicy {
    const val MIN_PERCENT=85
    const val MAX_PERCENT=125
    fun percent(size: LayoutSize, custom: Int) = size.preset ?: custom.coerceIn(MIN_PERCENT,MAX_PERCENT)
}
enum class OverviewCard(val title:String,private val group:String?=null) {
    HOME_SUMMARY("茶间小结"), HOME_PURCHASE("买前想想"), HOME_BUDGET("月度预算"),
    HOME_DEBT("本月待还","HOME_BALANCES"), HOME_SAVINGS("储蓄已存","HOME_BALANCES"),
    LEDGER_ASSETS("账户总资产"), LEDGER_FIXED_EXPENSE("固定消费","LEDGER_FIXED"),
    LEDGER_FIXED_INCOME("固定收入","LEDGER_FIXED"), LEDGER_MONTH("本月账本"),
    PLAN_SAVINGS("储蓄计划"), PLAN_DEBT("负债"), ANALYSIS_TOTAL("消费合计"), ANALYSIS_CHART("分类概览"), ANALYSIS_TREND("消费趋势");
    val pageKey get()=group ?: name
}
val LocalCardResizeRequest=staticCompositionLocalOf<((OverviewCard)->Unit)?>{null}
val LocalResizingCard=staticCompositionLocalOf<OverviewCard?>{null}
fun DisplayState.cardPercent(card:OverviewCard)=cardSizes[card] ?: layoutPercent

@Composable fun CardResizeControls(card:OverviewCard,layout:CardGridLayout,reset:()->Unit,done:()->Unit) {
    Row(Modifier.fillMaxWidth().height(36.dp).testTag("card-resize-panel"),verticalAlignment=Alignment.CenterVertically) {
        Text(card.title,Modifier.weight(1f),fontSize=12.sp,maxLines=1)
        Text("${layout.columns}×${layout.rows}",fontSize=12.sp,color=Forest,modifier=Modifier.testTag("card-resize-dimensions"))
        TextButton(onClick=reset,contentPadding=PaddingValues(8.dp),modifier=Modifier.testTag("card-resize-reset")){Text("默认",fontSize=12.sp)}
        TextButton(onClick=done,contentPadding=PaddingValues(8.dp),modifier=Modifier.testTag("card-resize-done")){Text("完成",fontSize=12.sp)}
    }
}

@Composable fun LayoutPreferencesForm(close:()->Unit) {
    val controller=LocalDisplayController.current
    Form("卡片大小",close=close,confirm=null) {
        TeaCard(tint=Lime) {
            Text("长按卡片，随手安排",fontSize=19.sp,fontWeight=FontWeight.Medium)
            TeaSmall("在首页、账本、规划或分析，长按卡片主体并保持按住，即可跟随手指在当前页移动，松手吸附并保存；其他卡片会平滑避让。")
            TeaSmall("长按后拖动两侧调整宽度，拖动下边缘调整高度；点击“完成”结束。取消手势会回到原位，短按仍打开原来的功能。")
        }
        TeaSmall("以首页“本月待还”2×1为基准，整行宽4格。每张卡片可调2—4格宽、1—4格高，文字和明细随形状重新排版；窗口较小时可用高度随之调整。空间不足会自动分页。")
        OutlinedButton(onClick={controller?.resetCardSizes()},modifier=Modifier.fillMaxWidth().testTag("layout-reset-all")) { Text("恢复所有卡片默认大小") }
    }
}
