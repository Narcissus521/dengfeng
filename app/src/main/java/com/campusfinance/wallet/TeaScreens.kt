package com.campusfinance.wallet

import androidx.compose.foundation.Canvas
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import java.time.YearMonth

@Composable fun TeaDock(selected:Int,navigate:(Int)->Unit,record:()->Unit,leafSelected:Boolean=false) {
    val slot=if(leafSelected)2 else if(selected<2)selected else selected+1
    GlassSelector(listOf("首页","账本","记账","规划","分析"),slot,{if(it==2)record() else navigate(if(it<2)it else it-1)},
        Modifier.padding(horizontal=10.dp,vertical=7.dp),listOf("home","wallet","leaf","plan","chart"))
}

@Composable fun TeaShell(s:Ledger,month:String,onMonth:(String)->Unit,tab:Int,onTab:(Int)->Unit,open:(Editor)->Unit,leaf:()->Unit,embedded:Boolean=false,active:Boolean=true) {
    val motion=LocalDisplayState.current
    val pageStates=rememberSaveableStateHolder()
    var resizing by remember { mutableStateOf<OverviewCard?>(null) }
    LaunchedEffect(tab,active){resizing=null}
    BackHandler(enabled=active&&resizing!=null){resizing=null}
    Column(Modifier.fillMaxSize().then(if(embedded)Modifier else Modifier.teaWallpaper().systemBarsPadding())) {
        if(!embedded) {
        Row(Modifier.fillMaxWidth().height(50.dp).padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            Glyph("leaf",Forest,Modifier.size(19.dp));Text(stringResource(R.string.app_name),Modifier.weight(1f),fontSize=16.sp,color=Ink)
        }
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            CompositionLocalProvider(LocalTeaCompact provides (maxHeight<620.dp),LocalResizingCard provides resizing,
                LocalCardResizeRequest provides {resizing=it},LocalCardResizeDone provides {resizing=null}) {
                Box(Modifier.fillMaxSize().padding(horizontal=17.dp)) {
                    pageStates.SaveableStateProvider(tab) {
                        when(tab) {
                            0->TeaHome(s,currentMonth(),open,onTab)
                            1->TeaLedger(s,currentMonth(),onMonth,open)
                            2->TeaPlanning(s,currentMonth(),open)
                            else->TeaAnalytics(s,month,onMonth,open)
                        }
                    }
                }
            }
        }
        if(!embedded)TeaDock(tab,{resizing=null;onTab(it)},{resizing=null;leaf()})
    }
}

@Composable private fun TeaHome(s:Ledger,month:String,open:(Editor)->Unit,navigate:(Int)->Unit) = GridTeaHome(s,month,open)

@Composable fun MonthPicker(month:String,onMonth:(String)->Unit) {
    val ym=YearMonth.parse(month)
    Row(Modifier.fillMaxWidth().height(40.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
        TextButton(onClick={onMonth(ym.minusMonths(1).toString())},contentPadding=PaddingValues(6.dp)){Text("‹",fontSize=22.sp)}
        TextButton(onClick={onMonth(currentMonth())}){Text("${ym.year} 年 ${ym.monthValue} 月",fontSize=13.sp,color=Ink)}
        TextButton(onClick={onMonth(ym.plusMonths(1).toString())},contentPadding=PaddingValues(6.dp)){Text("›",fontSize=22.sp)}
    }
}

@Composable private fun TeaLedger(s:Ledger,month:String,onMonth:(String)->Unit,open:(Editor)->Unit) = GridTeaLedger(s,month,open)
@Composable private fun TeaPlanning(s:Ledger,month:String,open:(Editor)->Unit) = GridTeaPlanning(s,month,open)

fun analysisStats(s:Ledger,month:String,range:String): SpendingPeriod {
    val ym=YearMonth.parse(month)
    return if(range=="year")s.spending(ym.withMonth(12),12) else s.spending(ym,if(range=="month")1 else range.toInt())
}

@Composable private fun TeaAnalytics(s:Ledger,month:String,onMonth:(String)->Unit,open:(Editor)->Unit) = GridTeaAnalytics(s,month,onMonth,open)

@Composable fun TeaDonut(stats:SpendingPeriod,dual:Boolean,bucket:Boolean,modifier:Modifier,compact:Boolean=false) {
    val palette=stats.categories.keys.map(::expenseChartColor)
    val inner=Necessity.entries.map{expenseChartColor(it.name)}
    val track=Pale
    Box(modifier.semantics{contentDescription=if(dual)"外圈消费种类，内圈必要、非必要与未判断消费，均按${if(stats.chartUsesGross)"消费原额" else "净消费"}计算" else "消费去向圆环"},contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            fun ring(values:List<Long>,colors:List<Color>,inset:Float,width:Float) {
                val total=values.exactSum();val bounds=Size(size.width-inset*2,size.height-inset*2)
                if(total<=0){drawArc(track,0f,360f,false,Offset(inset,inset),bounds,style=Stroke(width));return}
                var angle=-90f
                values.forEachIndexed { i,value -> val sweep=(value.toDouble()/total*360).toFloat();drawArc(colors[i%colors.size],angle+.6f,(sweep-1.2f).coerceAtLeast(0f),false,Offset(inset,inset),bounds,style=Stroke(width,cap=StrokeCap.Butt));angle+=sweep }
            }
            ring((if(bucket&&!dual)stats.necessities else stats.categories).values.toList(),if(bucket&&!dual)stats.necessities.keys.map(::expenseChartColor) else palette,size.width*12/165,size.width*19/165)
            if(dual)ring(Necessity.entries.map{stats.necessities[it.name] ?: 0},inner,size.width*38/165,size.width*15/165)
        }
        Column(horizontalAlignment=Alignment.CenterHorizontally) {
            TeaSmall(if(compact)"消费" else if(stats.chartUsesGross)"消费原额" else "净消费")
            if(!compact)Text("¥${Money.format(stats.chartTotal)}",fontSize=if(stats.chartTotal>=10000000)10.sp else 15.sp,color=Ink,maxLines=1)
        }
    }
}
