package com.campusfinance.wallet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale

/** Quiet native vector scenes. Steam drifts once; it never keeps an idle screen redrawing. */
@Composable fun TeaArt(scene:String,modifier:Modifier=Modifier) {
    val dark=LocalWalletColors.current.dark
    val motion=LocalDisplayState.current.motionEnabled
    val drift=remember(scene) { Animatable(0f) }
    LaunchedEffect(scene,motion) { if(motion)drift.animateTo(1f,tween(3800)) else drift.snapTo(0f) }
    val porcelain=if(dark)Color(0xFFD7DED4) else Color(0xFFFDFEF9)
    val shade=if(dark)Color(0xFFAEBBA8) else Color(0xFFE2E9DD)
    val line=if(dark)Color(0xFF71816D) else Color(0xFFA5B69D)
    val tea=if(dark)Color(0xFF98A579) else Color(0xFFCFD2A1)
    val leaf=if(dark)Color(0xFFAEC5A0) else Color(0xFF849A7B)
    val tray=if(dark)Color(0xFF3C4642) else Color(0xFFE6E8DD)
    val stove=if(dark)Color(0xFF6D766D) else Color(0xFFE4DFD1)
    Canvas(modifier) {
        val factor=minOf(size.width/150f,size.height/100f)
        scale(factor,pivot=Offset.Zero) {
            fun steam(x:Float,y:Float,small:Boolean=false) {
                val move=drift.value
                val shift=Offset(move*4f,-move*3f)
                val p=Path().apply {
                    moveTo(x+shift.x,y+shift.y)
                    cubicTo(x+1+shift.x,y-6+shift.y,x+9+shift.x,y-10+shift.y,x+8+shift.x,y-16+shift.y)
                    cubicTo(x+7+shift.x,y-19+shift.y,x+14+shift.x,y-21+shift.y,x+13+shift.x,y-25+shift.y)
                }
                drawPath(p,leaf.copy(alpha=if(small).17f else .29f),style=Stroke(if(small).9f else 1.1f,cap=StrokeCap.Round))
            }
            fun cup(x:Float,y:Float,w:Float=28f) {
                drawRoundRect(shade,Offset(x,y),Size(w,16f),CornerRadius(12f))
                drawOval(porcelain,Offset(x,y-4),Size(w,9f))
                drawOval(tea,Offset(x+3,y-1),Size(w-6,4f))
                drawRoundRect(shade,Offset(x+w*.3f,y+13),Size(w*.4f,3f),CornerRadius(2f))
                steam(x+w*.68f,y-4);steam(x+w*.9f,y-5,true)
            }
            fun pot(x:Float,y:Float) {
                drawOval(shade,Offset(x-14,y+5),Size(24f,25f),style=Stroke(4.5f))
                val spout=Path().apply {moveTo(x+46,y+12);lineTo(x+72,y+8);lineTo(x+70,y+14);lineTo(x+48,y+24);close()}
                drawPath(spout,shade)
                drawRoundRect(porcelain,Offset(x,y),Size(53f,38f),CornerRadius(21f))
                drawRoundRect(line.copy(alpha=.65f),Offset(x,y),Size(53f,38f),CornerRadius(21f),style=Stroke(.8f))
                drawOval(porcelain,Offset(x+6,y-5),Size(40f,10f));drawOval(line.copy(alpha=.7f),Offset(x+6,y-5),Size(40f,10f),style=Stroke(.8f))
                drawOval(shade,Offset(x+22,y-10),Size(8f,7f))
                rotate(-30f,pivot=Offset(x+28,y+17)){drawOval(leaf.copy(alpha=.45f),Offset(x+23,y+14),Size(11f,5f))}
            }
            drawOval(tray,Offset(13f,84f),Size(124f,8f))
            when(scene) {
                "pour","share" -> {
                    rotate(24f,pivot=Offset(47.5f,35f)) { pot(21f,16f) }
                    drawLine(tea,Offset(90.5f,47f),Offset(90.5f,if(scene=="share")63f else 65f),2f,StrokeCap.Round)
                    cup(80f,if(scene=="share")63f else 65f,if(scene=="share")25f else 30f)
                    if(scene=="share")cup(111f,68f,25f)
                }
                "simmer" -> {
                    drawRoundRect(stove,Offset(57f,62f),Size(38f,24f),CornerRadius(8f))
                    drawCircle(Color(0xFFC2A87C).copy(alpha=.8f),4f,Offset(76f,74f))
                    pot(51f,26f);steam(69f,17f);steam(84f,15f,true)
                }
                else -> {
                    drawRoundRect(porcelain,Offset(49f,50f),Size(58f,29f),CornerRadius(22f))
                    drawOval(porcelain,Offset(49f,45f),Size(58f,14f));drawOval(tea,Offset(53f,49f),Size(50f,6f))
                    rotate(-7f,pivot=Offset(49f,43f)){drawOval(porcelain,Offset(49f,39f),Size(58f,10f));drawOval(shade,Offset(73f,34f),Size(9f,7f))}
                    steam(70f,31f);steam(85f,28f,true)
                }
            }
            rotate(22f,pivot=Offset(31f,82f)){drawOval(leaf.copy(alpha=.45f),Offset(22f,79f),Size(17f,6f));drawOval(line.copy(alpha=.5f),Offset(35f,75f),Size(13f,5f))}
        }
    }
}
