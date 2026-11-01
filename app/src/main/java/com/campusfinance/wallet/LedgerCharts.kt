package com.campusfinance.wallet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/** Same segment geometry and palette; restrained thickness, inset track and fine bevel. */
internal fun DrawScope.ledgerRing(values:List<Pair<Color,Long>>,diameter:Float,x:Float,y:Float,inset:Float,width:Float,track:Color) {
    val hair=1.dp.toPx()
    val origin=Offset(x+inset,y+inset);val bounds=Size(diameter-2*inset,diameter-2*inset)
    drawArc(Color.Black.copy(alpha=.12f),0f,360f,false,origin+Offset(0f,2*hair),bounds,style=Stroke(width+2*hair))
    drawArc(track,0f,360f,false,origin,bounds,style=Stroke(width))
    val total=values.map{it.second}.exactSum();if(total<=0)return
    var angle=-90f
    values.forEach{(color,amount)->
        val sweep=(amount*360.0/total).toFloat();val start=angle+.5f;val arc=(sweep-1f).coerceAtLeast(0f)
        val face=Brush.verticalGradient(listOf(lerp(color,Color.White,.18f),color,lerp(color,Color.Black,.15f)),startY=y,endY=y+diameter)
        drawArc(face,start,arc,false,origin,bounds,style=Stroke(width))
        val rim=(width/2-hair).coerceAtLeast(0f)
        drawArc(Color.White.copy(alpha=.26f),start,arc,false,origin-Offset(rim,rim),Size(bounds.width+rim*2,bounds.height+rim*2),style=Stroke(hair))
        drawArc(Color.Black.copy(alpha=.18f),start,arc,false,origin+Offset(rim,rim),Size(bounds.width-rim*2,bounds.height-rim*2),style=Stroke(hair))
        angle+=sweep
    }
}
