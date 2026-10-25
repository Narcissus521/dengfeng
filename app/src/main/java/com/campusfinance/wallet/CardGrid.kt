package com.campusfinance.wallet

import androidx.compose.foundation.Canvas
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.floor
import kotlin.math.roundToInt

data class GridCardInfo(val layout:CardGridLayout,val width:Dp,val height:Dp) {
    val narrow get()=layout.columns==2
    val compact get()=layout.rows==1
    val detailed get()=layout.rows>=3
}
val LocalGridCard=staticCompositionLocalOf<GridCardInfo?>{null}
val LocalCardResizeDone=staticCompositionLocalOf<()->Unit>{{}}
data class GridDragActions(val start:()->Unit,val move:(Offset)->Unit,val finish:()->Unit,val cancel:()->Unit)
val LocalGridDragActions=staticCompositionLocalOf<GridDragActions?>{null}
private data class GridDraft(val card:OverviewCard,val layout:CardGridLayout,val before:Map<OverviewCard,CardGridLayout>)
private data class GridMove(val card:OverviewCard,val origin:CardGridLayout,val offset:Offset=Offset.Zero)

/** Remains installed throughout the gesture, including the first long press that enters edit mode. */
@Composable fun Modifier.gridCardDrag(card:OverviewCard,actions:GridDragActions):Modifier {
    val latest by rememberUpdatedState(actions)
    val haptic=LocalHapticFeedback.current
    return pointerInput(card) {
        detectDragGesturesAfterLongPress(onDragStart={haptic.performHapticFeedback(HapticFeedbackType.LongPress);latest.start()},
            onDragEnd={latest.finish()},onDragCancel={latest.cancel()}) { change,amount->change.consume();latest.move(amount) }
    }
}

