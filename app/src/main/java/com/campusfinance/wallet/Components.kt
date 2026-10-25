package com.campusfinance.wallet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun Glyph(name: String, color: Color = Forest, modifier: Modifier = Modifier.size(22.dp)) {
    Canvas(modifier) {
        val u = size.width / 24f
        fun line(x1:Float,y1:Float,x2:Float,y2:Float) = drawLine(color,Offset(x1*u,y1*u),Offset(x2*u,y2*u),1.7f*u,StrokeCap.Round)
        fun rect(x:Float,y:Float,w:Float,h:Float,r:Float=2f) = drawRoundRect(color,Offset(x*u,y*u),Size(w*u,h*u),CornerRadius(r*u),style=Stroke(1.7f*u))
        when(name) {
            "leaf" -> {
                val p=Path().apply{moveTo(5*u,18*u);cubicTo(3*u,8*u,11*u,3*u,20*u,3*u);cubicTo(22*u,13*u,15*u,21*u,5*u,18*u);close()}
                drawPath(p,color,style=Stroke(1.6f*u));line(4f,22f,15f,10f)
            }
            "repeat" -> {line(5f,7f,19f,7f);line(15f,3f,19f,7f);line(19f,7f,15f,11f);line(19f,17f,5f,17f);line(9f,13f,5f,17f);line(5f,17f,9f,21f)}
            "home" -> { rect(3f,3f,7f,7f);rect(14f,3f,7f,7f);rect(3f,14f,7f,7f);rect(14f,14f,7f,7f) }
            "wallet", "accounts" -> { rect(3f,5f,18f,15f,3f);line(4f,9f,20f,9f);rect(15f,12f,7f,5f,1f) }
            "plan" -> { rect(4f,4f,16f,17f);line(8f,2f,8f,6f);line(16f,2f,16f,6f);line(8f,11f,16f,11f);line(8f,15f,13f,15f) }
            "chart" -> { line(4f,20f,21f,20f);line(6f,15f,6f,19f);line(12f,10f,12f,19f);line(18f,4f,18f,19f) }
            "plus" -> { line(12f,5f,12f,19f);line(5f,12f,19f,12f) }
            "income" -> { line(12f,3f,12f,17f);line(7f,12f,12f,17f);line(17f,12f,12f,17f);line(5f,21f,19f,21f) }
            "expense" -> { line(12f,20f,12f,5f);line(7f,10f,12f,5f);line(17f,10f,12f,5f);line(5f,22f,19f,22f) }
            "transfer" -> { line(3f,8f,21f,8f);line(17f,4f,21f,8f);line(17f,12f,21f,8f);line(21f,17f,3f,17f);line(7f,13f,3f,17f);line(7f,21f,3f,17f) }
            "settings" -> {
                drawCircle(color,6.5f*u,style=Stroke(1.7f*u));drawCircle(color,2.3f*u,style=Stroke(1.7f*u))
                repeat(8){i->val a=i*Math.PI/4;line(12f+6.5f*kotlin.math.cos(a).toFloat(),12f+6.5f*kotlin.math.sin(a).toFloat(),12f+9f*kotlin.math.cos(a).toFloat(),12f+9f*kotlin.math.sin(a).toFloat())}
            }
            "goal" -> { drawCircle(color,9*u,style=Stroke(1.7f*u));drawCircle(color,5*u,style=Stroke(1.7f*u));drawCircle(color,1.5f*u) }
            "bag" -> { rect(5f,7f,14f,14f);drawArc(color,180f,180f,false,Offset(8*u,2*u),Size(8*u,10*u),style=Stroke(1.7f*u)) }
            "food" -> { line(5f,3f,5f,10f);line(9f,3f,9f,10f);line(7f,3f,7f,21f);line(5f,10f,9f,10f);line(17f,3f,17f,21f);line(15f,3f,15f,11f);line(15f,11f,17f,11f) }
            "book" -> { rect(4f,3f,16f,18f);line(8f,3f,8f,21f);line(11f,8f,17f,8f);line(11f,12f,17f,12f) }
            "check" -> { line(5f,12f,10f,17f);line(10f,17f,20f,6f) }
            "arrow" -> { line(4f,12f,20f,12f);line(14f,6f,20f,12f);line(14f,18f,20f,12f) }
            else -> { drawCircle(color,8*u,style=Stroke(1.7f*u));line(12f,7f,12f,13f);drawCircle(color,u,Offset(12*u,17*u)) }
        }
    }
}
@Composable fun TileIcon(name: String, tint: Color = Forest, bg: Color = Pale) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(15.dp)).background(bg),contentAlignment=Alignment.Center) { Glyph(name,tint) }
}
@Composable fun CardBox(modifier: Modifier = Modifier, color: Color = Card, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val interactions=remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    val motion=LocalDisplayState.current
    val scale by animateFloatAsState(if(pressed && motion.motionEnabled) .985f else 1f,tween(motion.duration(if(pressed)85 else 160)),label="card press")
    Column(modifier.fillMaxWidth().graphicsLayer { scaleX=scale;scaleY=scale }.teaGlass(color,24f)
        .then(if (onClick != null) Modifier.clickable(interactionSource=interactions,indication=LocalIndication.current,onClick=onClick) else Modifier).padding(20.dp), verticalArrangement=Arrangement.spacedBy(14.dp), content=content)
}
@Composable fun Headline(title: String, action: String? = null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
        Text(title,fontSize=18.sp,fontWeight=FontWeight.SemiBold,color=Ink)
        if(action!=null) TextButton(onClick=onClick,contentPadding=PaddingValues(horizontal=0.dp)) { Text(action,fontSize=12.sp) }
    }
}
@Composable fun Caption(text: String, color: Color = Muted) { Text(text,color=color,fontSize=12.sp,lineHeight=19.sp) }
@Composable fun Pill(text: String, color: Color = Forest, bg: Color = Pale) {
    Text(text,Modifier.clip(RoundedCornerShape(7.dp)).background(bg).padding(horizontal=8.dp,vertical=4.dp),fontSize=10.sp,color=color,fontWeight=FontWeight.Medium)
}
@Composable fun Notice(text: String, warning: Boolean = false) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if(warning) LocalWalletColors.current.warning else Pale).padding(12.dp),horizontalArrangement=Arrangement.spacedBy(9.dp)) {
        Glyph("info",if(warning) Amber else Forest,Modifier.size(17.dp)); Text(text,fontSize=11.sp,lineHeight=18.sp,color=if(warning) Amber else Forest)
    }
}
@Composable fun Progress(used: Long, total: Long, color: Color = Forest) {
    val fraction = if(total>0) (used.toDouble()/total).coerceIn(0.0,1.0).toFloat() else if(used>0) 1f else 0f
    val motion=LocalDisplayState.current
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered=true }
    val displayed by animateFloatAsState(if(entered) fraction else 0f,tween(motion.duration(420),easing=FastOutSlowInEasing),label="budget progress")
    Box(Modifier.fillMaxWidth().height(7.dp).clip(CircleShape).background(Pale)) {
        Box(Modifier.fillMaxWidth(displayed).fillMaxHeight().clip(CircleShape).background(if(used>total) Red else color))
    }
}
@Composable fun AmountLine(label: String, amount: Long, color: Color = Ink) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
        Text(label,fontSize=13.sp,color=Muted);AnimatedMoney(amount,color=color)
    }
}
@Composable fun EmptyState(title: String, detail: String, action: String? = null, onClick: () -> Unit = {}) {
    CardBox {
        TileIcon("book");Text(title,fontWeight=FontWeight.SemiBold,fontSize=17.sp);Caption(detail)
        if(action!=null) Button(onClick=onClick,shape=RoundedCornerShape(13.dp)) { Text(action) }
    }
}
@Composable fun <T> ChoiceRow(items: List<T>, selected: T, label: (T)->String, onSelect:(T)->Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        items.forEach { item -> FilterChip(selected=item==selected,onClick={onSelect(item)},label={Text(label(item),fontSize=12.sp)}) }
    }
}
@Composable fun BucketChoiceRow(selected:String,onSelect:(String)->Unit) {
    val scheme=MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Bucket.entries.forEach { bucket ->
            val checked=bucket.name==selected
            FilterChip(selected=checked,onClick={onSelect(bucket.name)},
                label={Text(bucket.label,fontSize=12.sp,fontWeight=if(checked)FontWeight.SemiBold else FontWeight.Normal)},
                leadingIcon={Box(Modifier.size(16.dp)){if(checked)Glyph("check",scheme.onPrimary,Modifier.fillMaxSize())}},
                colors=FilterChipDefaults.filterChipColors(containerColor=Card,labelColor=Muted,
                    selectedContainerColor=scheme.primary,selectedLabelColor=scheme.onPrimary,selectedLeadingIconColor=scheme.onPrimary),
                border=BorderStroke(if(checked)1.5.dp else 1.dp,if(checked)scheme.primary else scheme.outlineVariant))
        }
    }
}
fun entryTitle(e: Entry, s: Ledger): String = when(e.kind) {
    "TRANSFER" -> "${s.accountName(e.accountId)} → ${s.accountName(e.toAccountId)}"
    "REFUND" -> "${s.expenseCategory(e).let{if(it.group.children.isEmpty())it.group.name else it.leaf.name}}退款"
    "ADJUSTMENT" -> "余额校准"
    "REPAYMENT" -> "负债"
    "EXPENSE" -> s.expenseCategory(e).let{if(it.group.children.isEmpty())it.group.name else it.leaf.name}
    else -> e.category
}
@Composable fun EntryRow(e: Entry, s: Ledger, onClick: () -> Unit) {
    val positive = e.kind in listOf("INCOME","REFUND") || (e.kind=="ADJUSTMENT" && e.amount>0)
    val tint = when { e.voided -> Muted; positive -> Forest; e.kind=="TRANSFER" -> Blue; else -> Ink }
    val icon = when(e.kind) { "INCOME","REFUND"->"income";"TRANSFER"->"transfer";"REPAYMENT"->"plan";else->when(e.category){"餐饮"->"food";"学习"->"book";else->"bag"} }
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        TileIcon(icon,tint)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(entryTitle(e,s),fontSize=14.sp,fontWeight=FontWeight.Medium,color=if(e.voided) Muted else Ink,maxLines=1)
            Caption("${e.date.substring(5)} · ${s.accountName(e.accountId)}${if(e.voided) " · 已撤销" else ""}")
        }
        Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("${if(e.kind=="TRANSFER") "" else if(positive) "+" else "−"}${Money.format(kotlin.math.abs(e.amount))}",fontSize=15.sp,fontWeight=FontWeight.SemiBold,color=tint)
            Caption(if(e.kind in listOf("EXPENSE","REFUND")) Bucket.valueOf(s.bucketOf(e)).label else Kind.valueOf(e.kind).label)
        }
    }
}
