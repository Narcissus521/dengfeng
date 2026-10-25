package com.campusfinance.wallet

import android.graphics.*

/** A single full-window tea scene, shared by the wallpaper and all optical surfaces. */
internal fun makeOpticalTeaScene(width:Int,height:Int,density:Float,dark:Boolean):Bitmap {
    val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    fun dp(v:Float)=v*density
    fun c(hex:String)=Color.parseColor(hex)
        val b=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(b);val w=width.toFloat();val h=height.toFloat()
        paint.shader=LinearGradient(0f,0f,w*.8f,h,
            if(dark)intArrayOf(c("#17282C"),c("#344C46"),c("#203D36")) else intArrayOf(c("#F0F2E9"),c("#DFE7DC"),c("#C3D2C6")),floatArrayOf(0f,.52f,1f),Shader.TileMode.CLAMP)
        canvas.drawRect(0f,0f,w,h,paint)
        paint.shader=RadialGradient(w*.12f,h*.12f,w*.92f,if(dark)c("#476057") else c("#FFF8E8"),Color.TRANSPARENT,Shader.TileMode.CLAMP)
        canvas.drawRect(0f,0f,w,h,paint);paint.shader=null
        repeat(5){i->
            val path=Path();val y=h*(.41f+i*.12f)
            path.moveTo(-w*.1f,y+w*.2f)
            path.cubicTo(w*.27f,y-w*.22f,w*.62f,y+w*.2f,w*1.1f,y-w*.18f)
            path.lineTo(w*1.1f,h*1.1f);path.lineTo(-w*.1f,h*1.1f);path.close()
            paint.color=if(dark)Color.argb(16+i*5,17,42,34) else Color.argb(8+i*3,100,137,111)
            canvas.drawPath(path,paint)
            paint.color=if(dark)Color.argb(16,190,217,188) else Color.argb(22,250,255,239)
            paint.style=Paint.Style.STROKE;paint.strokeWidth=dp(.8f);canvas.drawPath(path,paint);paint.style=Paint.Style.FILL
        }
        // A continuous tea branch crossing both the outside space and the card's curved edge.
        val branch=Path().apply { moveTo(w*1.06f,h*.12f);cubicTo(w*.76f,h*.31f,w*.84f,h*.45f,w*.58f,h*.66f) }
        paint.color=if(dark)c("#547664") else c("#8EA88F");paint.alpha=95
        paint.style=Paint.Style.STROKE;paint.strokeWidth=dp(1.5f);canvas.drawPath(branch,paint);paint.style=Paint.Style.FILL
        val spots=listOf(floatArrayOf(.84f,.27f,-35f),floatArrayOf(.91f,.33f,25f),floatArrayOf(.77f,.40f,-38f),floatArrayOf(.86f,.46f,32f),floatArrayOf(.68f,.54f,-38f),floatArrayOf(.74f,.59f,34f))
        spots.forEachIndexed { i,a ->
            canvas.save();canvas.translate(w*a[0],h*a[1]);canvas.rotate(a[2])
            val leaf=Path().apply{moveTo(-dp(28f),0f);cubicTo(-dp(6f),-dp(22f),dp(21f),-dp(12f),dp(34f),0f);cubicTo(dp(8f),dp(18f),-dp(12f),dp(13f),-dp(28f),0f)}
            paint.color=if(dark)c("#759785") else c("#8CA487");paint.alpha=65+i*4;canvas.drawPath(leaf,paint)
            paint.color=if(dark)c("#B3C7AB") else c("#D8E0C7");paint.alpha=75;paint.strokeWidth=dp(.6f);canvas.drawLine(-dp(22f),0f,dp(28f),0f,paint);canvas.restore()
        }
        // Low-amplitude paper grain is part of the real source, not a per-card texture.
        val random=java.util.Random(1063)
        repeat(1500){paint.color=if(dark)Color.argb(5,239,244,230) else Color.argb(7,47,76,57);canvas.drawCircle(random.nextFloat()*w,random.nextFloat()*h,dp(.35f),paint)}
        paint.alpha=255
    return b
}
