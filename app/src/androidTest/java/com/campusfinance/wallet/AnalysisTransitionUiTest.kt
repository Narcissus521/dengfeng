package com.campusfinance.wallet

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.toPixelMap
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class AnalysisTransitionUiTest:TeaUiScenario() {
    @Test fun analysisEntriesKeepTheAnalysisDewAndDoNotFlashTheMainDockAtTheEnd() {
        val oldTheme=prefs.getString("theme_mode",null);val oldMotion=prefs.getBoolean("motion_enabled",true)
        try {
            listOf("LIGHT","DARK").forEach{theme->
                prefs.edit().putString("theme_mode",theme).putBoolean("motion_enabled",true).commit();ready();navigate("分析")
                listOf("calendar","budget","alerts").forEach{page->
                    ui.mainClock.autoAdvance=false
                    try {
                        ui.onNodeWithTag("analysis-entry-$page").performClick();ui.mainClock.advanceTimeByFrame();ui.waitForIdle()
                        ui.runOnIdle {
                            assertEquals(4,ui.activity.activeDockSlot)
                            val state=ui.activity.activeCardMotion!!
                            assertTrue("First visible frame must begin at the source card, not full size",state.fraction<.1f)
                        }
                        ui.mainClock.advanceTimeBy(430);ui.waitForIdle()
                        val before=ui.onRoot().captureToImage().toPixelMap()
                        ui.mainClock.advanceTimeBy(120);ui.waitForIdle()
                        val after=ui.onRoot().captureToImage().toPixelMap()
                        val x=(before.width*.96f).toInt();val y=(before.height*.92f).toInt()
                        val a=before[x,y];val b=after[x,y]
                        assertTrue("The last frame must not remove a visible main-page Dock underneath",maxOf(abs(a.red-b.red),abs(a.green-b.green),abs(a.blue-b.blue))<.07f)
                        ui.runOnIdle{assertEquals(4,ui.activity.activeDockSlot);assertEquals(CardPhase.OPEN,ui.activity.activeCardMotion!!.phase)}
                        ui.onNodeWithText("‹ 返回").performClick();ui.mainClock.advanceTimeBy(450);ui.waitForIdle()
                        ui.onNodeWithTag("dock-分析").assertIsSelected()
                    } finally {ui.mainClock.autoAdvance=true}
                }
            }
            prefs.edit().putBoolean("motion_enabled",false).commit();ready();navigate("分析");tapTag("analysis-entry-calendar")
            ui.runOnIdle{assertEquals(4,ui.activity.activeDockSlot)};back();ui.onNodeWithTag("dock-分析").assertIsSelected()
        } finally {ui.mainClock.autoAdvance=true;prefs.edit().putString("theme_mode",oldTheme).putBoolean("motion_enabled",oldMotion).commit()}
    }
}
