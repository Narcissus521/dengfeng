package com.campusfinance.wallet

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun AboutForm(close:()->Unit) {
    Form("关于与说明",close=close,confirm=null) {
        TeaCard(tint=LocalWalletColors.current.hero) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Image(painterResource(R.drawable.designer_avatar),contentDescription="梨花怎黛玉的头像",
                    contentScale=ContentScale.Crop,modifier=Modifier.size(76.dp).clip(CircleShape))
                Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("梨花怎黛玉",fontSize=20.sp,color=Ink,fontWeight=FontWeight.Medium)
                    TeaSmall("产品构想 · 交互与视觉共创")
                    TeaSmall("${stringResource(R.string.app_name)} 0.11.0 体验版")
                }
            }
            Text("由梨花怎黛玉与 OpenAI Codex 共同设计",fontSize=14.sp,color=Ink,fontWeight=FontWeight.Medium)
            TeaSmall("梨花怎黛玉提出产品需求、选择与打磨设计；OpenAI Codex 提供 AI 辅助设计、代码实现和测试支持。")
        }
        TeaCard {
            Text("记下今天，照顾未来",fontSize=17.sp,color=Ink)
            TeaSmall("以白茶的轻盈与从容，陪伴每一次记录和计划。让花钱更清楚，让储蓄有方向。")
            TeaArt("steep",Modifier.fillMaxWidth().height(68.dp))
        }
        TeaCard {
            Text("使用免责声明",fontSize=17.sp,color=Ink,fontWeight=FontWeight.Medium)
            TeaSmall("本 App 用于个人记账与消费规划。统计、储蓄估算和购买建议依赖你填写的数据，仅供参考，不构成投资、借贷或专业财务建议，也不保证购买或还款能力。")
            TeaSmall("请以实际账户余额、账单和有效合同为准。漏记、错记或系统后台调度延迟可能影响结果；重要支出与还款请自行核对。")
        }
        TeaCard {
            Text("自动记账与数据",fontSize=17.sp,color=Ink,fontWeight=FontWeight.Medium)
            TeaSmall("固定收支仅自动生成本地账本记录，可撤销；不会读取银行或支付流水，也不会发起真实扣款、转账或还款。后台执行与提醒可能受系统省电限制，重新打开 App 时会补记已到期记录。")
            TeaSmall("个人账本与示例账本分开保存在本机。请定期导出备份，妥善保存含有财务信息的文件；卸载或清除应用数据会删除本机账本。")
            TeaSmall("共同设计署名用于说明创作过程，不代表 OpenAI 对本 App 的官方发布或背书。头像由梨花怎黛玉提供。")
        }
    }
}
