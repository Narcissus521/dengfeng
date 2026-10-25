package com.campusfinance.wallet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.test.filters.SdkSuppress
import org.junit.Assert.*
import org.junit.Test

@SdkSuppress(minSdkVersion=28)
class CardMoveTest:TeaUiScenario() {
    private val debt=OverviewCard.HOME_DEBT
    private val savings=OverviewCard.HOME_SAVINGS
    private fun card(c:OverviewCard)=ui.onNodeWithTag("card-${c.name}")
    private fun defaults(motion:Boolean=false) {
        prefs.edit().putString("theme_mode","LIGHT").putBoolean("motion_enabled",motion).commit()
        ui.runOnIdle{ui.activity.displayController.resetCardSizes()};ready()
    }
    private fun cleanup(){ui.mainClock.autoAdvance=true;ui.runOnIdle{ui.activity.displayController.resetCardSizes();ui.activity.displayController.setAnimations(true)}}
    @Test fun firstLongPressFollowsTheFingerThenSnapsAndPersistsWithoutOpeningDetails() {
        try {
            defaults();val origin=card(debt).fetchSemanticsNode().boundsInRoot
            val gap=10f*context.resources.displayMetrics.density;val step=(origin.width+gap)/2
            val dock=ui.onNodeWithTag("dock-drag").fetchSemanticsNode().boundsInRoot
            card(debt).performTouchInput{down(Offset(center.x,18f));advanceEventTime(700);moveBy(Offset(step*1.6f,-step*.3f))}
            ui.waitForIdle()
            val follow=card(debt).fetchSemanticsNode().boundsInRoot
            assertEquals(origin.left+step*1.6f,follow.left,3f)
            assertTrue(follow.top<origin.top);assertTrue(ui.activity.displayController.state.value.cardGrids.isEmpty())
            ui.onNodeWithTag("form-title").assertDoesNotExist();shot("move-finger-follow")
            card(debt).performTouchInput{up()};ui.waitForIdle()
            val stored=ui.activity.displayController.state.value.cardGrids.getValue(debt)
            assertEquals(2,stored.column);assertEquals(6,stored.row)
            val end=card(debt).fetchSemanticsNode().boundsInRoot
            assertEquals(origin.left+step*2,end.left,3f);assertEquals(origin.top,end.top,3f)
            assertEquals(dock,ui.onNodeWithTag("dock-drag").fetchSemanticsNode().boundsInRoot)
            assertEquals(origin.left,card(savings).fetchSemanticsNode().boundsInRoot.left,3f)
            tapTag("card-resize-done");ready()
            assertEquals(stored,ui.activity.displayController.state.value.cardGrids.getValue(debt))
            assertEquals(end.left,card(debt).fetchSemanticsNode().boundsInRoot.left,3f)
            card(debt).performClick();ui.onNodeWithTag("form-title").assertTextEquals("负债")
        }finally{cleanup()}
    }
    @Test fun cancellingAContinuousMoveRestoresAllPositionsWithoutSaving() {
        try {
            defaults();val before=OverviewCard.entries.take(5).associateWith{card(it).fetchSemanticsNode().boundsInRoot}
            card(debt).performTouchInput{down(Offset(center.x,18f));advanceEventTime(700);moveBy(Offset(300f,-200f))};ui.waitForIdle()
            card(debt).performTouchInput{cancel()};ui.waitForIdle()
            assertTrue(ui.activity.displayController.state.value.cardGrids.isEmpty())
            before.forEach{(c,rect)->assertEquals(rect,card(c).fetchSemanticsNode().boundsInRoot)}
            tapTag("card-resize-done");ui.onNodeWithTag("form-title").assertDoesNotExist()
        }finally{cleanup()}
    }
    @Test fun neighboursAnimateWhileTheHeldCardTracksDirectlyAndMotionCanBeDisabled() {
        try {
            defaults(true);val from=card(debt).fetchSemanticsNode().boundsInRoot
            val neighbour=card(savings).fetchSemanticsNode().boundsInRoot
            val distance=neighbour.left-from.left
            ui.mainClock.autoAdvance=false
            card(debt).performTouchInput{down(Offset(center.x,18f));advanceEventTime(700);moveBy(Offset(distance,0f))}
            ui.mainClock.advanceTimeBy(80);ui.waitForIdle()
            assertEquals(neighbour.left,card(debt).fetchSemanticsNode().boundsInRoot.left,3f)
            val movingNeighbour=card(savings).fetchSemanticsNode().boundsInRoot.left
            assertTrue("Neighbour should interpolate, not jump",movingNeighbour>from.left&&movingNeighbour<neighbour.left)
            ui.mainClock.advanceTimeBy(300);ui.waitForIdle()
            assertEquals(from.left,card(savings).fetchSemanticsNode().boundsInRoot.left,3f)
            card(debt).performTouchInput{up()};ui.mainClock.advanceTimeBy(300);ui.waitForIdle()
            ui.mainClock.autoAdvance=true
            shot("move-settled-no-grid-lines")
            ui.runOnIdle{ui.activity.displayController.setAnimations(false)}
            val now=card(debt).fetchSemanticsNode().boundsInRoot
            card(debt).performTouchInput{down(Offset(center.x,18f));advanceEventTime(700);moveBy(Offset(-distance,0f));up()};ui.waitForIdle()
            assertEquals(from.left,card(debt).fetchSemanticsNode().boundsInRoot.left,3f)
            assertEquals(0,ui.activity.displayController.state.value.cardGrids.getValue(debt).column)
            assertEquals(now.width,card(debt).fetchSemanticsNode().boundsInRoot.width,1f)
        }finally{cleanup()}
    }
}
