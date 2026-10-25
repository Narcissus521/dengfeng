package com.campusfinance.wallet

import org.junit.Assert.*
import org.junit.Test

class CardGridPolicyTest {
    private val home=listOf(OverviewCard.HOME_SUMMARY,OverviewCard.HOME_PURCHASE,OverviewCard.HOME_BUDGET,OverviewCard.HOME_DEBT,OverviewCard.HOME_SAVINGS)
    private fun validate(result:List<PlacedGridCard>,cards:List<OverviewCard>,rows:Int) {
        assertEquals(cards.toSet(),result.map{it.card}.toSet())
        val cells=mutableSetOf<Triple<Int,Int,Int>>()
        result.forEach{(_,v)->assertTrue(v.columns in 2..4);assertTrue(v.rows in 1..minOf(4,rows));assertTrue(v.column>=0&&v.column+v.columns<=4);assertTrue(v.row>=0&&v.row+v.rows<=rows)
            for(y in v.row until v.row+v.rows)for(x in v.column until v.column+v.columns)assertTrue("Overlapping cells",cells.add(Triple(v.page,y,x)))}
    }
    @Test fun defaultHomeFitsOnePageWithTheDebtAtTwoByOne() {
        val result=CardGridPolicy.pack(home,emptyMap(),7);validate(result,home,7)
        assertTrue(result.all{it.layout.page==0})
        assertEquals(CardGridLayout(2,1,0,6,0),result.first{it.card==OverviewCard.HOME_DEBT}.layout)
        assertEquals(2,result.first{it.card==OverviewCard.HOME_SAVINGS}.layout.column)
    }
    @Test fun narrowingFromTheLeftKeepsTheRightEdgeAnchored() {
        val saved=mapOf(OverviewCard.HOME_BUDGET to CardGridLayout(2,2,2,4,0))
        val result=CardGridPolicy.pack(home,saved,7,OverviewCard.HOME_BUDGET);validate(result,home,7)
        val b=result.first{it.card==OverviewCard.HOME_BUDGET}.layout
        assertEquals(4,b.column+b.columns);assertEquals(2,b.rows)
    }
    @Test fun growingHeightReflowsOtherCardsAndKeepsTheActiveCardOnItsPage() {
        val saved=mapOf(OverviewCard.HOME_BUDGET to CardGridLayout(2,4,0,3,0))
        val result=CardGridPolicy.pack(home,saved,7,OverviewCard.HOME_BUDGET);validate(result,home,7)
        assertEquals(0,result.first{it.card==OverviewCard.HOME_BUDGET}.layout.page)
        assertTrue(result.any{it.layout.page>0})
    }
    @Test fun savedPositionsRestoreExactlyWithoutAnotherReflow() {
        val first=CardGridPolicy.pack(home,mapOf(OverviewCard.HOME_DEBT to CardGridLayout(2,4,2,3,0)),7,OverviewCard.HOME_DEBT)
        assertEquals(first,CardGridPolicy.pack(home,first.associate{it.card to it.layout},7))
    }
    @Test fun smallWindowsClampOnlyTheRenderedLayoutAndKeepEveryCardReachable() {
        val saved=home.associateWith{CardGridLayout(4,4,0,0,0)}
        val result=CardGridPolicy.pack(home,saved,1);validate(result,home,1)
        assertEquals(home.size,result.map{it.layout.page}.distinct().size)
        assertTrue(saved.values.all{it.rows==4})
    }
    @Test fun malformedPreferencesCannotProduceOffscreenCellsOrHugePageNumbers() {
        val saved=home.associateWith{CardGridLayout(Int.MAX_VALUE,Int.MIN_VALUE,Int.MAX_VALUE,Int.MAX_VALUE,Int.MAX_VALUE)}
        validate(CardGridPolicy.pack(home,saved,7,OverviewCard.HOME_SUMMARY),home,7)
    }
    @Test fun allCardShapesPackWithoutOverlapAcrossAvailableViewportHeights() {
        val cards=OverviewCard.entries.toList()
        for(rows in 1..8)for(w in 2..4)for(h in 1..4) {
            val sizes=cards.associateWith{CardGridLayout(w,h)}
            validate(CardGridPolicy.pack(cards,sizes,rows),cards,rows)
        }
    }
    @Test fun repeatedDirectionChangesNeverLoseCards() {
        var saved=emptyMap<OverviewCard,CardGridLayout>()
        for(i in 0..40){val c=home[i%home.size];val old=saved[c] ?: CardGridPolicy.default(c)
            val result=CardGridPolicy.pack(home,saved+(c to old.copy(columns=if(i%2==0)2 else 4,rows=i%4+1)),7,c)
            validate(result,home,7);saved=result.associate{it.card to it.layout}}
    }
    @Test fun widthAndHeightSnapIndependentlyAtHalfACell() {
        assertEquals(4,CardGridPolicy.snap(4,-49f,100f,2,4))
        assertEquals(3,CardGridPolicy.snap(4,-51f,100f,2,4))
        assertEquals(2,CardGridPolicy.snap(4,-1000f,100f,2,4))
        assertEquals(4,CardGridPolicy.snap(1,1000f,100f,1,4))
    }
    @Test fun invalidGestureMeasurementsCannotCorruptSizes() {
        for(delta in listOf(Float.NaN,Float.POSITIVE_INFINITY))assertEquals(2,CardGridPolicy.snap(2,delta,100f,2,4))
        assertEquals(2,CardGridPolicy.snap(2,20f,0f,2,4))
    }
    @Test fun movingSnapsPositionWithoutChangingSizeAndClampsAtTheViewportEdges() {
        val v=CardGridLayout(2,2,1,3,0)
        assertEquals(v.copy(column=2,row=1),CardGridPolicy.move(v,100f,-200f,100f,100f,7))
        assertEquals(v.copy(column=0,row=5),CardGridPolicy.move(v,-10000f,10000f,100f,100f,7))
        assertEquals(v,CardGridPolicy.move(v,Float.NaN,Float.POSITIVE_INFINITY,100f,100f,7))
        assertEquals(v,CardGridPolicy.move(v,40f,40f,0f,0f,7))
    }
    @Test fun movingReservesUnchangedAnchorsBeforeDisplacingTheCollidingCard() {
        val before=CardGridPolicy.pack(home,emptyMap(),7).associate{it.card to it.layout}
        val debt=before.getValue(OverviewCard.HOME_DEBT).copy(column=2)
        val after=CardGridPolicy.pack(home,before+(OverviewCard.HOME_DEBT to debt),7,OverviewCard.HOME_DEBT)
        validate(after,home,7)
        val saved=after.associate{it.card to it.layout}
        assertEquals(debt,saved[OverviewCard.HOME_DEBT])
        assertEquals(0,saved.getValue(OverviewCard.HOME_SAVINGS).column)
        for(c in home.take(3))assertEquals(before[c],saved[c])
        assertEquals(after,CardGridPolicy.pack(home,saved,7))
    }
    @Test fun newAnalysisOverviewFitsSixRowsWithoutLosingResizableCards() {
        val cards=listOf(OverviewCard.ANALYSIS_TOTAL,OverviewCard.ANALYSIS_CHART,OverviewCard.ANALYSIS_TREND)
        val placed=CardGridPolicy.pack(cards,emptyMap(),6);validate(placed,cards,6);assertTrue(placed.all{it.layout.page==0})
    }

}
