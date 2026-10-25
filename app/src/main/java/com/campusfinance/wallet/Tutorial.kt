package com.campusfinance.wallet

import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object TutorialPolicy {
    const val VERSION=1
    const val KEY="onboarding_version"
    fun needsTutorial(prefs:SharedPreferences)=prefs.getInt(KEY,0)<VERSION
    fun complete(prefs:SharedPreferences){prefs.edit().putInt(KEY,VERSION).apply()}
}

@Composable fun FirstRunGate(prefs:SharedPreferences,content:@Composable ()->Unit) {
    // Completion is durable in preferences; do not restore an obsolete gate from Activity state.
    var show by remember {mutableStateOf(TutorialPolicy.needsTutorial(prefs))}
    if(show)BeginnerTutorial({TutorialPolicy.complete(prefs);show=false}) else content()
}

private data class TutorialPage(val title:String,val art:String,val lines:List<String>)
private val tutorialPages=listOf(
    TutorialPage("先把账户整理好","share",listOf("在「账本」添加微信、支付宝、银行卡或现金。","首次填写开始余额和日期，之后显示当前余额。","核对有差额可手动校准；初始信息在账户设置中修改。")),
    TutorialPage("每天记一点，心里更清楚","steep",listOf("点底部茶叶，选择支出、收入、转账或设置。","支出按用途分类，公益捐赠和其他支出可直接选，必要性可选，默认未判断。","固定收支到期会自动记入本地账本，可在明细中撤销。")),
    TutorialPage("给未来留一点余地","simmer",listOf("在「规划」安排预算、储蓄和负债；在「分析」回看消费。","填目标金额、已存金额和日期，就能查看每月需存多少。","买前想想分别展示现金、预算、还款和储蓄影响。")),
    TutorialPage("像拨动一滴露水","steep",listOf("总结时段和底部导航既能点，也能左右拖动选择。","点小卡片进入详情；返回会平滑收回原位。","长按卡片主体拖动位置，拖动边缘调整宽高；松手保存。")),
    TutorialPage("安心记录，也记得备份","share",listOf("数据保存在本机，无需注册，不读取支付软件流水。","自动记账只更新记录，不会真的扣款或转账。","设置中可导出备份、重看教程、查看共同设计与免责声明。"))
)

@Composable fun BeginnerTutorial(finish:()->Unit,replay:Boolean=false) {
    var step by rememberSaveable {mutableIntStateOf(0)}
    var demoTab by rememberSaveable {mutableIntStateOf(0)}
    val page=tutorialPages[step]
    val next by rememberUpdatedState {if(step<tutorialPages.lastIndex)step++ else finish()}
    BackHandler {if(step>0)step-- else finish()}
    Column(Modifier.fillMaxSize().teaWallpaper().then(if(replay)Modifier else Modifier.systemBarsPadding()).padding(horizontal=24.dp,vertical=12.dp).testTag("tutorial"),verticalArrangement=Arrangement.spacedBy(15.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Glyph("leaf",modifier=Modifier.size(25.dp));Spacer(Modifier.width(8.dp))
            Text("新手教程",Modifier.weight(1f),fontSize=17.sp)
            TextButton(onClick=finish,modifier=Modifier.testTag("tutorial-skip")){Text(if(replay)"关闭" else "跳过")}
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().pointerInput(Unit){var distance=0f;detectHorizontalDragGestures(onDragStart={distance=0f},onDragEnd={if(distance < -70f)next() else if(distance>70f&&step>0)step--}) {change,amount->change.consume();distance+=amount}}) {
            val compact=maxHeight<560.dp
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(if(compact)13.dp else 22.dp)) {
                TeaArt(page.art,Modifier.align(Alignment.CenterHorizontally).size(if(compact)100.dp else 142.dp,if(compact)80.dp else 116.dp))
                Text(page.title,fontSize=if(compact)25.sp else 29.sp,lineHeight=38.sp,fontWeight=FontWeight.Medium,color=Ink,modifier=Modifier.testTag("tutorial-title"))
                Column(Modifier.fillMaxWidth().teaGlass(Lime).padding(18.dp),verticalArrangement=Arrangement.spacedBy(if(compact)12.dp else 19.dp)) {
                    page.lines.forEachIndexed {i,line->Row(horizontalArrangement=Arrangement.spacedBy(11.dp)){Text("${i+1}",color=Forest,fontSize=14.sp);Text(line,fontSize=14.sp,lineHeight=23.sp,color=Ink)}}
                }
                if(step==3){TeaTabs(listOf("月度","半年","年度"),demoTab){demoTab=it};TeaSmall("在这里试着左右拖动，不会影响真实账本。")}
                if(step==4)TeaSmall("建议与估算仅供个人规划参考，请以实际余额、账单和合同为准。")
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={step--},enabled=step>0){Text("上一步")}
            TeaSmall("${step+1} / ${tutorialPages.size}")
            Button(onClick={next()},modifier=Modifier.testTag("tutorial-next")){Text(if(step==tutorialPages.lastIndex)"开始使用" else "下一步")}
        }
    }
}
