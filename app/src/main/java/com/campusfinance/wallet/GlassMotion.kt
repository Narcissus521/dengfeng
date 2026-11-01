package com.campusfinance.wallet

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job

enum class CardPhase { CLOSED, OPENING, OPEN, CLOSING }
data class CardOrigin(val rect:Rect,val key:OverviewCard?,val radius:Float,val captured:Long=SystemClock.uptimeMillis())
class CardMotionState {
    val progress=Animatable(1f)
    var phase by mutableStateOf(CardPhase.CLOSED)
    var origin by mutableStateOf<CardOrigin?>(null)
    var bodyOffset by mutableStateOf(Offset.Zero)
    var openingReady by mutableStateOf(false)
    var entrancePrepared by mutableStateOf(false)
    var destinationBounds by mutableStateOf(Rect.Zero)
    var pending:CardOrigin?=null
    var leafOrigin:CardOrigin?=null
    val rectangles=mutableMapOf<OverviewCard,CardOrigin>()
    val dockRectangles=mutableMapOf<Int,Rect>()
    var job:Job?=null
    val fraction get()=if(phase==CardPhase.OPENING&&!entrancePrepared)0f else progress.value
    fun sourceInRoot():CardOrigin?=origin?.key?.let{rectangles[it]} ?: origin
    fun frameInRoot():Rect? {
        val target=destinationBounds.takeUnless{it.isEmpty} ?: return null
        val original=sourceInRoot()?.rect ?: target
        val t=fraction
        return Rect(original.left+(target.left-original.left)*t,original.top+(target.top-original.top)*t,original.right+(target.right-original.right)*t,original.bottom+(target.bottom-original.bottom)*t)
    }
    // Restore the original card during the final part of the same shrink, never afterwards.
    val sourceReveal get()=if(phase==CardPhase.CLOSING)((.22f-progress.value)/.22f).coerceIn(0f,1f) else 0f
    fun capture(rect:Rect,key:OverviewCard?,radius:Float){if(!rect.isEmpty)pending=CardOrigin(rect,key,radius)}
    fun takeOrigin():CardOrigin?=pending?.takeIf{SystemClock.uptimeMillis()-it.captured<1200}.also{pending=null}
    fun reset(){job?.cancel();job=null;phase=CardPhase.CLOSED;origin=null;pending=null;openingReady=false;entrancePrepared=false}
}
val LocalCardMotion=staticCompositionLocalOf<CardMotionState?> { null }
val LocalEmbeddedForm=staticCompositionLocalOf { false }
val LocalEditorPageActive=staticCompositionLocalOf { true }

internal val NavigationEasing=CubicBezierEasing(.2f,.8f,.2f,1f)

/** Keep the previous page underneath the incoming page; use layer translation, never a whole-page fade. */
fun Modifier.revealBehindEditor(state:CardMotionState)=graphicsLayer {
    translationX=if(state.phase==CardPhase.CLOSED)0f else -size.width*.24f*state.fraction
    compositingStrategy=if(state.phase==CardPhase.OPENING||state.phase==CardPhase.CLOSING)CompositingStrategy.Offscreen else CompositingStrategy.Auto
}

@Composable fun CardEditorLayer(state:CardMotionState,editor:Editor,content:@Composable ()->Unit) {
    val motion=LocalDisplayState.current
    LaunchedEffect(motion.motionEnabled){if(!motion.motionEnabled && state.phase==CardPhase.OPENING){state.job?.cancel();state.progress.snapTo(1f);state.phase=CardPhase.OPEN}}
    Box(Modifier.fillMaxSize().testTag("card-editor-layer").graphicsLayer {
        translationX=(1-state.fraction)*size.width
    }.drawWithCache {
        // A fixed narrow edge gradient avoids rasterising a full-window elevation shadow each frame.
        val width=12.dp.toPx()
        val edge=Brush.horizontalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.15f)),startX=-width,endX=0f)
        onDrawBehind{drawRect(edge,Offset(-width,0f),Size(width,size.height))}
    }) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            compositingStrategy=if(state.phase==CardPhase.OPENING||state.phase==CardPhase.CLOSING)CompositingStrategy.Offscreen else CompositingStrategy.Auto
        }.background(Page).onGloballyPositioned{if(state.phase==CardPhase.OPENING)state.openingReady=true}){content()}
    }
}