/** Fixed viewport, independent two-dimensional cells, with real content measured at native density. */
@Composable fun OverviewGrid(cards:List<OverviewCard>,modifier:Modifier=Modifier,minimumRow:Dp=72.dp,content:@Composable (OverviewCard,GridCardInfo)->Unit) {
    val settings=LocalDisplayState.current
    val controller=LocalDisplayController.current
    val editing=LocalResizingCard.current
    val density=LocalDensity.current
    val select=LocalCardResizeRequest.current
    var page by rememberSaveable{mutableIntStateOf(0)}
    var draft by remember{mutableStateOf<GridDraft?>(null)}
    var moving by remember{mutableStateOf<GridMove?>(null)}
    LaunchedEffect(editing){if(draft?.card!=editing)draft=null;if(moving?.card!=editing)moving=null}
    LaunchedEffect(settings.cardGrids){draft?.let{if(settings.cardGrids[it.card]==it.layout)draft=null}}
    BoxWithConstraints(modifier.fillMaxSize()) {
        val gap=10.dp
        val columnUnit=((maxWidth-gap*3)/4).coerceAtLeast(1.dp)
        val usable=(maxHeight-64.dp).coerceAtLeast(1.dp)
        val typeMinimum=(minimumRow.value*density.fontScale.coerceAtLeast(1f)).dp
        val rowUnit=minOf(columnUnit,((usable-gap*6)/7).coerceAtLeast(1.dp)).coerceAtLeast(typeMinimum)
        val rows=floor((usable+gap)/(rowUnit+gap)+.0001f).toInt().coerceIn(1,8)
        val saved=settings.cardGrids+draft?.before.orEmpty()+listOfNotNull(draft?.let{it.card to it.layout}).toMap()
        val placements=CardGridPolicy.pack(cards,saved,rows,draft?.card)
        val pages=placements.maxOfOrNull{it.layout.page}?.plus(1) ?: 1
        val current=page.coerceIn(0,pages-1)
        val stepX=with(density){(columnUnit+gap).toPx()};val stepY=with(density){(rowUnit+gap).toPx()}
        LaunchedEffect(editing,cards){editing?.let{card->placements.find{it.card==card}?.let{page=it.layout.page}}}
        fun preview(card:OverviewCard,value:CardGridLayout){
            val before=draft?.takeIf{it.card==card}?.before ?: placements.associate{it.card to it.layout}
            draft=GridDraft(card,value.normalized(rows),before);page=value.page
        }
        fun commit() {
            val v=draft ?: return
            val result=CardGridPolicy.pack(cards,settings.cardGrids+v.before+(v.card to v.layout),rows,v.card)
            val normalized=result.associate{it.card to it.layout}
            draft=v.copy(layout=normalized.getValue(v.card))
            controller?.setCardGrids(normalized)
        }
        fun change(card:OverviewCard,value:CardGridLayout){preview(card,value);commit()}
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Box(Modifier.fillMaxSize().testTag("overview-grid")) {
                    placements.forEach { item->key(item.card) {
                        val v=item.layout
                        val drag=moving?.takeIf{it.card==item.card}
                        val x=if(drag==null)v.column*stepX else drag.origin.column*stepX+drag.offset.x
                        val y=if(drag==null)v.row*stepY else drag.origin.row*stepY+drag.offset.y
                        val target=Rect(x,y,x+with(density){(columnUnit*v.columns+gap*(v.columns-1)).toPx()},
                            y+with(density){(rowUnit*v.rows+gap*(v.rows-1)).toPx()})
                        val actions=GridDragActions(start={
                            select?.invoke(item.card);moving=GridMove(item.card,v)
                        },move={amount->
                            moving?.takeIf{it.card==item.card}?.let { old->
                                val delta=old.offset+amount
                                val offset=Offset(delta.x.coerceIn(-old.origin.column*stepX,(4-old.origin.columns-old.origin.column)*stepX),
                                    delta.y.coerceIn(-old.origin.row*stepY,(rows-old.origin.rows-old.origin.row)*stepY))
                                moving=old.copy(offset=offset)
                                val next=CardGridPolicy.move(old.origin,offset.x,offset.y,stepX,stepY,rows)
                                if(next!=old.origin||draft!=null)preview(item.card,next)
                            }
                        },finish={commit();moving=null},cancel={draft=null;moving=null})
                        GridAnimatedCard(target,v,item.card,visible=v.page==current,selected=editing==item.card,moving=drag!=null,
                            motion=settings.motionEnabled) { info->
                            CompositionLocalProvider(LocalGridCard provides info,LocalGridDragActions provides actions) {
                                Box(Modifier.fillMaxSize()) {
                                    content(item.card,info)
                                    if(editing==item.card)GridResizeHandles(item.card,v,rows,stepX,stepY,
                                        preview={preview(item.card,it)},finish={commit()},cancel={draft=null},adjust={change(item.card,it)})
                                }
                            }
                        }
                    } }
                }
            }
            Column(Modifier.fillMaxWidth().height(64.dp)) {
                if(editing!=null&&editing in cards) {
                    val v=placements.first{it.card==editing}.layout
                    CardResizeControls(editing,v,reset={change(editing,CardGridPolicy.default(editing).copy(column=v.column,row=v.row,page=v.page))},done=LocalCardResizeDone.current)
                } else Spacer(Modifier.height(36.dp))
                if(pages>1)Row(Modifier.fillMaxWidth().height(28.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                    TextButton(onClick={page=(current-1).coerceAtLeast(0)},enabled=current>0,contentPadding=PaddingValues(0.dp),modifier=Modifier.testTag("grid-previous")){Text("上一页",fontSize=11.sp)}
                    Text("${current+1} / $pages",fontSize=11.sp,color=Muted,modifier=Modifier.testTag("grid-pages"))
                    TextButton(onClick={page=(current+1).coerceAtMost(pages-1)},enabled=current<pages-1,contentPadding=PaddingValues(0.dp),modifier=Modifier.testTag("grid-next")){Text("下一页",fontSize=11.sp)}
                }
            }
        }
    }
}

