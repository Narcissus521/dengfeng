package com.campusfinance.wallet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.test.filters.SdkSuppress
import org.junit.Assert.*
import org.junit.Test

@SdkSuppress(minSdkVersion=28)
class GlassAndTutorialTest:TeaUiScenario() {
    @Test fun reopeningExpenseDuringItsReturnDoesNotWaitForAnUnnecessaryNewLayout() {
        prefs.edit().putBoolean("motion_enabled",true).commit();ready()
        navigate("记账");tapTag("leaf-EXPENSE")
        ui.onNodeWithTag("form-title").assertTextEquals("记录支出")
        ui.mainClock.autoAdvance=false
        try {
            ui.onNodeWithText("‹ 返回").performClick();ui.mainClock.advanceTimeBy(80);ui.waitForIdle()
            ui.onNodeWithTag("dock-记账").performClick();ui.mainClock.advanceTimeBy(160);ui.waitForIdle()
            ui.runOnIdle{assertEquals(CardPhase.CLOSING,ui.activity.activeCardMotion!!.phase)}
            ui.onNodeWithTag("leaf-EXPENSE").performClick();ui.mainClock.advanceTimeBy(650);ui.waitForIdle()
            ui.runOnIdle{assertEquals(CardPhase.OPEN,ui.activity.activeCardMotion!!.phase)}
            ui.onNodeWithTag("form-title").assertTextEquals("记录支出")
            ui.onNodeWithText("金额（元）").assertIsDisplayed();shot("expense-reopen")
        }finally{ui.mainClock.autoAdvance=true}
    }
    @Test fun freshInstallTutorialCanSkipAndDoesNotChooseAnAccountMode() {
        val oldMode=prefs.getString("mode",null)
        try {
            prefs.edit().remove("mode").remove(TutorialPolicy.KEY).commit();ui.activityRule.scenario.recreate()
            ui.onNodeWithTag("tutorial").assertIsDisplayed();shot("tutorial-first")
            tapTag("tutorial-skip")
            ui.onNodeWithText("创建我的账本").assertIsDisplayed()
            assertNull(prefs.getString("mode",null));assertEquals(1,prefs.getInt(TutorialPolicy.KEY,0))
            ui.activityRule.scenario.recreate();ui.waitForIdle()
            ui.onNodeWithTag("tutorial").assertDoesNotExist();ui.onNodeWithText("创建我的账本").assertIsDisplayed()
        }finally{prefs.edit().putString("mode",oldMode ?: "demo").putInt(TutorialPolicy.KEY,1).commit()}
    }
    @Test fun upgradeTutorialCompletesOnceAndCanReplayFromSettings() {
        ready();prefs.edit().remove(TutorialPolicy.KEY).commit();ui.activityRule.scenario.recreate();ui.waitForIdle()
        ui.onNodeWithTag("tutorial").assertIsDisplayed()
        repeat(4){tapTag("tutorial-next")};shot("tutorial-last");tapTag("tutorial-next")
        ui.onNodeWithTag("dock-首页").assertIsDisplayed();assertEquals("demo",prefs.getString("mode",null))
        ui.activityRule.scenario.recreate();ui.waitForIdle();ui.onNodeWithTag("tutorial").assertDoesNotExist()
        navigate("记账");tapTag("leaf-SETTINGS");tap("新手教程")
        ui.onNodeWithTag("tutorial").assertIsDisplayed();tapTag("tutorial-skip")
        ui.onNodeWithTag("form-title").assertTextEquals("设置");assertEquals(1,prefs.getInt(TutorialPolicy.KEY,0))
    }
    @Test fun nativeDragsScrubSummaryAndDockWithoutOpeningTheLeafOnPassThrough() {
        ready()
        val summary=ui.onNodeWithTag("tabs-月度-半年-年度",useUnmergedTree=true)
        summary.performTouchInput{swipe(Offset(width/6f,center.y),Offset(width*5/6f,center.y),500)};ui.waitForIdle()
        ui.onNodeWithText("年度").assertIsSelected()
        ui.onNodeWithTag("dock-drag").performTouchInput{swipe(Offset(width/10f,center.y),Offset(width*9/10f,center.y),600)};ui.waitForIdle()
        ui.onNodeWithText("消费分析").assertIsDisplayed();ui.onNodeWithTag("leaf-menu").assertDoesNotExist();shot("glass-drag-analysis")
        ui.onNodeWithTag("dock-drag").performTouchInput{swipe(Offset(width*9/10f,center.y),Offset(width/10f,center.y),600)};ui.waitForIdle()
        ui.onNodeWithText("年度").assertIsSelected()
        ui.onNodeWithTag("dock-drag").performTouchInput{swipe(Offset(width/10f,center.y),Offset(width/2f,center.y),500)};ui.waitForIdle()
        ui.onNodeWithTag("leaf-menu").assertIsDisplayed();tapTag("leaf-close")
        ui.onNodeWithText("月度").performClick();ui.onNodeWithText("月度").assertIsSelected()
    }
    @Test fun returnFinishesWithTheCardTextVisibleAndRapidNavigationRestoresTheCard() {
        prefs.edit().putString("theme_mode","LIGHT").putBoolean("motion_enabled",true).commit()
        ready();val before=ui.onNodeWithText("买前想想").captureToImage().asAndroidBitmap()
        tapTag("card-HOME_PURCHASE")
        ui.onAllNodesWithTag("dock-首页").assertCountEquals(1)
        ui.mainClock.autoAdvance=false
        try {
            ui.onNodeWithText("‹ 返回").performClick();ui.mainClock.advanceTimeBy(192);ui.waitForIdle()
            ui.runOnIdle {assertEquals(CardPhase.CLOSING,ui.activity.activeCardMotion!!.phase);assertEquals(OverviewCard.HOME_PURCHASE,ui.activity.activeCardMotion!!.origin!!.key)}
            shot("glass-return-mid")
            ui.mainClock.advanceTimeBy(208);ui.waitForIdle()
            ui.onNodeWithTag("card-editor-layer").assertDoesNotExist();ui.onNodeWithTag("card-HOME_PURCHASE").assertIsDisplayed()
            val after=ui.onNodeWithText("买前想想").captureToImage().asAndroidBitmap()
            assertTrue("Card title must already be fully drawn when the shrink finishes",before.sameAs(after))
            before.recycle();after.recycle();shot("glass-return-complete")
        }finally{ui.mainClock.autoAdvance=true}
        tapTag("card-HOME_PURCHASE");ui.onNodeWithText("‹ 返回").performClick();navigate("账本");navigate("首页")
        ui.onNodeWithTag("card-HOME_PURCHASE").assertIsDisplayed();ui.onAllNodesWithTag("dock-首页").assertCountEquals(1)
        shot("glass-home")
    }
    @Test fun settingsChildrenReturnWithoutHorizontalJumps() {
        prefs.edit().putBoolean("motion_enabled",true).commit();ready()
        navigate("记账");tapTag("leaf-SETTINGS")
        val titleLeft=ui.onNodeWithTag("form-title").fetchSemanticsNode().boundsInRoot.left
        listOf("主题、显示与动效","新手教程").forEach { child->
            tap(child);ui.mainClock.autoAdvance=false
            try {
                if(child=="新手教程")ui.onNodeWithTag("tutorial-skip").performClick() else ui.onNodeWithText("‹ 返回").performClick()
                repeat(16) {
                    ui.mainClock.advanceTimeBy(16);ui.waitForIdle()
                    val title=ui.onNodeWithTag("form-title").assertTextEquals("设置").fetchSemanticsNode()
                    assertEquals("The parent settings page must stay horizontally anchored throughout return",titleLeft,title.boundsInRoot.left,1f)
                }
                ui.onAllNodesWithTag("dock-drag").assertCountEquals(1)
            }finally{ui.mainClock.autoAdvance=true}
        }
    }
}
