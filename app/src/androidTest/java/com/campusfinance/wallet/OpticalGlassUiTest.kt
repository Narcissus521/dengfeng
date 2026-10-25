package com.campusfinance.wallet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class OpticalGlassUiTest:TeaUiScenario() {
    @Test fun bodySwipeScrubsTheExistingLensAndCancelledSwipeKeepsThePageAndScene() {
        prefs.edit().putString("theme_mode","LIGHT").putBoolean("motion_enabled",true).commit();ready()
        ui.waitUntil(10000){ui.activity.activeOpticalScene?.image!=null}
        ui.waitForIdle()
        val original=ui.onRoot().captureToImage().asAndroidBitmap()
        val originalDock=ui.onNodeWithTag("dock-drag").captureToImage().asAndroidBitmap()
        ui.mainClock.autoAdvance=false
        try {
            val viewport=ui.onNodeWithTag("main-page-viewport")
            viewport.performTouchInput{down(Offset(width*.88f,25f));moveTo(Offset(width*.23f,25f),180)}
            ui.mainClock.advanceTimeBy(96);ui.waitForIdle()
            ui.runOnIdle{assertTrue(ui.activity.activePageMotion!!.syncDock);assertEquals(.65f,ui.activity.activePageMotion!!.position,.04f)}
            val held=ui.onNodeWithTag("dock-drag").captureToImage().asAndroidBitmap()
            assertFalse("A body swipe must move the existing Dock lens",originalDock.sameAs(held));originalDock.recycle();held.recycle()
            viewport.performTouchInput{up()};ui.mainClock.advanceTimeBy(1000);ui.waitForIdle()
            ui.onNodeWithTag("dock-账本").assertIsSelected()
            viewport.performTouchInput{down(Offset(width*.2f,25f));moveTo(Offset(width*.89f,25f),180);up()}
            ui.mainClock.advanceTimeBy(1000);ui.waitForIdle();ui.onNodeWithTag("dock-首页").assertIsSelected()
            viewport.performTouchInput{down(Offset(width*.9f,25f));moveTo(Offset(width*.2f,25f),180);cancel()}
            ui.mainClock.advanceTimeBy(1000);ui.waitForIdle();ui.onNodeWithTag("dock-首页").assertIsSelected()
        } finally {ui.mainClock.autoAdvance=true}
        ui.runOnIdle{assertEquals("Cancelled swipe must settle back to the committed page",0f,ui.activity.activePageMotion!!.position,.001f)}
        val after=ui.onRoot().captureToImage().asAndroidBitmap()
        listOf(800,1100,1400).forEach{y->assertEquals("Outside scenery must remain stationary",original.getPixel(4,y),after.getPixel(4,y))}
        original.recycle();after.recycle();shot("optical-body-swipe")
    }

    @Test fun themeReturnAndIdleStopDoNotReadDetachedCoordinatesOrKeepDrawing() {
        listOf("LIGHT","DARK").forEach{theme->
            prefs.edit().putString("theme_mode",theme).putBoolean("motion_enabled",true).commit();ready()
            ui.waitUntil(10000){ui.activity.activeOpticalScene?.image!=null}
            navigate("记账");tapTag("leaf-SETTINGS");tap("主题、显示与动效");back();navigate("分析");navigate("首页")
            ui.mainClock.autoAdvance=false
            try {
                ui.mainClock.advanceTimeBy(2400);ui.waitForIdle()
                val frame=ui.activity.activeOpticalScene!!.frame
                ui.mainClock.advanceTimeBy(400);ui.waitForIdle()
                assertEquals("Static glass must stop requesting sample redraws",frame,ui.activity.activeOpticalScene!!.frame)
            }finally{ui.mainClock.autoAdvance=true}
            shot("optical-$theme")
        }
    }

    @Test fun compatibilityRendererPreservesFullOriginWhenClippedAndWhenCoordinatesDetach() {
        val scene=OpticalScene().apply{image=makeOpticalTeaScene(240,320,1f,false)}
        val anchor=GlassAnchor().apply{origin=Offset(30f,60f)}
        val full=Bitmap.createBitmap(140,180,Bitmap.Config.ARGB_8888)
        val clipped=Bitmap.createBitmap(140,180,Bitmap.Config.ARGB_8888)
        fun render(bitmap:Bitmap,clip:Boolean){val canvas=Canvas(bitmap);if(clip)canvas.clipRect(0,45,140,180)
            OpticalGlassPainter(forceCompatibility=true).draw(canvas,scene,anchor,RectF(0f,0f,140f,180f),25f,1f,false,Color.WHITE)
        }
        render(full,false);render(clipped,true)
        assertEquals(Color.TRANSPARENT,full.getPixel(0,0))
        assertEquals(Color.TRANSPARENT,clipped.getPixel(70,30))
        for(y in 55 until 165 step 13)for(x in 20 until 120 step 17)assertEquals(full.getPixel(x,y),clipped.getPixel(x,y))
        anchor.update(scene);assertEquals(Offset(30f,60f),anchor.origin)
        full.recycle();clipped.recycle()
    }
}
