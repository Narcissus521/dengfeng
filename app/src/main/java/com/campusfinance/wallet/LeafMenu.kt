package com.campusfinance.wallet

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Keep the current page and its unfinished form composed underneath the overlay. */
@Composable fun LeafActionMenu(close:()->Unit,choose:(Editor)->Unit) {
    val motion=LocalDisplayState.current
    val entrance=remember { MutableTransitionState(false).apply{targetState=true} }
    BackHandler(onBack=close)
    Box(Modifier.fillMaxSize().testTag("leaf-overlay")) {
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha=.16f))
            .clickable(interactionSource=remember{MutableInteractionSource()},indication=null,onClick=close).testTag("leaf-dismiss"))
        Box(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal=18.dp).padding(bottom=96.dp),contentAlignment=Alignment.BottomCenter) {
            AnimatedVisibility(entrance,enter=fadeIn(tween(motion.duration(150)))+slideInVertically(tween(motion.duration(150))){it/5}) {
                Surface(Modifier.fillMaxWidth().teaGlass(Card,radius=26f).semantics{paneTitle="记录与设置"}.testTag("leaf-menu")
                    .clickable(interactionSource=remember{MutableInteractionSource()},indication=null){},
                    shape=RoundedCornerShape(26.dp),color=Color.Transparent,shadowElevation=0.dp) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                            Glyph("leaf",Forest,Modifier.size(23.dp));Spacer(Modifier.width(8.dp))
                            Text("记录与设置",Modifier.weight(1f),fontSize=17.sp,fontWeight=FontWeight.Medium)
                            TextButton(onClick=close,modifier=Modifier.testTag("leaf-close")){Text("关闭",fontSize=12.sp)}
                        }
                        listOf(listOf("EXPENSE","INCOME"),listOf("TRANSFER","SETTINGS")).forEach { row ->
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                                row.forEach { kind ->
                                    val title=when(kind){"EXPENSE"->"支出";"INCOME"->"收入";"TRANSFER"->"转账";else->"设置"}
                                    val icon=when(kind){"EXPENSE"->"expense";"INCOME"->"income";"TRANSFER"->"transfer";else->"settings"}
                                    Row(Modifier.weight(1f).teaGlass(if(kind=="EXPENSE")Lime else Card,radius=19f)
                                        .clickable {choose(if(kind=="SETTINGS")Editor("profile") else Editor("transaction",kind=kind))}
                                        .padding(horizontal=16.dp,vertical=20.dp).testTag("leaf-$kind"),
                                        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                        Glyph(icon,Forest,Modifier.size(23.dp));Text(title,fontSize=16.sp)
                                    }
                                }
                            }
                        }
                        TeaSmall("像每日一盏茶，慢慢养成记录习惯。")
                    }
                }
            }
        }
    }
}