/** Geometry is measured each frame; money and text reflow instead of being stretched. */
@Composable private fun GridAnimatedCard(target:Rect,value:CardGridLayout,card:OverviewCard,visible:Boolean,selected:Boolean,moving:Boolean,
    motion:Boolean,content:@Composable (GridCardInfo)->Unit) {
    val density=LocalDensity.current
    val shadow=Forest
    val geometry=remember{Animatable(target,Rect.VectorConverter)}
    LaunchedEffect(target,moving,motion) {
        if(moving||!motion)geometry.snapTo(target) else geometry.animateTo(target,tween(230,easing=FastOutSlowInEasing))
    }
    val rendered=if(moving||!motion)target else geometry.value
    val elevation by animateFloatAsState(if(!visible)0f else if(moving)10f else if(selected)3f else 0f,tween(if(motion)180 else 0),label="card lift")
    val width=with(density){rendered.width.coerceAtLeast(1f).toDp()};val height=with(density){rendered.height.coerceAtLeast(1f).toDp()}
    Box(Modifier.offset{IntOffset(rendered.left.roundToInt(),rendered.top.roundToInt())}.size(width,height)
        .zIndex(if(moving)3f else if(selected)2f else 0f)
        .drawBehind {
            // Draw a quiet round halo without an elevated offscreen layer over translucent glass.
            if(elevation>0f)for(i in 4 downTo 1) {
                val spread=i.dp.toPx();val lift=elevation.dp.toPx()*.25f
                drawRoundRect(shadow.copy(alpha=(elevation/10f)*.025f),Offset(-spread,lift-spread),
                    Size(size.width+spread*2,size.height+spread*2),CornerRadius(27.dp.toPx()+spread),style=Stroke(spread*1.5f))
            }
        }
        .testTag("grid-placement-${card.name}")) {
        AnimatedVisibility(visible,enter=fadeIn(tween(if(motion)160 else 0)),exit=fadeOut(tween(if(motion)120 else 0)),modifier=Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().then(if(visible)Modifier else Modifier.pointerInput(Unit){awaitPointerEventScope{while(true)awaitPointerEvent(PointerEventPass.Initial).changes.forEach{it.consume()}}})) {
                content(GridCardInfo(value,width,height))
            }
        }
    }
}

@Composable private fun BoxScope.GridResizeHandles(card:OverviewCard,value:CardGridLayout,capacity:Int,stepX:Float,stepY:Float,
    preview:(CardGridLayout)->Unit,finish:()->Unit,cancel:()->Unit,adjust:(CardGridLayout)->Unit) {
    val current by rememberUpdatedState(value)
    val previewNow by rememberUpdatedState(preview)
    val finishNow by rememberUpdatedState(finish)
    val cancelNow by rememberUpdatedState(cancel)
    val adjustNow by rememberUpdatedState(adjust)
    val tint=Forest
    for(edge in listOf("left","right","bottom")) {
        val horizontal=edge!="bottom"
        val target=if(horizontal)Modifier.align(if(edge=="left")Alignment.CenterStart else Alignment.CenterEnd).size(44.dp,48.dp)
            else Modifier.align(Alignment.BottomCenter).size(48.dp,44.dp)
        Box(target.testTag("resize-${card.name}-$edge").semantics {
            contentDescription="${card.title}${when(edge){"left"->"左";"right"->"右";else->"下"}}边缘，拖动调整"
            stateDescription="${value.columns}×${value.rows}格"
            customActions=listOf(-1,1).map { direction->CustomAccessibilityAction(if(horizontal)if(direction>0)"加宽一格" else "缩窄一格" else if(direction>0)"加高一格" else "降低一格") {
                val v=current;val n=if(horizontal)v.copy(columns=(v.columns+direction).coerceIn(2,4),column=if(edge=="left")(v.column-direction).coerceAtLeast(0) else v.column)
                    else v.copy(rows=(v.rows+direction).coerceIn(1,minOf(4,capacity)))
                adjustNow(n);true
            }}
        }.pointerInput(card,edge,stepX,stepY,capacity) {
            var base=current;var delta=Offset.Zero
            detectDragGestures(onDragStart={base=current;delta=Offset.Zero},onDragEnd={finishNow()},onDragCancel={cancelNow()}) { change,amount->
                change.consume();delta+=amount
                val maxWidth=if(edge=="left")base.column+base.columns else 4-base.column
                val w=if(horizontal)CardGridPolicy.snap(base.columns,if(edge=="left")-delta.x else delta.x,stepX,2,maxWidth.coerceAtLeast(2)) else base.columns
                val h=if(horizontal)base.rows else CardGridPolicy.snap(base.rows,delta.y,stepY,1,minOf(4,capacity))
                previewNow(base.copy(columns=w,rows=h,column=if(edge=="left")base.column+base.columns-w else base.column,row=base.row.coerceAtMost(capacity-h)))
            }
        }) {
            Canvas(Modifier.matchParentSize()) {
                if(horizontal){val x=if(edge=="left")2.dp.toPx() else size.width-8.dp.toPx();drawRoundRect(tint,Offset(x,(size.height-22.dp.toPx())/2),Size(6.dp.toPx(),22.dp.toPx()),CornerRadius(4.dp.toPx()))}
                else drawRoundRect(tint,Offset((size.width-24.dp.toPx())/2,size.height-8.dp.toPx()),Size(24.dp.toPx(),6.dp.toPx()),CornerRadius(4.dp.toPx()))
            }
        }
    }
}
