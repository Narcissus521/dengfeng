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

/** Pages measured from their real content and font size. No vertical scrolling or hidden list tail. */
@Composable fun AutoPages(modifier: Modifier = Modifier, content: @Composable ColumnScope.()->Unit) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    var count by remember { mutableIntStateOf(1) }
    val density=LocalDensity.current
    val focusedCard=LocalResizingCard.current?.pageKey
    val keyboardOpen=WindowInsets.ime.getBottom(density)>0
    val windowSize=LocalWindowInfo.current.containerSize
    val keyboardScroll=rememberScrollState()
    LaunchedEffect(keyboardOpen) { if(!keyboardOpen)keyboardScroll.scrollTo(0) }
    Column(modifier.fillMaxSize()) {
        val scope=this
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            var closedHeight by remember(windowSize.width) { mutableStateOf(maxHeight) }
            SideEffect { if(!keyboardOpen)closedHeight=maxHeight }
            val pageHeight=if(keyboardOpen)maxOf(closedHeight,maxHeight) else maxHeight
            Box(Modifier.fillMaxSize().verticalScroll(keyboardScroll,enabled=keyboardOpen)) {
            Layout(content={scope.content()}, modifier=Modifier.fillMaxWidth().height(pageHeight).clip(RoundedCornerShape(0.dp))) { nodes, constraints ->
                val gap=10.dp.roundToPx()
                val items=nodes.map { it.measure(Constraints(maxWidth=constraints.maxWidth)) }
                fun groups(height: Int): List<List<Int>> {
                    val pages=mutableListOf<MutableList<Int>>();var used=0
                    items.forEachIndexed { i,p ->
                        if(pages.isEmpty() || (pages.last().isNotEmpty() && used+gap+p.height>height)) { pages+=mutableListOf<Int>();used=0 }
                        pages.last()+=i;used+=p.height+if(pages.last().size>1)gap else 0
                    }
                    return pages.ifEmpty { listOf(emptyList()) }
                }
                val preliminary=groups(constraints.maxHeight)
                val pages=if(preliminary.size>1)groups((constraints.maxHeight-46.dp.roundToPx()).coerceAtLeast(1)) else preliminary
                if(count!=pages.size)count=pages.size
                if(focusedCard!=null) {
                    val target=nodes.indexOfFirst { it.layoutId==focusedCard }
                    val targetPage=pages.indexOfFirst { target in it }
                    if(targetPage>=0 && index!=targetPage)index=targetPage
                }
                val selected=index.coerceIn(0,pages.lastIndex)
                layout(constraints.maxWidth,constraints.maxHeight) {
                    var y=0
                    pages[selected].forEach { i -> items[i].placeRelative(0,y);y+=items[i].height+gap }
                }
            }
            }
            if(count>1)Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(44.dp).teaGlass(Card,radius=22f),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick={index=(index-1).coerceAtLeast(0)},enabled=focusedCard==null&&index>0,modifier=Modifier.testTag("pagePrevious")){Text("上一页",fontSize=12.sp)}
                Text("${index.coerceAtMost(count-1)+1} / $count",fontSize=11.sp,color=Muted)
                TextButton(onClick={index=(index+1).coerceAtMost(count-1)},enabled=focusedCard==null&&index<count-1,modifier=Modifier.testTag("pageNext")){Text("下一页",fontSize=12.sp)}
            }
        }
    }
}

@Composable fun TeaCard(modifier: Modifier=Modifier, tint: androidx.compose.ui.graphics.Color=Card, onClick:(()->Unit)?=null, padding:androidx.compose.ui.unit.Dp=12.dp, spacing:androidx.compose.ui.unit.Dp=6.dp, card:OverviewCard?=null, content:@Composable ColumnScope.()->Unit) {
    val request=LocalCardResizeRequest.current
    val editing=LocalResizingCard.current
    val gridDrag=LocalGridDragActions.current
    val density=LocalDensity.current
    val percent=if(card!=null&&LocalGridCard.current==null)LocalDisplayState.current.cardPercent(card) else 100
    val shape=RoundedCornerShape(27.dp)
    val motion=LocalCardMotion.current
    var bounds by remember {mutableStateOf(Rect.Zero)}
    CompositionLocalProvider(LocalDensity provides Density(density.density*percent/100f,density.fontScale)) {
        val radius=with(LocalDensity.current){27.dp.toPx()}
        val click:()->Unit={if(editing!=null&&card!=null)request?.invoke(card) else {motion?.capture(bounds,card,radius);onClick?.invoke()}}
        Column(modifier.fillMaxWidth().then(if(card!=null)Modifier.layoutId(card.pageKey).testTag("card-${card.name}") else Modifier)
            .onGloballyPositioned {bounds=it.boundsInRoot();if(card!=null)motion?.rectangles?.set(card,CardOrigin(bounds,card,radius))}
            .pointerInput(card){awaitEachGesture{awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial);motion?.capture(bounds,card,radius)}}
            .graphicsLayer{alpha=if(card!=null&&motion?.origin?.key==card&&motion.phase==CardPhase.CLOSING)motion.sourceReveal else 1f}
            .teaGlass(tint)
            .then(if(card!=null&&editing==card)Modifier.border(1.5.dp,Forest,shape) else Modifier)
            .then(if(card!=null&&request!=null&&gridDrag!=null)Modifier.gridCardDrag(card,gridDrag)
                .semantics{onLongClick("移动或调整${card.title}"){request(card);true}}
                .clickable(interactionSource=remember{MutableInteractionSource()},indication=null,onClick=click)
            else if(card!=null&&request!=null)Modifier.combinedClickable(
                interactionSource=remember{MutableInteractionSource()},indication=null,onClick=click,
                onLongClickLabel="调整${card.title}大小",onLongClick={request(card)})
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
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick=onClick).padding(vertical=8.dp,horizontal=5.dp),verticalAlignment=Alignment.CenterVertically) {
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
    BackHandler { if(!busy)close() }
        Surface(Modifier.fillMaxSize(),color=androidx.compose.ui.graphics.Color.Transparent) {
            Column(Modifier.fillMaxSize().then(if(embedded)Modifier else Modifier.systemBarsPadding().then(if(Build.VERSION.SDK_INT<30)Modifier else Modifier.imePadding()))) {
                if(!embedded) {
                Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    Glyph("leaf",Forest,Modifier.size(19.dp));Text(stringResource(R.string.app_name),fontSize=16.sp,color=Ink)
                }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal=13.dp),verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick=close,enabled=!busy,contentPadding=PaddingValues(8.dp)){Text("‹ 返回",fontSize=13.sp)}
                    Text(title,Modifier.weight(1f).testTag("form-title"),fontSize=23.sp,fontWeight=FontWeight.Medium,color=Ink,maxLines=2)
                }
                key(title) { AutoPages(Modifier.weight(1f).padding(horizontal=18.dp,vertical=10.dp),content) }
                error?.let { Text(it,Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=6.dp),fontSize=12.sp,color=Red) }
                if(confirm!=null)Button(onClick={try{error=null;submit()}catch(e:Exception){error=e.message ?: "请检查输入"}},enabled=!busy,
                    modifier=Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=8.dp).height(49.dp).testTag("form-submit"),shape=RoundedCornerShape(25.dp)) {
                    Text(if(busy)"正在保存…" else confirm)
                }
                if(!embedded&&dock!=null)dock()
            }
        }
}

