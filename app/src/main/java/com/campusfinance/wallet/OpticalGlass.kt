package com.campusfinance.wallet

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

internal val LocalOpticalScene=staticCompositionLocalOf<OpticalScene?>{null}

internal class OpticalScene {
    var image by mutableStateOf<Bitmap?>(null)
    var coordinates:LayoutCoordinates?=null
    var dark:Boolean?=null
    var frame by mutableIntStateOf(0)
    var revision by mutableIntStateOf(0)
    fun pulse(){revision++}
}

/** Last valid full origin and basis survive detach; clipped bounds are never a sampling origin. */
internal class GlassAnchor {
    var coordinates:LayoutCoordinates?=null
    var origin=Offset.Zero
    var axisX=Offset(1f,0f)
    var axisY=Offset(0f,1f)
    fun update(scene:OpticalScene) {
        val node=coordinates;val root=scene.coordinates
        if(node?.isAttached!=true||root?.isAttached!=true)return
        val zero=node.localToScreen(Offset.Zero)
        val base=root.localToScreen(Offset.Zero)
        val x=node.localToScreen(Offset(1f,0f))-zero
        val y=node.localToScreen(Offset(0f,1f))-zero
        val next=zero-base
        if(listOf(next.x,next.y,x.x,x.y,y.x,y.y).all{it.isFinite()}){origin=next;axisX=x;axisY=y}
    }
}

@Composable internal fun OpticalBackdrop(content:@Composable ()->Unit) {
    val dark=LocalWalletColors.current.dark
    val density=LocalDensity.current.density
    val base=Page
    val scene=remember{OpticalScene()}
    var width by remember{mutableIntStateOf(0)}
    var height by remember{mutableIntStateOf(0)}
    LaunchedEffect(width,height,dark,density){
        if(width>0&&height>0){
            if(scene.dark!=dark){scene.image=null;scene.dark=dark}
            scene.image=withContext(Dispatchers.Default){makeOpticalTeaScene(width,height,density,dark)}
        }
    }
    LaunchedEffect(scene){snapshotFlow{scene.revision}.collectLatest { revision ->
        if(revision>0){var start=0L;var elapsed=0L
            do{withFrameNanos{now->if(start==0L)start=now;elapsed=now-start;scene.frame++}}while(elapsed<650_000_000L)
        }
    }}
    CompositionLocalProvider(LocalOpticalScene provides scene) {
        Box(Modifier.fillMaxSize().onSizeChanged {
            // Keep a keyboard resize from rescaling the scenery underneath the focused field.
            if(width!=it.width){width=it.width;height=it.height}else height=maxOf(height,it.height)
        }.onPlaced{scene.coordinates=it}.pointerInput(scene){
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial);scene.pulse()
                do{val event=awaitPointerEvent(PointerEventPass.Initial);scene.pulse()}while(event.changes.any{it.pressed})
            }
        }) {
            Canvas(Modifier.fillMaxSize()){val image=scene.image;if(image==null)drawRect(base) else drawIntoCanvas{it.nativeCanvas.drawBitmap(image,0f,0f,null)}}
            content()
        }
    }
}

/** Per-surface shader/paint cache; only uniforms change while the surface moves. */
internal class OpticalGlassPainter(forceCompatibility:Boolean=false) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var source:Bitmap?=null
    private var bitmapShader:BitmapShader?=null
    private val fallbackMatrix=Matrix()
    private val inverse=Matrix()
    private val backend=if(Build.VERSION.SDK_INT>=33&&!forceCompatibility)AgslOptics() else null
    fun draw(canvas:AndroidCanvas,scene:OpticalScene?,anchor:GlassAnchor,rect:RectF,radius:Float,density:Float,dark:Boolean,tint:Int,strength:Float=.56f,bend:Float=1f,lift:Float=0f) {
        if(rect.width()<=0||rect.height()<=0)return
        scene?.frame // Invalidate cached child layers during ancestor translation and resizing.
        val image=scene?.image
        if(scene!=null)anchor.update(scene)
        val r=minOf(radius,rect.width()/2,rect.height()/2).coerceAtLeast(0f)
        if(image!=null&&image!=source) {
            source=image;bitmapShader=BitmapShader(image,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP)
            if(Build.VERSION.SDK_INT>=33&&backend!=null){bitmapShader!!.setFilterMode(BitmapShader.FILTER_MODE_LINEAR);backend.setSource(bitmapShader!!)}
        }
        if(image!=null&&Build.VERSION.SDK_INT>=33&&backend!=null) {
            backend!!.draw(canvas,paint,anchor,rect,r,density,dark,tint,strength,bend,lift)
        } else {
            // Older supported Android versions keep the same coordinate-aligned scene and shape.
            val a=anchor
            fallbackMatrix.setValues(floatArrayOf(a.axisX.x,a.axisY.x,a.origin.x,a.axisX.y,a.axisY.y,a.origin.y,0f,0f,1f))
            if(fallbackMatrix.invert(inverse))bitmapShader?.setLocalMatrix(inverse)
            paint.shader=bitmapShader;paint.alpha=255
            if(image!=null)canvas.drawRoundRect(rect,r,r,paint)
            paint.shader=null;paint.color=tint;paint.alpha=if(dark)214 else 205;canvas.drawRoundRect(rect,r,r,paint)
            paint.color=if(dark)0x22FFFFFF else 0x45FFFFFF;paint.alpha=if(dark)34 else 69;paint.style=Paint.Style.STROKE;paint.strokeWidth=density*.6f
            canvas.drawRoundRect(RectF(rect).apply{inset(density*.4f,density*.4f)},r,r,paint);paint.style=Paint.Style.FILL;paint.alpha=255
        }
    }
}

