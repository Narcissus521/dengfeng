package com.campusfinance.wallet

import android.graphics.Color
import androidx.compose.ui.test.*
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Actual navigation geometry and rendered system-bar pixels, in the isolated demo ledger. */
class NavigationContinuityTest:TeaUiScenario() {
    @Test fun rootDestinationMovesContinuouslyWithItsTitleFromTheFirstFrames() {
        prefs.edit().putString("theme_mode","LIGHT").putBoolean("motion_enabled",true).commit();ready()
        navigate("记账");ui.mainClock.autoAdvance=false
        try {
            ui.onNodeWithTag("leaf-EXPENSE").performClick()
            ui.mainClock.advanceTimeBy(96);ui.waitForIdle()
            val first=ui.onAllNodesWithTag("form-title").onLast().fetchSemanticsNode().boundsInRoot.left
            shot("navigation-expense-early")
            ui.mainClock.advanceTimeBy(96);ui.waitForIdle()
            val second=ui.onAllNodesWithTag("form-title").onLast().fetchSemanticsNode().boundsInRoot.left
            shot("navigation-expense-middle")
            assertTrue("Incoming title must travel with the page, rather than appear late under a growing mask: $first -> $second",first>second+8f)
            ui.mainClock.advanceTimeBy(700);ui.waitForIdle()
            ui.onNodeWithText("金额（元）").assertIsDisplayed()
        } finally {ui.mainClock.autoAdvance=true}
        shot("navigation-expense-complete")
    }

    @Test fun statusBarHasOnlyItsSafeInsetAndLightIconsInBothThemes() {
        for(mode in listOf("LIGHT","DARK")) {
            prefs.edit().putString("theme_mode",mode).commit();ready()
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();android.os.SystemClock.sleep(250)
            val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            try {
                var brightness=0.0;var count=0
                // Above all app content, away from clock, camera cutout and battery glyphs.
                for(x in bitmap.width/4 until bitmap.width/3 step 3)for(y in 2..8 step 2) {
                    val pixel=bitmap.getPixel(x,y);brightness+=(Color.red(pixel)+Color.green(pixel)+Color.blue(pixel))/765.0;count++
                }
                assertTrue("Status-bar safe inset must retain its graphite background, mode=$mode",brightness/count<.4)
                ui.onNodeWithTag("brand-0").assertDoesNotExist()
                val inset=androidx.core.view.ViewCompat.getRootWindowInsets(ui.activity.window.decorView)!!.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars()).top
                val viewport=ui.onNodeWithTag("main-page-viewport").fetchSemanticsNode().boundsInRoot
                assertEquals("No extra app-name row above the page",inset.toFloat(),viewport.top,2f)
                ui.runOnIdle{assertFalse(WindowInsetsControllerCompat(ui.activity.window,ui.activity.window.decorView).isAppearanceLightStatusBars)}
            } finally {bitmap.recycle()}
        }
    }

    @Test fun returnAndReopenContinueFromTheVisiblePositionAndReducedMotionSettlesImmediately() {
        prefs.edit().putString("theme_mode","LIGHT").putBoolean("motion_enabled",true).commit();ready()
        navigate("记账");tapTag("leaf-EXPENSE");ui.mainClock.autoAdvance=false
        try {
            ui.onNodeWithText("‹ 返回").performClick();ui.mainClock.advanceTimeBy(80);ui.waitForIdle()
            val before=ui.activity.activeCardMotion!!.fraction
            assertTrue(before>0f&&before<1f)
            ui.onNodeWithTag("dock-记账").performClick();ui.mainClock.advanceTimeBy(32);ui.waitForIdle()
            val visible=ui.activity.activeCardMotion!!.fraction
            ui.onNodeWithTag("leaf-EXPENSE").performClick();ui.mainClock.advanceTimeBy(32);ui.waitForIdle()
            val reopening=ui.activity.activeCardMotion!!.fraction
            assertTrue("Reopening must turn from the current position instead of restarting offscreen: $visible -> $reopening",reopening>=visible-.04f)
            ui.mainClock.advanceTimeBy(700);ui.waitForIdle()
            ui.onNodeWithTag("form-title").assertTextEquals("记录支出")
        } finally {ui.mainClock.autoAdvance=true}
        back();prefs.edit().putBoolean("motion_enabled",false).commit();ready()
        navigate("记账");tapTag("leaf-EXPENSE")
        ui.runOnIdle{assertEquals(CardPhase.OPEN,ui.activity.activeCardMotion!!.phase)}
        back();ui.onNodeWithTag("dock-首页").assertExists()
    }
}
