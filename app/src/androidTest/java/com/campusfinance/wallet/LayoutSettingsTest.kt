package com.campusfinance.wallet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.filters.SdkSuppress
import org.junit.Assert.*
import org.junit.Test

@SdkSuppress(minSdkVersion=28)
class LayoutSettingsTest:TeaUiScenario() {
    private fun defaults() {
        prefs.edit().apply {
            putString("layout_size","STANDARD");putInt("layout_percent",100);putString("theme_mode","LIGHT");putBoolean("motion_enabled",false)
            OverviewCard.entries.forEach{card->remove("card_size_${card.name}");listOf("w","h","x","y","p").forEach{remove("card_grid_${card.name}_$it")}}
        }.commit();ready()
    }
    private fun card(c:OverviewCard)=ui.onNodeWithTag("card-${c.name}")
    private fun reveal(c:OverviewCard) {
        if(runCatching{card(c).assertIsDisplayed()}.isSuccess)return
        repeat(15){val previous=ui.onAllNodesWithTag("grid-previous").onLast();if(runCatching{previous.assertIsEnabled()}.isSuccess){previous.performClick();ui.waitForIdle()}}
        repeat(15){if(runCatching{card(c).assertIsDisplayed()}.isSuccess)return;val next=ui.onAllNodesWithTag("grid-next").onLast();if(runCatching{next.assertIsEnabled()}.isSuccess){next.performClick();ui.waitForIdle()}}
        card(c).assertIsDisplayed()
    }
    private fun edit(c:OverviewCard){reveal(c);card(c).performTouchInput{longClick(Offset(center.x,18f))};ui.waitForIdle();ui.onNodeWithTag("card-resize-panel").assertIsDisplayed();ui.onNodeWithTag("form-title").assertDoesNotExist()}
    private fun stored(c:OverviewCard)=ui.activity.displayController.state.value.cardGrids[c]
    private fun cleanup(){ui.runOnIdle{ui.activity.displayController.resetCardSizes();ui.activity.displayController.setAnimations(true)}}
    @Test fun nativeEdgeDragsChangeWidthAndHeightIndependentlyAndPersist() {
        try {
            defaults();val c=OverviewCard.HOME_BUDGET;val before=card(c).fetchSemanticsNode().boundsInRoot
            val dock=ui.onNodeWithTag("dock-drag").fetchSemanticsNode().boundsInRoot
            edit(c);val gap=10f*context.resources.displayMetrics.density;val step=(before.width+gap)/4f
            ui.onNodeWithTag("resize-${c.name}-right").performTouchInput{swipe(center,center-Offset(step*2,0f),500)};ui.waitForIdle()
            assertEquals(2,stored(c)!!.columns);assertEquals(2,stored(c)!!.rows)
            ui.onNodeWithTag("card-resize-dimensions").assertTextEquals("2×2");shot("grid-budget-two-by-two")
            val height=card(c).fetchSemanticsNode().boundsInRoot.height;val stepY=(height+gap)/2f
            ui.onNodeWithTag("resize-${c.name}-bottom").performTouchInput{swipe(center,center+Offset(0f,stepY*2),500)};ui.waitForIdle()
            assertEquals(2,stored(c)!!.columns);assertEquals(4,stored(c)!!.rows)
            assertEquals(dock,ui.onNodeWithTag("dock-drag").fetchSemanticsNode().boundsInRoot)
            ui.onNodeWithText("必要剩余").assertIsDisplayed();ui.onNodeWithText("自由剩余").assertIsDisplayed();shot("grid-budget-two-by-four")
            tapTag("card-resize-done");ready();assertEquals(2,stored(c)!!.columns);assertEquals(4,stored(c)!!.rows)
        }finally{cleanup()}
    }
    @Test fun pullingTheLeftEdgeInwardKeepsTheRightEdgeAtItsOriginalPosition() {
        try {
            defaults();val c=OverviewCard.HOME_PURCHASE;edit(c)
            val before=card(c).fetchSemanticsNode().boundsInRoot;val step=(before.width+10f*context.resources.displayMetrics.density)/4f
            ui.onNodeWithTag("resize-${c.name}-left").performTouchInput{swipe(center,center+Offset(step*2,0f),500)};ui.waitForIdle()
            assertEquals(2,stored(c)!!.columns);assertEquals(2,stored(c)!!.column)
            assertEquals(before.right,card(c).fetchSemanticsNode().boundsInRoot.right,2f)
            tapTag("card-resize-done");reveal(c);card(c).performClick();ui.onNodeWithTag("form-title").assertTextEquals("买前想想")
        }finally{cleanup()}
    }
    @Test fun cancelledResizeDoesNotSaveAndResetRestoresOnlyTheSelectedCard() {
        try {
            defaults();val c=OverviewCard.HOME_BUDGET;edit(c)
            ui.onNodeWithTag("resize-${c.name}-right").performTouchInput{down(center);moveBy(Offset(-180f,0f));cancel()};ui.waitForIdle()
            assertNull(stored(c));ui.onNodeWithTag("card-resize-dimensions").assertTextEquals("4×2")
            ui.runOnIdle{ui.activity.displayController.setCardGrids(mapOf(OverviewCard.HOME_DEBT to CardGridLayout(2,4)))}
            tapTag("card-resize-reset");assertEquals(CardGridPolicy.default(c).columns,stored(c)!!.columns)
            assertEquals(4,stored(OverviewCard.HOME_DEBT)!!.rows)
        }finally{cleanup()}
    }
    @Test fun allPagesUseEdgeHandlesAndSettingsCanResetAllGridPreferences() {
        try {
            defaults()
            listOf("首页" to OverviewCard.HOME_DEBT,"账本" to OverviewCard.LEDGER_FIXED_EXPENSE,"规划" to OverviewCard.PLAN_DEBT,"分析" to OverviewCard.ANALYSIS_CHART).forEach{(page,c)->
                navigate(page);edit(c)
                val action=ui.onNodeWithTag("resize-${c.name}-bottom").fetchSemanticsNode().config[SemanticsActions.CustomActions].first{it.label=="加高一格"}
                ui.runOnIdle{assertTrue(action.action())}
                ui.waitForIdle();assertNotNull(stored(c));tapTag("card-resize-done")
            }
            navigate("记账");tapTag("leaf-SETTINGS");tap("主题、显示与动效");tap("卡片大小");tapTag("layout-reset-all")
            assertTrue(ui.activity.displayController.state.value.cardGrids.isEmpty())
        }finally{cleanup()}
    }
    @Test fun debtTeaStaysInTheBottomRightAndEditedCardStillOpensItsDetails() {
        try {
            defaults();val c=OverviewCard.HOME_DEBT
            ui.runOnIdle{ui.activity.displayController.setCardGrids(mapOf(c to CardGridLayout(2,4,2,3,0)))};ui.waitForIdle();reveal(c)
            val bounds=card(c).fetchSemanticsNode().boundsInRoot
            val art=ui.onNodeWithTag("debt-corner-tea",useUnmergedTree=true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue(art.right<=bounds.right);assertTrue(art.bottom<=bounds.bottom)
            assertTrue(art.left>bounds.center.x-30f);assertTrue(art.top>bounds.center.y)
            shot("grid-debt-tea-corner");card(c).performClick();ui.onNodeWithTag("form-title").assertTextEquals("负债")
        }finally{cleanup()}
    }
    @Test fun malformedGridPreferencesFallBackSafelyAndMainAmountsFitAllShapes() {
        try {
            defaults();prefs.edit().putInt("card_grid_HOME_DEBT_w",Int.MAX_VALUE).putInt("card_grid_HOME_DEBT_h",Int.MIN_VALUE)
                .putString("card_grid_HOME_PURCHASE_w","invalid").putInt("card_grid_HOME_PURCHASE_h",2).commit();ready()
            assertEquals(4,stored(OverviewCard.HOME_DEBT)!!.columns);assertEquals(1,stored(OverviewCard.HOME_DEBT)!!.rows)
            assertNull(stored(OverviewCard.HOME_PURCHASE))
            for((w,h) in listOf(2 to 1,2 to 2,4 to 2,2 to 4)) {
                ui.runOnIdle{ui.activity.displayController.setCardGrids(OverviewCard.entries.associateWith{CardGridLayout(w,h)})};ui.waitForIdle()
                for(page in listOf("首页","账本","规划","分析")) {
                    navigate(page)
                    repeat(14){val previous=ui.onAllNodesWithTag("grid-previous").onLast();if(runCatching{previous.assertIsEnabled()}.isSuccess)previous.performClick()}
                    for(index in 0..13) {
                        ui.waitForIdle()
                        ui.onAllNodesWithTag("grid-money",useUnmergedTree=true).fetchSemanticsNodes().indices.forEach{n->
                            val node=ui.onAllNodesWithTag("grid-money",useUnmergedTree=true)[n]
                            val results=mutableListOf<TextLayoutResult>()
                            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(results)}
                            val shown=node.fetchSemanticsNode().boundsInRoot
                            results.forEach{result->assertFalse("Amount overflows: $page ${w}x$h ${result.layoutInput.text}",result.hasVisualOverflow);assertTrue("Amount was clipped: $page ${w}x$h ${result.layoutInput.text}",result.size.height<=shown.height+2f)}
                        }
                        val next=ui.onAllNodesWithTag("grid-next").onLast()
                        if(runCatching{next.assertIsEnabled()}.isFailure)break
                        next.performClick()
                    }
                }
            }
        }finally{cleanup()}
    }
}
