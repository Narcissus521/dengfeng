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
    val appContext=androidx.compose.ui.platform.LocalContext.current
    val version=androidx.compose.runtime.remember(appContext){appContext.packageManager.getPackageInfo(appContext.packageName,0).versionName ?: ""}
    Form("关于与说明",close=close,confirm=null) {
        TeaCard(tint=LocalWalletColors.current.hero) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Image(painterResource(R.drawable.designer_avatar),contentDescription="梨花怎黛玉的头像",
                    contentScale=ContentScale.Crop,modifier=Modifier.size(76.dp).clip(CircleShape))
                Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("梨花怎黛玉",fontSize=20.sp,color=Ink,fontWeight=FontWeight.Medium)
                    TeaSmall("产品构想 · 交互与视觉共创")
                    TeaSmall("${stringResource(R.string.app_name)} $version")
                }
            }
            Text("由梨花怎黛玉与 OpenAI Codex 共同设计",fontSize=14.sp,color=Ink,fontWeight=FontWeight.Medium)
            TeaSmall("梨花怎黛玉提出产品需求、选择与打磨设计；OpenAI Codex 提供 AI 辅助设计、代码实现和测试支持。")
        }
        TeaCard {
            Text("记下今天，照顾未来",fontSize=17.sp,color=Ink)
            TeaSmall("像翻开一本清楚的账本，让花钱有记录，让储蓄有方向。")
            TeaArt("steep",Modifier.fillMaxWidth().height(68.dp))
        }
        TeaCard {
            Text("使用免责声明",fontSize=17.sp,color=Ink,fontWeight=FontWeight.Medium)
            disclaimerParagraphs.take(2).forEach{TeaSmall(it)}
        }
        TeaCard {
            Text("确认记账与数据",fontSize=17.sp,color=Ink,fontWeight=FontWeight.Medium)
            disclaimerParagraphs.drop(2).forEach{TeaSmall(it)}
            TeaSmall("共同设计署名用于说明创作过程，不代表 OpenAI 对本 App 的官方发布或背书。头像由梨花怎黛玉提供。")
        }
    }
}