@RequiresApi(33)
private class AgslOptics {
    private val shader=RuntimeShader(OPTICAL_GLASS_SHADER)
    fun setSource(source:Shader){shader.setInputShader("scene",source)}
    fun draw(canvas:AndroidCanvas,paint:Paint,a:GlassAnchor,rect:RectF,radius:Float,density:Float,dark:Boolean,tint:Int,strength:Float,bend:Float,lift:Float) {
        shader.setFloatUniform("rect",rect.left,rect.top,rect.right,rect.bottom)
        shader.setFloatUniform("origin",a.origin.x,a.origin.y)
        shader.setFloatUniform("axisX",a.axisX.x,a.axisX.y);shader.setFloatUniform("axisY",a.axisY.x,a.axisY.y)
        shader.setFloatUniform("radius",radius);shader.setFloatUniform("scale",density)
        shader.setFloatUniform("tint",strength);shader.setFloatUniform("bend",bend)
        shader.setFloatUniform("night",if(dark)1f else 0f);shader.setFloatUniform("lift",lift)
        // Theme and explicit card tint define contrast. Local sampled brightness never changes it.
        shader.setColorUniform("cardTint",tint)
        paint.shader=shader;paint.alpha=255;canvas.drawRoundRect(rect,radius,radius,paint);paint.shader=null
    }
}

private const val OPTICAL_GLASS_SHADER="""
uniform shader scene;
uniform float4 rect;
uniform float2 origin;
uniform float2 axisX;
uniform float2 axisY;
uniform float radius;
uniform float scale;
uniform float tint;
uniform float bend;
uniform float night;
uniform float lift;
layout(color) uniform half4 cardTint;
float roundedDistance(float2 p,float2 halfSize,float r){float2 q=abs(p)-halfSize+r;return length(max(q,0.0))+min(max(q.x,q.y),0.0)-r;}
half4 main(float2 xy){
    float2 center=(rect.xy+rect.zw)*0.5;
    float2 halfSize=(rect.zw-rect.xy)*0.5;
    float2 p=xy-center;
    float d=roundedDistance(p,halfSize,radius);
    float2 q=abs(p)-halfSize+radius;
    float2 n=normalize(max(q,0.0)+float2(0.0001));
    if(q.x<0.0)n=float2(0.0,1.0);
    if(q.y<0.0)n=float2(1.0,0.0);
    n*=sign(p);
    float edge=1.0-smoothstep(0.0,11.0*scale,-d);
    float2 world=origin+xy.x*axisX+xy.y*axisY;
    float2 normal=n.x*axisX+n.y*axisY;
    float2 sampleAt=world-normal*(5.4*scale*bend*edge*edge);
    float blur=1.35*scale;
    half4 src=scene.eval(sampleAt)*0.36;
    src+=(scene.eval(sampleAt+float2(blur,0))+scene.eval(sampleAt-float2(blur,0))+scene.eval(sampleAt+float2(0,blur))+scene.eval(sampleAt-float2(0,blur)))*0.12;
    src+=(scene.eval(sampleAt+float2(blur,blur))+scene.eval(sampleAt-float2(blur,blur))+scene.eval(sampleAt+float2(-blur,blur))+scene.eval(sampleAt+float2(blur,-blur)))*0.04;
    half3 veil=mix(half3(0.963,0.977,0.957),half3(0.059,0.099,0.096),night);
    veil=mix(veil,cardTint.rgb,0.08);
    half3 color=mix(src.rgb,veil,tint*(1.0-edge*0.20));
    float rim=1.0-smoothstep(0.0,1.0*scale,abs(d+0.7*scale));
    float topLight=pow(max(0.0,dot(n,normalize(float2(-0.55,-0.84)))),5.0);
    float lowLight=pow(max(0.0,dot(n,normalize(float2(0.5,0.86)))),7.0);
    color+=half3(1.0)*(rim*(0.045+0.13*topLight+0.025*lowLight+lift*0.035));
    color-=half3(exp(-abs(d+2.4*scale)/(1.5*scale))*0.025);
    return half4(clamp(color,0.0,1.0),1.0);
}
"""
