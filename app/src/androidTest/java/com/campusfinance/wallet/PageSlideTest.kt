package com.campusfinance.wallet

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.test.filters.SdkSuppress
import java.io.File
import org.junit.Assert.*
import org.junit.Test

@SdkSuppress(minSdkVersion=28)
class PageSlideTest:TeaUiScenario() {
    private fun frame(name:String) {
        val bitmap=ui.onNodeWithTag("main-page-viewport").captureToImage().asAndroidBitmap()
        val file=File(context.getExternalFilesDir(null),"screenshots/0.8.6-$name.png");file.parentFile?.mkdirs()
        file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    @Test fun brandAndBodySlideTogetherWhileTheDockStaysFixedInBothDirections() {
        prefs.edit().putString("theme_mode","LIGHT").putBoolean("motion_enabled",true).commit();ready()
        val dockBounds=ui.onNodeWithTag("dock-drag").fetchSemanticsNode().boundsInRoot
        val viewport=ui.onNodeWithTag("main-page-viewport").fetchSemanticsNode().boundsInRoot
        ui.mainClock.autoAdvance=false
        try {
            ui.onNodeWithTag("dock-账本").performClick();ui.mainClock.advanceTimeBy(160);ui.waitForIdle()
            val brand=ui.onNodeWithTag("brand-1").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val card=ui.onNodeWithTag("card-LEDGER_ASSETS").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("The brand must travel with the entering page",brand.left>viewport.left+10f)
            assertEquals(dockBounds,ui.onNodeWithTag("dock-drag").fetchSemanticsNode().boundsInRoot)
            frame("slide-forward-mid")
            ui.mainClock.advanceTimeBy(64);ui.waitForIdle()
            val nextBrand=ui.onNodeWithTag("brand-1").fetchSemanticsNode().boundsInRoot
            val nextCard=ui.onNodeWithTag("card-LEDGER_ASSETS").fetchSemanticsNode().boundsInRoot
            assertTrue(nextBrand.left<brand.left)
            assertEquals("Header and body must move by the same distance",brand.left-nextBrand.left,card.left-nextCard.left,1f)
            ui.mainClock.advanceTimeBy(500);ui.waitForIdle()
            assertEquals(viewport.left,ui.onNodeWithTag("brand-1").fetchSemanticsNode().boundsInRoot.left,1f)
            ui.onAllNodesWithText("等风").assertCountEquals(1)
            ui.onNodeWithTag("dock-首页").performClick();ui.mainClock.advanceTimeBy(160);ui.waitForIdle()
            val reverse=ui.onNodeWithTag("brand-0").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("Returning pages must enter from the opposite side",reverse.right<viewport.right-10f)
            assertEquals(dockBounds,ui.onNodeWithTag("dock-drag").fetchSemanticsNode().boundsInRoot)
            frame("slide-back-mid");ui.mainClock.advanceTimeBy(500);ui.waitForIdle()
            ui.onNodeWithTag("dock-首页").assertIsSelected();ui.onAllNodesWithText("等风").assertCountEquals(1)
        }finally{ui.mainClock.autoAdvance=true}
        shot("slide-home-complete")
    }
    @Test fun rapidReversalsKeepSelectionsAndFinishWithOnePageAndOneDock() {
        prefs.edit().putBoolean("motion_enabled",true).commit();ready()
        ui.onNodeWithText("年度").performClick();navigate("账本");ui.onNodeWithText("各账户").performClick()
        navigate("分析");ui.onNodeWithText("6 个月").performClick();ui.waitForIdle()
        ui.mainClock.autoAdvance=false
        try {
            for(label in listOf("首页","规划","账本","首页","分析")) {
                ui.onNodeWithTag("dock-$label").performClick();ui.mainClock.advanceTimeBy(64);ui.waitForIdle()
            }
            ui.mainClock.advanceTimeBy(700);ui.waitForIdle()
            ui.onNodeWithTag("dock-分析").assertIsSelected();ui.onNodeWithText("6 个月").assertIsSelected()
            ui.onAllNodesWithTag("dock-drag").assertCountEquals(1);ui.onAllNodesWithText("等风").assertCountEquals(1)
            frame("slide-analysis-complete")
        }finally{ui.mainClock.autoAdvance=true}
        navigate("账本");ui.onNodeWithText("各账户").assertIsSelected()
        navigate("首页");ui.onNodeWithText("年度").assertIsSelected()
    }
    @Test fun motionOffPlacesTheWholeTargetPageWithoutSliding() {
        prefs.edit().putBoolean("motion_enabled",false).commit();ready()
        val left=ui.onNodeWithTag("brand-0").fetchSemanticsNode().boundsInRoot.left
        ui.mainClock.autoAdvance=false
        try {
            ui.onNodeWithTag("dock-规划").performClick();ui.mainClock.advanceTimeBy(64);ui.waitForIdle()
            assertEquals(left,ui.onNodeWithTag("brand-2").fetchSemanticsNode().boundsInRoot.left,1f)
            ui.onNodeWithText("资金规划").assertIsDisplayed();ui.onAllNodesWithText("等风").assertCountEquals(1)
        }finally{ui.mainClock.autoAdvance=true;prefs.edit().putBoolean("motion_enabled",true).commit()}
    }
}
