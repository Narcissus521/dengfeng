package com.campusfinance.wallet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

internal class GlassPageMotion(initial:Int) {
    val animated=Animatable(initial.toFloat())
    var drag by mutableStateOf<Float?>(null)
    var released by mutableStateOf<Float?>(null)
    var syncDock by mutableStateOf(false)
    var settleRevision by mutableIntStateOf(0)
    val position get()=drag ?: released ?: animated.value
}
internal val LocalPageMotion=staticCompositionLocalOf<GlassPageMotion?>{null}
internal fun pageDockSlot(page:Float)=when{page<=1f->page;page<=2f->1f+(page-1f)*2;else->page+1f}

/** Content only moves over the shared scene. No page bitmap, opacity crossfade or reset on retarget. */
@Composable internal fun GlassPages(selected:Int,state:GlassPageMotion,enabled:Boolean,onSwipe:(Int)->Unit,modifier:Modifier=Modifier,content:@Composable (Int)->Unit) {
    val motion=LocalDisplayState.current
    val scene=LocalOpticalScene.current
    val selectNow by rememberUpdatedState(onSwipe)
    val selectedNow by rememberUpdatedState(selected)
    val dragging=state.drag!=null
    LaunchedEffect(selected,dragging,state.settleRevision,motion.motionEnabled) {
        if(!dragging){
            state.released?.let{state.animated.snapTo(it);state.released=null}
            scene?.pulse()
            state.animated.animateTo(selected.toFloat(),tween(motion.duration(390),easing=FastOutSlowInEasing))
            state.syncDock=false
        }
    }
    BoxWithConstraints(modifier.clipToBounds()) {
        val width=with(LocalDensity.current){maxWidth.toPx()}.coerceAtLeast(1f)
        val position=state.position.coerceIn(0f,3f)
        val pages=listOf(floor(position).toInt(),ceil(position).toInt()).distinct()
        val gesture=if(enabled)Modifier.pointerInput(width) {
            var start=0f;var dx=0f
            detectHorizontalDragGestures(onDragStart={start=state.position;dx=0f;state.drag=start;state.syncDock=true},onDragCancel={state.released=state.drag;state.drag=null;state.settleRevision++},onDragEnd={
                val current=state.drag ?: selectedNow.toFloat();state.released=current;state.drag=null
                selectNow(current.roundToInt().coerceIn(0,3));state.syncDock=true;state.settleRevision++
            }){change,amount->change.consume();dx+=amount;state.drag=(start-dx/width).coerceIn(0f,3f)}
        } else Modifier
        Box(Modifier.fillMaxSize().then(gesture)) {
            pages.forEach{page->key(page){Box(Modifier.fillMaxSize().graphicsLayer{translationX=(page-state.position)*size.width}){content(page)}}}
        }
    }
}
