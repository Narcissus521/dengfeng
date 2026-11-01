package com.campusfinance.wallet

import android.view.WindowManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

val LocalTeaDock = staticCompositionLocalOf<(@Composable () -> Unit)?> { null }
val LocalTeaCompact = staticCompositionLocalOf { false }

/** Full forms and lists scroll naturally; all existing fields remain reachable at large font sizes. */
@Composable fun AutoPages(modifier: Modifier = Modifier, content: @Composable ColumnScope.()->Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("natural-scroll"),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
}

@Composable fun TeaCard(modifier: Modifier=Modifier, tint: androidx.compose.ui.graphics.Color=Card, onClick:(()->Unit)?=null, padding:androidx.compose.ui.unit.Dp=12.dp, spacing:androidx.compose.ui.unit.Dp=6.dp, card:OverviewCard?=null, content:@Composable ColumnScope.()->Unit) {
    val request=LocalCardResizeRequest.current
    val editing=LocalResizingCard.current
    val gridDrag=LocalGridDragActions.current
    val density=LocalDensity.current
    val percent=if(card!=null&&LocalGridCard.current==null)LocalDisplayState.current.cardPercent(card) else 100
    val shape=RoundedCornerShape(8.dp)
    val motion=LocalCardMotion.current
    val quickState=LocalLedgerQuickState.current
    var bounds by remember {mutableStateOf(Rect.Zero)}
    CompositionLocalProvider(LocalDensity provides Density(density.density*percent/100f,density.fontScale)) {
        val radius=with(LocalDensity.current){8.dp.toPx()}
        val click:()->Unit={if(quickState?.sourceClickAllowed()!=false){if(editing!=null&&card!=null)request?.invoke(card) else {motion?.capture(bounds,card,radius);onClick?.invoke()}}}
        Column(modifier.fillMaxWidth().then(if(card!=null)Modifier.layoutId(card.pageKey).testTag("card-${card.name}") else Modifier)
            .onGloballyPositioned {bounds=it.boundsInRoot();if(card!=null)motion?.rectangles?.set(card,CardOrigin(bounds,card,radius))}
            .pointerInput(card){awaitEachGesture{awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial);motion?.capture(bounds,card,radius)}}
            .ledgerMaterial(tint,8f,fold=card==OverviewCard.HOME_BUDGET)
            .then(if(card!=null&&editing==card)Modifier.border(1.5.dp,Forest,shape) else Modifier)
            .then(if(card!=null&&editing!=null&&request!=null&&gridDrag!=null)Modifier.gridCardDrag(card,gridDrag)
                .semantics{onLongClick("移动或调整${card.title}"){request(card);true}}
                .clickable(interactionSource=remember{MutableInteractionSource()},indication=null,onClick=click)
            else if(card!=null)Modifier.ledgerQuickActions(QuickTarget(card=card))
                .clickable(interactionSource=remember{MutableInteractionSource()},indication=null,onClick=click)
            else if(onClick!=null)Modifier.clickable(onClick=click) else Modifier).padding(padding),
            verticalArrangement=Arrangement.spacedBy(spacing),content=content)
    }
}
@Composable fun TeaSmall(text:String, modifier:Modifier=Modifier, color:androidx.compose.ui.graphics.Color=Muted) {
    Text(text,modifier,fontSize=11.sp,lineHeight=17.sp,color=color)
}
@Composable fun TeaTitle(title:String,subtitle:String,art:String?=null) {
    Row(Modifier.fillMaxWidth().padding(bottom=6.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            TeaSmall(subtitle);Text(title,fontSize=25.sp,fontWeight=FontWeight.Medium,color=Ink)
        }
        if(art!=null)TeaArt(art,Modifier.size(58.dp,45.dp))
    }
}
@Composable fun TeaLink(title:String,subtitle:String?=null,icon:String="arrow",onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick=onClick).padding(vertical=8.dp,horizontal=5.dp),verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){Text(title,fontSize=13.sp,color=Ink);if(subtitle!=null)TeaSmall(subtitle)}
        Glyph(icon,Muted,Modifier.size(17.dp))
    }
}
@Composable fun TeaTabs(labels:List<String>,selected:Int,onSelect:(Int)->Unit) {
    GlassSelector(labels,selected,onSelect)
}

@Composable fun PagedForm(title:String,busy:Boolean,close:()->Unit,confirm:String?,submit:()->Unit,content:@Composable ColumnScope.()->Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    val dock=LocalTeaDock.current
    val embedded=LocalEmbeddedForm.current
    BackHandler(enabled=LocalEditorPageActive.current) { if(!busy)close() }
        Surface(Modifier.fillMaxSize(),color=if(LocalQuickForm.current)Card else Page) {
            Column(Modifier.fillMaxSize().then(if(embedded)Modifier else Modifier.systemBarsPadding().then(if(Build.VERSION.SDK_INT<30)Modifier else Modifier.imePadding()))) {
                if(!embedded) {
                Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    Glyph("book",Forest,Modifier.size(19.dp));Text(stringResource(R.string.app_name),fontSize=16.sp,color=Ink)
                }
                }
                Row(Modifier.fillMaxWidth().heightIn(min=54.dp).then(if(LocalQuickForm.current)Modifier else Modifier.ledgerHeader()).padding(horizontal=13.dp),verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick=close,enabled=!busy,contentPadding=PaddingValues(8.dp)){Text("‹ 返回",fontSize=13.sp,color=if(LocalQuickForm.current)Forest else HeaderInk)}
                    Text(title,Modifier.weight(1f).testTag("form-title"),fontSize=if(LocalQuickForm.current)18.sp else 20.sp,fontWeight=FontWeight.Medium,color=if(LocalQuickForm.current)Ink else HeaderInk,maxLines=2)
                }
                key(title) { AutoPages(Modifier.weight(1f).padding(horizontal=18.dp,vertical=10.dp),content) }
                error?.let { Text(it,Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=6.dp),fontSize=12.sp,color=Red) }
                if(confirm!=null)Button(onClick={try{error=null;submit()}catch(e:Exception){error=e.message ?: "请检查输入"}},enabled=!busy,
                    modifier=Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=8.dp).height(49.dp).ledgerMaterial(Forest,8f,button=true).testTag("form-submit"),shape=RoundedCornerShape(8.dp),colors=ButtonDefaults.buttonColors(containerColor=androidx.compose.ui.graphics.Color.Transparent,contentColor=MaterialTheme.colorScheme.onPrimary)) {
                    Text(if(busy)"正在保存…" else confirm)
                }
                if(!embedded&&dock!=null)dock()
            }
        }
}

