package com.campusfinance.wallet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val disclaimerParagraphs=listOf(
    "本 App 用于个人记账与消费规划。统计、储蓄估算和购买建议依赖你填写的数据，仅供参考，不构成投资、借贷或专业财务建议，也不保证购买或还款能力。",
    "请以实际账户余额、账单和有效合同为准。漏记、错记或系统后台调度延迟可能影响结果；重要支出与还款请自行核对。",
    "固定收支到期后等待你确认，可修改本次金额或跳过。App 不读取银行或支付流水，不发起真实扣款、转账或还款。",
    "账本保存在本机。卸载或清除应用数据会删除本机账本。"
)

@Composable fun FirstConsentScreen(agree:()->Unit,decline:()->Unit) {
    BackHandler(onBack=decline)
    Column(Modifier.fillMaxSize().teaWallpaper().systemBarsPadding().padding(20.dp).testTag("first-consent"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("等风",fontSize=25.sp,fontWeight=FontWeight.Medium,color=Ink)
        Text("使用前请阅读",fontSize=21.sp,color=Ink)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            TeaCard { Text("使用免责声明",fontWeight=FontWeight.Medium);disclaimerParagraphs.forEach{Text(it,fontSize=14.sp,lineHeight=23.sp,color=Ink)} }
            TeaSmall("同意后才能进入账本。此页面仅首次自动展示，之后可在设置中主动查看。")
        }
        Button(onClick=agree,modifier=Modifier.fillMaxWidth().height(50.dp).testTag("consent-agree")){Text("同意并继续")}
        TextButton(onClick=decline,modifier=Modifier.fillMaxWidth().testTag("consent-decline")){Text("不同意并退出")}
    }
}
