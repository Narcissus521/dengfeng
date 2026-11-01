package com.campusfinance.wallet

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Original approved paper colours, including the surrounding shading that makes the fold visible. */
internal data class PaperFinish(val high:Color,val middle:Color,val low:Color,val edge:Color,val shade:Color)
@Composable internal fun paperFinish():PaperFinish = if(LocalWalletColors.current.dark)
    PaperFinish(Color(0xFF454137),Color(0xFF36332C),Color(0xFF292720),Color(0xFF716852),Color.Black.copy(alpha=.22f))
else PaperFinish(Color(0xFFFFFCF3),Color(0xFFF9F4E6),Color(0xFFEDE5D2),Color(0xFFC7BEA8),Color(0xFF4B3F2B).copy(alpha=.15f))

/** Static native drawing: no sampling, runtime shader, bitmap backdrop or idle animation. */
@Composable fun Modifier.ledgerMaterial(tint:Color=Card,radius:Float=8f,fold:Boolean=false,inset:Boolean=false,button:Boolean=false,pressed:Boolean=false):Modifier {
    val dark=LocalWalletColors.current.dark
    val paper=paperFinish();val edge=if(fold)paper.edge else MaterialEdge
    val primaryKey=button&&tint==Forest
    val high=if(primaryKey)lerp(tint,Color(0xFFFFFDF8),.12f) else if(fold)paper.high else lerp(tint,if(dark)Color(0xFF777165) else Color(0xFFFFFDF8),if(button).3f else .65f)
    val low=if(primaryKey)lerp(tint,Color(0xFF472731),.15f) else if(fold)paper.low else lerp(tint,if(dark)Color(0xFF151513) else Color(0xFFC9C5B8),if(button).28f else .13f)
    val shine=if(dark)Color(0xFFBEB39C).copy(alpha=.16f) else Color(0xFFFFFDF9)
    val grain=if(dark)Color(0xFFD8CBAF).copy(alpha=.025f) else Color(0xFF745D39).copy(alpha=.025f)
    val shape=RoundedCornerShape(radius.dp)
    return drawWithCache {
        val r=CornerRadius(radius.dp.toPx())
        val hair=1.dp.toPx();val bounds=Size(size.width,size.height)
        val fill=if(fold)Brush.linearGradient(0f to paper.high,.8f to paper.middle,1f to paper.low,start=Offset.Zero,end=Offset(size.width,size.height*.55f))
            else Brush.verticalGradient(if(inset||pressed)listOf(low,high) else listOf(high,low))
        val foldSize=minOf(16.dp.toPx(),size.width*.15f,size.height*.2f)
        val foldOrigin=Offset(size.width-foldSize,size.height-foldSize)
        val paperOutline=Path().apply{addRoundRect(RoundRect(Rect(Offset.Zero,size),r))}
        val flip=Brush.linearGradient(0f to paper.middle,.48f to paper.middle,.50f to paper.edge,.57f to paper.high,1f to paper.low,
            start=foldOrigin,end=Offset(size.width,size.height))
        onDrawWithContent {
            if(fold) {
                for(layer in 2 downTo 1) {
                    val offset=layer*2.dp.toPx()
                    drawRoundRect(paper.edge,Offset(0f,offset),bounds,r)
                    drawRoundRect(paper.high,Offset(hair,offset),Size((size.width-2*hair).coerceAtLeast(0f),size.height),r)
                }
            } else if(button&&!pressed&&!inset)drawRoundRect(edge,Offset(0f,2.dp.toPx()),bounds,r)
            drawRoundRect(fill,cornerRadius=r)
            drawRoundRect(edge,Offset(hair*.5f,hair*.5f),Size((size.width-hair).coerceAtLeast(0f),(size.height-hair).coerceAtLeast(0f)),r,style=Stroke(hair))
            drawLine(if(inset||pressed)paper.shade else shine,Offset(radius.dp.toPx(),hair),Offset((size.width-radius.dp.toPx()).coerceAtLeast(0f),hair),hair)
            if(fold) {
                var y=3.dp.toPx()
                while(y<size.height) {drawLine(grain,Offset(hair,y),Offset(size.width-hair,y),hair*.5f);y+=3.dp.toPx()}
                drawLine(paper.shade,Offset(hair*2,hair*2),Offset(hair*2,size.height-hair*2),hair*2)
            }
            drawContent()
            if(fold)clipPath(paperOutline){drawRect(flip,foldOrigin,Size(foldSize,foldSize))}
        }
    }.clip(shape)
}

