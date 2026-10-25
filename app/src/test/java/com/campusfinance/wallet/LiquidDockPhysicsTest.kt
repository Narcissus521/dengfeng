package com.campusfinance.wallet

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class LiquidDockPhysicsTest {
    private fun advance(p:LiquidDockPhysics,seconds:Float,hz:Int=120){repeat((seconds*hz).toInt()){p.step(1f/hz)}}

    @Test fun tapSettlesAndStopsRequestingFrames() {
        val p=LiquidDockPhysics(0f)
        p.request(4f,false,.9f,.3f,false);p.pulse();advance(p,1.5f)
        assertTrue(p.settled);assertEquals(4f,p.frame.head,0f);assertEquals(4f,p.frame.tail,0f)
        val rest=p.frame;advance(p,.5f);assertEquals(rest,p.frame)
        assertFalse(p.request(4f,false,.9f,.3f,false))
    }
    @Test fun reversalKeepsTheCurrentFrameInsteadOfJumpingToAnOldSelection() {
        val p=LiquidDockPhysics(0f);p.request(4f,false,.9f,.3f,false);advance(p,.125f)
        val inFlight=p.frame
        assertTrue(inFlight.head>inFlight.tail)
        p.request(0f,false,.1f,.3f,false);assertEquals(inFlight,p.frame)
        p.step(1f/120f);assertTrue(abs(p.frame.head-inFlight.head)<.2f)
        advance(p,1.5f);assertTrue(p.settled);assertEquals(0f,p.frame.head,0f)
    }
    @Test fun dragTracksTheFingerAndReleaseDoesNotTeleport() {
        val p=LiquidDockPhysics(0f);p.request(2.6f,true,.62f,.7f,true)
        assertEquals(2.6f,p.frame.head,0f);assertEquals(0f,p.frame.tail,0f)
        advance(p,.1f);val held=p.frame
        p.request(3f,false,.7f,.3f,false);assertEquals(held,p.frame)
        advance(p,1.5f);assertTrue(p.settled);assertEquals(3f,p.frame.head,0f);assertEquals(0f,p.frame.lift,0f)
    }
    @Test fun cancellingAHoldReturnsToTheCommittedPage() {
        val p=LiquidDockPhysics(1f);p.request(4f,true,.9f,.5f,false);advance(p,.15f)
        p.request(1f,false,.3f,.3f,false);advance(p,1.5f)
        assertTrue(p.settled);assertEquals(1f,p.frame.head,0f);assertEquals(0f,p.frame.lift,0f)
    }
    @Test fun motionOffSnapsWithoutLeavingAPressedBulge() {
        val p=LiquidDockPhysics(0f);p.request(4f,true,.9f,.5f,false);p.snap(reducedMotion=true)
        assertEquals(4f,p.frame.head,0f);assertEquals(4f,p.frame.tail,0f);assertEquals(0f,p.frame.lift,0f)
    }
    @Test fun animationRemainsConsistentOn60And120HzScreens() {
        val a=LiquidDockPhysics(0f);val b=LiquidDockPhysics(0f)
        for(p in listOf(a,b))p.request(4f,true,.9f,.5f,false)
        advance(a,.25f,60);advance(b,.25f,120)
        assertEquals(a.frame.head,b.frame.head,.025f);assertEquals(a.frame.tail,b.frame.tail,.025f)
        assertEquals(a.frame.lift,b.frame.lift,.025f)
    }
    @Test fun interruptedEdgeAnimationsStayInsideNarrowAndWideDocks() {
        for(width in listOf(280f,320f,411f,600f))for(density in listOf(1f,2.625f)) {
            val p=LiquidDockPhysics(0f)
            repeat(300){i->
                if(i%7==0)p.request(if(i%2==0)4f else 0f,i%3==0,if(i%2==0)1f else 0f,.5f,i%5==0)
                p.step(1f/120f);val lens=dockLens(p.frame,width*density,66*density,density,5)
                assertTrue(listOf(lens.left,lens.top,lens.width,lens.height,lens.lean).all{it.isFinite()})
                assertTrue(lens.left>=0f&&lens.top>=0f&&lens.width>0f&&lens.height>0f)
                assertTrue(lens.left+lens.width<=width*density+.01f)
                assertTrue(lens.top+lens.height<=66*density+.01f)
            }
        }
    }
    @Test fun invalidPointerSamplesAndStalledFramesDoNotBreakTheAnimation() {
        val p=LiquidDockPhysics(0f);p.request(4f,true,.9f,.5f,false)
        p.step(Float.NaN);p.step(-1f);p.step(Float.POSITIVE_INFINITY)
        p.request(Float.NaN,true,Float.NaN,Float.POSITIVE_INFINITY,false);p.step(10f)
        assertTrue(listOf(p.frame.head,p.frame.tail,p.frame.lift,p.frame.lightX,p.frame.lightY).all{it.isFinite()})
        p.request(4f,false,.9f,.3f,false);advance(p,1.5f);assertTrue(p.settled)
    }
}
