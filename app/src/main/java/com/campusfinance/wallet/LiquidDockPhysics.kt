package com.campusfinance.wallet

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sqrt

data class DockFluidFrame(val head:Float,val tail:Float,val lift:Float,val lightX:Float,val lightY:Float)
data class DockLens(val left:Float,val top:Float,val width:Float,val height:Float,val lean:Float,val centerIndex:Float)

/** Small, bounded springs. Retargeting retains position and velocity, including rapid reversals. */
class LiquidDockPhysics(initial:Float,private val lastIndex:Int=4) {
    private class Axis(var position:Float,var velocity:Float=0f) {
        fun step(goal:Float,k:Float,damping:Float,dt:Float){
            velocity+=(k*(goal-position)-2f*sqrt(k)*damping*velocity)*dt
            position+=velocity*dt
        }
        fun at(goal:Float)=abs(position-goal)<.0008f&&abs(velocity)<.009f
        fun snap(goal:Float){position=goal;velocity=0f}
    }
    private val head=Axis(initial.coerceIn(0f,lastIndex.toFloat()))
    private val tail=Axis(head.position)
    private val lift=Axis(0f)
    private val lightX=Axis((head.position+.5f)/(lastIndex+1))
    private val lightY=Axis(.3f)
    private var goal=head.position
    private var pressure=0f
    private var lightGoalX=lightX.position
    private var lightGoalY=lightY.position
    private var direct=false
    val frame get()=DockFluidFrame(head.position,tail.position,lift.position.coerceIn(0f,1.12f),lightX.position,lightY.position)
    val settled get()=head.at(goal)&&tail.at(goal)&&lift.at(pressure)&&lightX.at(lightGoalX)&&lightY.at(lightGoalY)

    fun request(target:Float,pressed:Boolean,x:Float,y:Float,dragging:Boolean):Boolean {
        val next=if(target.isFinite())target.coerceIn(0f,lastIndex.toFloat()) else goal
        val nx=if(x.isFinite())x.coerceIn(0f,1f) else lightGoalX
        val ny=if(y.isFinite())y.coerceIn(0f,1f) else lightGoalY
        val changed=next!=goal||pressed!=(pressure>0f)||nx!=lightGoalX||ny!=lightGoalY||direct!=dragging
        if(!changed)return false
        if(dragging&&next!=head.position){head.velocity=((next-head.position)*32f).coerceIn(-12f,12f);head.position=next}
        if(direct&&!dragging)head.velocity=head.velocity.coerceIn(-4f,4f)
        goal=next;pressure=if(pressed)1f else 0f;lightGoalX=nx;lightGoalY=ny;direct=dragging
        return true
    }
    fun pulse(){lift.velocity=maxOf(lift.velocity,3f)}
    fun snap(reducedMotion:Boolean=false){head.snap(goal);tail.snap(goal);lift.snap(if(reducedMotion)0f else pressure);lightX.snap(lightGoalX);lightY.snap(lightGoalY)}
    fun step(seconds:Float) {
        if(!seconds.isFinite()||seconds<=0f)return
        val duration=seconds.coerceAtMost(.05f)
        val count=ceil(duration/(1f/240f)).toInt().coerceAtLeast(1);val dt=duration/count
        repeat(count) {
            if(direct){head.position=goal;head.velocity*=exp(-12f*dt)} else head.step(goal,760f,.78f,dt)
            tail.step(goal,310f,.84f,dt)
            lift.step(pressure,if(pressure>0)900f else 480f,.77f,dt)
            lightX.step(lightGoalX,420f,.9f,dt);lightY.step(lightGoalY,420f,.9f,dt)
        }
        if(settled)snap()
    }
}

/** Render within the Dock, including narrow windows and interrupted springs at either edge. */
fun dockLens(frame:DockFluidFrame,width:Float,height:Float,density:Float,count:Int):DockLens {
    val d=density.coerceAtLeast(.1f);val pad=4*d
    val step=((width-2*pad)/count.coerceAtLeast(1)).coerceAtLeast(1f)
    val centerIndex=(frame.head*.82f+frame.tail*.18f).coerceIn(-.12f,count-1+.12f)
    val lag=(frame.head-frame.tail).coerceIn(-1.4f,1.4f)
    val baseW=min((step-3*d).coerceAtLeast(d),68*d)
    val stretch=(abs(lag)*step*.58f).coerceAtMost(baseW*.4f)
    val lensW=(baseW+stretch+frame.lift*baseW*.035f).coerceAtMost((width-4*d).coerceAtLeast(d))
    val baseH=min(54*d,(height-10*d).coerceAtLeast(d))
    val lensH=(baseH*(1+frame.lift*.035f-stretch/baseW*.18f)).coerceAtMost((height-4*d).coerceAtLeast(d))
    val center=pad+step*(centerIndex+.5f)
    val left=(center-lensW/2).coerceIn(2*d,(width-lensW-2*d).coerceAtLeast(2*d))
    val top=((height-lensH)/2).coerceIn(2*d,(height-lensH-2*d).coerceAtLeast(2*d))
    return DockLens(left,top,lensW,lensH,lag*2.3f*d,centerIndex)
}
