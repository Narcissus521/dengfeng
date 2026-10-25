package com.campusfinance.wallet

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import android.graphics.RectF
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.Job
import kotlin.math.min

enum class CardPhase { CLOSED, OPENING, OPEN, CLOSING }
data class CardOrigin(val rect:Rect,val key:OverviewCard?,val radius:Float,val captured:Long=SystemClock.uptimeMillis())
class CardMotionState {
    val progress=Animatable(1f)
    var phase by mutableStateOf(CardPhase.CLOSED)
    var origin by mutableStateOf<CardOrigin?>(null)
    var bodyOffset by mutableStateOf(Offset.Zero)
    var openingReady by mutableStateOf(false)
    var pending:CardOrigin?=null
    var leafOrigin:CardOrigin?=null
    val rectangles=mutableMapOf<OverviewCard,CardOrigin>()
    var job:Job?=null
    // Restore the original card during the final part of the same shrink, never afterwards.
    val sourceReveal get()=if(phase==CardPhase.CLOSING)((.22f-progress.value)/.22f).coerceIn(0f,1f) else 0f
    fun capture(rect:Rect,key:OverviewCard?,radius:Float){if(!rect.isEmpty)pending=CardOrigin(rect,key,radius)}
    fun takeOrigin():CardOrigin?=pending?.takeIf{SystemClock.uptimeMillis()-it.captured<1200}.also{pending=null}
    fun reset(){job?.cancel();job=null;phase=CardPhase.CLOSED;origin=null;pending=null;openingReady=false}
}
val LocalCardMotion=staticCompositionLocalOf<CardMotionState?> { null }
val LocalEmbeddedForm=staticCompositionLocalOf { false }

/** A uniform round rect can use the native layer outline instead of a fresh full-window clip path. */
private data class EditorClipShape(val frame:Rect,val radius:Float):Shape {
    override fun createOutline(size:Size,layoutDirection:LayoutDirection,density:Density)=Outline.Rounded(RoundRect(frame,CornerRadius(radius)))
}

@Composable fun CardEditorLayer(state:CardMotionState,editor:Editor,content:@Composable ()->Unit) {
    val motion=LocalDisplayState.current
    val scene=LocalOpticalScene.current
    val painter=remember{OpticalGlassPainter()}
    val anchor=remember{GlassAnchor()}
    val dark=LocalWalletColors.current.dark
    val tint=Card
    val density=LocalDensity.current.density
    LaunchedEffect(motion.motionEnabled){if(!motion.motionEnabled && state.phase==CardPhase.OPENING){state.job?.cancel();state.progress.snapTo(1f);state.phase=CardPhase.OPEN}}
    fun frame(width:Float,height:Float):Rect {
        val target=Rect(0f,0f,width,height)
        val measured=state.origin?.key?.let{state.rectangles[it]} ?: state.origin
        val original=measured?.rect?.translate(-state.bodyOffset) ?: Rect(width*.08f,height*.18f,width*.92f,height*.65f)
        val t=state.progress.value
        return Rect(original.left+(target.left-original.left)*t,original.top+(target.top-original.top)*t,original.right+(target.right-original.right)*t,original.bottom+(target.bottom-original.bottom)*t)
    }
    Box(Modifier.fillMaxSize().testTag("card-editor-layer").graphicsLayer {
        val r=frame(size.width,size.height);val radius=(state.origin?.radius ?: 27.dp.toPx())*(1-state.progress.value)+28.dp.toPx()*state.progress.value
        shape=EditorClipShape(r,radius);clip=true;alpha=1-state.sourceReveal
    }.onPlaced{anchor.coordinates=it}.drawBehind {
        val r=frame(size.width,size.height)
        val radius=(state.origin?.radius ?: 27.dp.toPx())*(1-state.progress.value)+28.dp.toPx()*state.progress.value
        drawIntoCanvas{painter.draw(it.nativeCanvas,scene,anchor,RectF(r.left,r.top,r.right,r.bottom),radius,density,dark,tint.toArgb(),if(dark).62f else .65f)}
    }) {
        Box(Modifier.fillMaxSize().onGloballyPositioned{if(state.phase==CardPhase.OPENING)state.openingReady=true}.graphicsLayer {
            val r=frame(size.width,size.height);val ratio=min(r.width/size.width,r.height/size.height)
            alpha=if(state.phase==CardPhase.CLOSING)((ratio-.76f)/.24f).coerceIn(0f,1f) else ((ratio-.65f)/.23f).coerceIn(0f,1f)
        }){content()}
    }
}