val MaterialEdge:Color @Composable get()=if(LocalWalletColors.current.dark)Color(0xFF645D4E) else Color(0xFFCCC6B8)
val HeaderInk:Color @Composable get()=Color(0xFFF4F3EF)
@Composable fun Modifier.ledgerHeader():Modifier {
    val top=if(LocalWalletColors.current.dark)Color(0xFF303334) else Color(0xFF47494B)
    return drawWithCache {
        val fill=Brush.verticalGradient(listOf(top,top))
        onDrawBehind {
            drawRect(fill)
            val step=3.dp.toPx();var x=0f;var y=0f
            while(x<size.width){drawLine(Color.White.copy(alpha=.025f),Offset(x,0f),Offset(x,size.height),.5.dp.toPx());x+=step}
            while(y<size.height){drawLine(Color.Black.copy(alpha=.04f),Offset(0f,y),Offset(size.width,y),.5.dp.toPx());y+=step}
        }
    }
}

/** Physical segments retain tap, drag-to-select and page-swipe synchronisation. */
@Composable internal fun LedgerSelector(labels:List<String>,selectedIndex:Int,onSelect:(Int)->Unit,modifier:Modifier=Modifier,icons:List<String>?=null) {
    val isDock=icons!=null
    val activity=LocalContext.current as? MainActivity
    val cardMotion=LocalCardMotion.current;val pageMotion=LocalPageMotion.current
    val density=androidx.compose.ui.platform.LocalDensity.current
    val keyRadius=with(density){7.dp.toPx()}
    val onSelectNow by rememberUpdatedState(onSelect)
    var drag by remember{mutableStateOf<Float?>(null)}
    var width by remember{mutableIntStateOf(1)}
    val linked=if(isDock)pageMotion?.takeIf{it.syncDock}?.position?.let(::pageDockSlot) else null
    val visual=(drag ?: linked ?: selectedIndex.toFloat()).roundToInt().coerceIn(0,labels.lastIndex)
    if(isDock)SideEffect{activity?.activeDockSlot=selectedIndex}
    val base=if(isDock)Pale else Card
    Row(modifier.fillMaxWidth().onSizeChanged{width=it.width}.ledgerMaterial(base,if(isDock)0f else 6f,inset=!isDock)
        .pointerInput(labels.size){detectHorizontalDragGestures(onDragStart={drag=(it.x/width*labels.size-.5f).coerceIn(0f,labels.lastIndex.toFloat())},
            onDragCancel={drag=null},onDragEnd={val slot=drag?.roundToInt()?.coerceIn(0,labels.lastIndex);drag=null;if(slot!=null)onSelectNow(slot)}){change,_->change.consume();drag=(change.position.x/width*labels.size-.5f).coerceIn(0f,labels.lastIndex.toFloat())}}
        .testTag(if(isDock)"ledger-dock" else "tabs-${labels.joinToString("-")}").padding(if(isDock)6.dp else 2.dp),horizontalArrangement=Arrangement.spacedBy(if(isDock)4.dp else 1.dp)) {
        labels.forEachIndexed{i,label->
            val interactions=remember{MutableInteractionSource()};val pressed by interactions.collectIsPressedAsState()
            val motion=LocalDisplayState.current
            val lift by animateFloatAsState(if(pressed)1f else 0f,tween(motion.duration(100)),label="key press")
            val compose=isDock&&i==2
            val active=i==visual
            val color=if(compose)Forest else if(active)Lime else base
            val foreground=if(compose)MaterialThemeOnPrimary else if(active)Forest else Muted
            Column(Modifier.weight(1f).height(if(isDock)56.dp else 40.dp)
                .graphicsLayer{translationY=lift*1.dp.toPx()}
                .onGloballyPositioned{if(isDock){val rect=it.boundsInRoot();cardMotion?.dockRectangles?.set(i,rect);if(i==2)cardMotion?.leafOrigin=CardOrigin(rect,null,keyRadius)}}
                .ledgerMaterial(color,6f,inset=active&&!compose,button=true,pressed=pressed)
                .semantics{selected=if(isDock)i==selectedIndex else active}
                .clickable(interactionSource=interactions,indication=null,role=Role.Tab){onSelectNow(i)}
                .then(if(isDock)Modifier.testTag("dock-$label") else Modifier),
                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp,Alignment.CenterVertically)) {
                if(icons!=null)Glyph(if(compose)"edit" else icons[i],foreground,Modifier.size(if(compose)23.dp else 20.dp))
                Text(label,fontSize=if(isDock)10.sp else 11.sp,color=foreground,maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=if(active||compose)FontWeight.Medium else FontWeight.Normal)
            }
        }
    }
}
val MaterialThemeOnPrimary:Color @Composable get()=androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
