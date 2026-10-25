package com.campusfinance.wallet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import android.graphics.RectF
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.roundToInt

internal class FluidDockController(initial:Float,count:Int) {
    val physics=LiquidDockPhysics(initial,count-1)
    var frame by mutableStateOf(physics.frame)
    var revision by mutableIntStateOf(0)
    fun request(target:Float,pressed:Boolean,point:Offset,dragging:Boolean){if(physics.request(target,pressed,point.x,point.y,dragging)){frame=physics.frame;revision++}}
    fun pulse(){physics.pulse();revision++}
}

/** A single lens covers icon and label; nothing commits while a finger passes another tab. */
@Composable fun FluidDock(labels:List<String>,icons:List<String>,selectedIndex:Int,onSelect:(Int)->Unit,modifier:Modifier=Modifier) {
    val motion=LocalDisplayState.current;val cardMotion=LocalCardMotion.current
    val dark=LocalWalletColors.current.dark;val ink=Ink;val muted=Muted;val tea=Lime;val page=Page
    val density=LocalDensity.current.density
    val scene=LocalOpticalScene.current
    val pageMotion=LocalPageMotion.current
    val painter=remember{OpticalGlassPainter()}
    val anchor=remember{GlassAnchor()}
    val controller=remember(labels.size){FluidDockController(selectedIndex.toFloat(),labels.size)}
    val selectedNow by rememberUpdatedState(selectedIndex);val selectNow by rememberUpdatedState(onSelect)
    var drag by remember {mutableStateOf<Float?>(null)}
    var pressed by remember {mutableStateOf(false)}
    var pressedSlot by remember {mutableIntStateOf(selectedIndex)}
    var point by remember {mutableStateOf(Offset((selectedIndex+.5f)/labels.size,.35f))}
    var width by remember {mutableIntStateOf(1)}
    val linked=pageMotion?.takeIf{it.syncDock}?.position?.let(::pageDockSlot)
    val target=drag ?: if(pressed)pressedSlot.toFloat() else linked ?: selectedIndex.toFloat()
    val light=if(pressed)point else Offset((selectedIndex+.5f)/labels.size,.3f)
    SideEffect {controller.request(target,pressed,light,drag!=null||linked!=null)}
    LaunchedEffect(controller,motion.motionEnabled) {
        snapshotFlow{controller.revision}.collectLatest {
            if(!motion.motionEnabled){controller.physics.snap(reducedMotion=true);controller.frame=controller.physics.frame}
            else {
                var previous=0L;var elapsed=0f
                do {
                    withFrameNanos { now->
                        val dt=if(previous==0L)1f/120f else ((now-previous)/1_000_000_000f).coerceAtMost(.05f)
                        previous=now;elapsed+=dt;controller.physics.step(dt);controller.frame=controller.physics.frame
                    }
                }while(!controller.physics.settled&&elapsed<1.8f)
                if(elapsed>=1.8f){controller.physics.snap();controller.frame=controller.physics.frame}
            }
        }
    }
    val shape=RoundedCornerShape(33.dp)
    val inset=4f*density
    fun slot(x:Float)=((x-inset)/(width-2*inset).coerceAtLeast(1f)*labels.size-.5f).coerceIn(0f,labels.lastIndex.toFloat())
    Box(modifier.fillMaxWidth().height(66.dp)
        .shadow(10.dp,shape,clip=false,ambientColor=ink.copy(alpha=.06f),spotColor=ink.copy(alpha=.08f)).clip(shape)
        .onSizeChanged{width=it.width}
        .pointerInput(labels.size){
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                pressed=true;pressedSlot=slot(down.position.x).roundToInt()
                point=Offset(down.position.x/size.width.coerceAtLeast(1),down.position.y/size.height.coerceAtLeast(1))
                try {
                    var held=true
                    while(held){val event=awaitPointerEvent(PointerEventPass.Final);val primary=event.changes.firstOrNull{it.id==down.id};held=primary?.pressed==true
                        if(primary!=null)point=Offset(primary.position.x/size.width.coerceAtLeast(1),primary.position.y/size.height.coerceAtLeast(1))}
                } finally {pressed=false}
            }
        }
        .pointerInput(labels.size,inset){
            detectHorizontalDragGestures(onDragStart={drag=slot(it.x)},onDragCancel={drag=null},onDragEnd={
                val destination=(drag ?: selectedNow.toFloat()).roundToInt().coerceIn(0,labels.lastIndex);drag=null;selectNow(destination)
            }){change,_->change.consume();drag=slot(change.position.x)}
        }.testTag("dock-drag")) {
        Canvas(Modifier.matchParentSize().onPlaced{anchor.coordinates=it}) {
            val f=controller.frame;val lens=dockLens(f,size.width,size.height,density,labels.size)
            drawIntoCanvas { canvas ->
                painter.draw(canvas.nativeCanvas,scene,anchor,RectF(0f,0f,size.width,size.height),size.height/2,density,dark,page.toArgb(),if(dark).36f else .34f,.8f)
                val rect=RectF(lens.left,lens.top,lens.left+lens.width,lens.top+lens.height)
                // Both optical passes use the identical capsule, including hit feedback and rim.
                painter.draw(canvas.nativeCanvas,scene,anchor,rect,minOf(lens.width,lens.height)/2,density,dark,tea.toArgb(),if(dark).23f else .20f,1.6f,f.lift)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=4.dp,vertical=6.dp)) {
            labels.forEachIndexed { i,label ->
                val active=if(drag!=null)drag!!.roundToInt()==i else selectedIndex==i
                Column(Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(24.dp))
                    .onGloballyPositioned{if(i==2)cardMotion?.leafOrigin=CardOrigin(it.boundsInRoot(),null,18f*density)}
                    .semantics{selected=active}.clickable(interactionSource=remember{MutableInteractionSource()},indication=null){if(motion.motionEnabled)controller.pulse();selectNow(i)}
                    .testTag("dock-$label"),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(1.dp,Alignment.CenterVertically)) {
                    Box(Modifier.height(29.dp).graphicsLayer {
                        val f=controller.frame;val proximity=(1-abs(i-(f.head*.82f+f.tail*.18f))).coerceIn(0f,1f)
                        val scale=1+proximity*(.018f+f.lift*.055f);scaleX=scale;scaleY=scale
                    },contentAlignment=Alignment.Center){Glyph(icons[i],if(active||i==2)ink else muted,Modifier.size(if(i==2)29.dp else 20.dp))}
                    Text(label,fontSize=11.sp,lineHeight=14.sp,style=TextStyle(platformStyle=PlatformTextStyle(includeFontPadding=false)),color=if(active)ink else muted,maxLines=1)
                }
            }
        }
    }
}
