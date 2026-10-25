package com.campusfinance.wallet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onPlaced
import android.graphics.RectF
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass

/** Soft, static light behind translucent surfaces. No perpetual render loop or text blur. */
@Composable fun Modifier.teaWallpaper():Modifier {
    if(LocalOpticalScene.current!=null)return this
    val page=Page;val tea=Lime;val warm=if(LocalWalletColors.current.dark)Color(0xFF625D43) else Color(0xFFE7DEC7)
    return background(page).drawWithCache {
        val mist=Brush.radialGradient(listOf(tea.copy(alpha=.65f),Color.Transparent),Offset(size.width*1.08f,size.height*.26f),size.width*.88f)
        val bottom=Brush.radialGradient(listOf(warm.copy(alpha=.23f),Color.Transparent),Offset(size.width*.1f,size.height*.9f),size.width*.9f)
        onDrawBehind { drawRect(mist);drawRect(bottom) }
    }
}

@Composable fun Modifier.teaGlass(tint:Color=Card,radius:Float=27f):Modifier {
    val dark=LocalWalletColors.current.dark
    val scene=LocalOpticalScene.current
    val painter=remember{OpticalGlassPainter()}
    val anchor=remember{GlassAnchor()}
    val density=LocalDensity.current.density
    return clip(RoundedCornerShape(radius.dp)).onPlaced{anchor.coordinates=it}.drawWithContent {
        drawIntoCanvas{painter.draw(it.nativeCanvas,scene,anchor,RectF(0f,0f,size.width,size.height),radius.dp.toPx(),density,dark,tint.toArgb(),if(dark).60f else .60f)}
        drawContent()
    }
}

/** Drag previews only; selecting a page or opening the leaf menu happens once on release. */
@Composable fun GlassSelector(labels:List<String>,selectedIndex:Int,onSelect:(Int)->Unit,modifier:Modifier=Modifier,icons:List<String>?=null) {
    if(icons!=null){FluidDock(labels,icons,selectedIndex,onSelect,modifier);return}
    val motion=LocalDisplayState.current
    val ink=Ink;val muted=Muted;val tea=Lime;val dark=LocalWalletColors.current.dark
    val selectedNow by rememberUpdatedState(selectedIndex)
    val selectNow by rememberUpdatedState(onSelect)
    var drag by remember { mutableStateOf<Float?>(null) }
    var pressed by remember{mutableStateOf(false)}
    var pressedSlot by remember{mutableIntStateOf(selectedIndex)}
    var width by remember { mutableIntStateOf(1) }
    val dragging=drag!=null
    val controller=remember(labels.size){FluidDockController(selectedIndex.toFloat(),labels.size)}
    val scene=LocalOpticalScene.current
    val painter=remember{OpticalGlassPainter()}
    val anchor=remember{GlassAnchor()}
    val density=LocalDensity.current.density
    val target=drag ?: if(pressed)pressedSlot.toFloat() else selectedIndex.toFloat()
    SideEffect{controller.request(target,pressed,Offset((target+.5f)/labels.size,.3f),dragging)}
    LaunchedEffect(controller,motion.motionEnabled){snapshotFlow{controller.revision}.collectLatest {
        if(!motion.motionEnabled){controller.physics.snap(true);controller.frame=controller.physics.frame}
        else {var last=0L;var elapsed=0f
            do{withFrameNanos{now->val dt=if(last==0L)1f/120f else ((now-last)/1e9f).coerceAtMost(.05f);last=now;elapsed+=dt;controller.physics.step(dt);controller.frame=controller.physics.frame}}while(!controller.physics.settled&&elapsed<1.8f)
            if(elapsed>=1.8f){controller.physics.snap();controller.frame=controller.physics.frame}
        }
    }}
    val shape=RoundedCornerShape(1000.dp)
    val inset=with(androidx.compose.ui.platform.LocalDensity.current){4.dp.toPx()}
    fun slot(x:Float)=((x-inset)/(width-2*inset).coerceAtLeast(1f)*labels.size-.5f).coerceIn(0f,labels.lastIndex.toFloat())
    Box(modifier.fillMaxWidth().clip(shape).onSizeChanged {width=it.width}
        .pointerInput(labels.size){awaitEachGesture{val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial);pressedSlot=slot(down.position.x).roundToInt();pressed=true
            try{do{val event=awaitPointerEvent(PointerEventPass.Final)}while(event.changes.any{it.pressed})}finally{pressed=false}
        }}
        .pointerInput(labels.size,inset){
            detectHorizontalDragGestures(onDragStart={drag=slot(it.x)},onDragCancel={drag=null},onDragEnd={
                val current=drag ?: selectedNow.toFloat();drag=null;selectNow(current.roundToInt().coerceIn(0,labels.lastIndex))
            }) { change,_-> change.consume();drag=slot(change.position.x) }
        }.testTag("tabs-${labels.joinToString("-")}")) {
        Canvas(Modifier.matchParentSize().onPlaced{anchor.coordinates=it}) {
            val f=controller.frame
            val step=(size.width-8.dp.toPx())/labels.size
            val display=f.head*.85f+f.tail*.15f
            val lag=kotlin.math.abs(f.head-f.tail).coerceAtMost(1f)
            val w=(step-3.dp.toPx()+lag*step*.22f).coerceAtMost(size.width-4.dp.toPx())
            val h=(size.height-8.dp.toPx()+f.lift*1.4.dp.toPx()).coerceAtMost(size.height-4.dp.toPx())
            val x=(4.dp.toPx()+step*(display+.5f)-w/2).coerceIn(2.dp.toPx(),(size.width-w-2.dp.toPx()).coerceAtLeast(2.dp.toPx()))
            val y=(size.height-h)/2
            drawIntoCanvas{canvas->
                painter.draw(canvas.nativeCanvas,scene,anchor,RectF(0f,0f,size.width,size.height),size.height/2,density,dark,tea.toArgb(),if(dark).38f else .34f,.75f)
                painter.draw(canvas.nativeCanvas,scene,anchor,RectF(x,y,x+w,y+h),minOf(w,h)/2,density,dark,tea.toArgb(),if(dark).23f else .20f,1.5f,f.lift)
            }
        }
        Row(Modifier.fillMaxWidth().padding(4.dp),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
            labels.forEachIndexed { i,label ->
                val active=if(dragging)(drag!!).roundToInt()==i else selectedIndex==i
                Column(Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(18.dp))
                    .semantics{selected=active}.clickable(interactionSource=remember{MutableInteractionSource()},indication=null){selectNow(i)},
                    verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(label,fontSize=11.sp,color=if(active)ink else muted,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
            }
        }
    }
}
