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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job

enum class CardPhase { CLOSED, OPENING, OPEN, CLOSING }
private data class SharedCorners(val radius:CornerRadius):Shape {
    override fun createOutline(size:Size,layoutDirection:LayoutDirection,density:Density):Outline =
        Outline.Rounded(RoundRect(Rect(Offset.Zero,size),radius))
}
data class CardOrigin(val rect:Rect,val key:OverviewCard?,val radius:Float,val captured:Long=SystemClock.uptimeMillis(),val tint:Color?=null)
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
    val dockRectangles=mutableMapOf<Int,Rect>()
    var job:Job?=null
    val fraction get()=if(phase==CardPhase.OPENING&&!entrancePrepared)0f else progress.value
    val shared get()=origin?.tint != null
    fun frameInRoot():Rect? {
        val target=destinationBounds.takeUnless{it.isEmpty} ?: return null
        val original=origin?.rect ?: target
        val t=fraction
        return Rect(original.left+(target.left-original.left)*t,original.top+(target.top-original.top)*t,original.right+(target.right-original.right)*t,original.bottom+(target.bottom-original.bottom)*t)
    }
    fun capture(rect:Rect,key:OverviewCard?,radius:Float,tint:Color?=null){
        if(!rect.isEmpty)pending=CardOrigin(rect,key,radius,tint=tint)
    }
    fun takeOrigin():CardOrigin?=pending?.takeIf{SystemClock.uptimeMillis()-it.captured<1200}.also{pending=null}
    fun reset(){job?.cancel();job=null;phase=CardPhase.CLOSED;origin=null;pending=null;openingReady=false;entrancePrepared=false}
}
val LocalCardMotion=staticCompositionLocalOf<CardMotionState?> { null }
val LocalEmbeddedForm=staticCompositionLocalOf { false }
val LocalEditorPageActive=staticCompositionLocalOf { true }

internal val NavigationEasing=CubicBezierEasing(.2f,.8f,.2f,1f)

fun Modifier.revealBehindEditor(state:CardMotionState)=graphicsLayer {
    val progress=if(state.phase==CardPhase.CLOSED)0f else state.fraction
    val scale=if(state.shared)1f-.045f*progress else 1f
    scaleX=scale
    scaleY=scale
    translationX=if(state.shared)0f else -size.width*.24f*progress
}

@Composable fun CardEditorLayer(state:CardMotionState,content:@Composable ()->Unit) {
    val motion=LocalDisplayState.current
    val page=Page
    LaunchedEffect(motion.motionEnabled){if(!motion.motionEnabled && state.phase==CardPhase.OPENING){state.job?.cancel();state.progress.snapTo(1f);state.phase=CardPhase.OPEN}}
    val shared=state.shared
    val container=if(shared)Modifier.graphicsLayer {
        val frame=state.frameInRoot()
        if(frame!=null&&state.fraction<1f) {
            transformOrigin=TransformOrigin(0f,0f)
            scaleX=frame.width/size.width.coerceAtLeast(1f)
            scaleY=frame.height/size.height.coerceAtLeast(1f)
            translationX=frame.left-state.bodyOffset.x
            translationY=frame.top-state.bodyOffset.y
            val radius=(state.origin?.radius ?: 0f)*(1-state.fraction)
            shape=SharedCorners(CornerRadius(radius/scaleX.coerceAtLeast(.001f),radius/scaleY.coerceAtLeast(.001f)))
            clip=true
            alpha=(state.fraction/.12f).coerceIn(0f,1f)
        }
    }.drawWithCache {
        onDrawBehind { drawRect(lerp(state.origin?.tint ?: page,page,state.fraction)) }
    } else Modifier.graphicsLayer {
        translationX=(1-state.fraction)*size.width
    }.drawWithCache {
        // A fixed narrow edge gradient avoids rasterising a full-window elevation shadow each frame.
        val width=12.dp.toPx()
        val edge=Brush.horizontalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.15f)),startX=-width,endX=0f)
        onDrawBehind{drawRect(edge,Offset(-width,0f),Size(width,size.height))}
    }
    Box(Modifier.fillMaxSize().then(container).testTag("card-editor-layer")) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            if(shared)alpha=((state.fraction-.08f)/.78f).coerceIn(0f,1f)
        }.then(if(shared)Modifier else Modifier.background(page))
            .onGloballyPositioned{if(state.phase==CardPhase.OPENING)state.openingReady=true}){content()}
    }
}
