package com.campusfinance.wallet

import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil

/** Targeted real frame-clock sample in the emulator's demo ledger; never use a personal device. */
@RunWith(AndroidJUnit4::class)
class ExpenseOpeningTimingTest {
    private data class Sample(val cycle:Int,val timestamp:Long,val total:Long,val layout:Long,val draw:Long,val sync:Long,val command:Long)
    @Test fun measureFirstAndRepeatedExpenseOpening() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("wallet_settings",0)
        prefs.edit().putString("mode","demo").putInt(TutorialPolicy.KEY,TutorialPolicy.VERSION)
            .putString("theme_mode","LIGHT").putBoolean("motion_enabled",true).putBoolean("high_refresh",true).commit()
        val version=context.packageManager.getPackageInfo(context.packageName,0).versionName ?: "unknown"
        val report=File(context.getExternalFilesDir(null),"metrics/expense-open-$version.json")
        if(report.exists())check(report.delete()) { "Could not remove the previous timing report" }
        val samples=ConcurrentLinkedQueue<Sample>();val stage=AtomicInteger(-1);val lost=AtomicInteger()
        val worker=HandlerThread("expense-frame-observer").apply{start()}
        val listener=Window.OnFrameMetricsAvailableListener {_,m,dropped->
            lost.addAndGet(dropped);val i=stage.get()
            if(i>=0)samples.add(Sample(i,m.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP),m.getMetric(FrameMetrics.TOTAL_DURATION),
                m.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION),m.getMetric(FrameMetrics.DRAW_DURATION),m.getMetric(FrameMetrics.SYNC_DURATION),m.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION)))
        }
        lateinit var scenarioRef:ActivityScenario<MainActivity>
        fun root(v:View):RootForTest? {if(v is RootForTest)return v;if(v is ViewGroup)for(i in 0 until v.childCount)root(v.getChildAt(i))?.let{return it};return null}
        fun invoke(node:SemanticsNode,tag:String?,label:String?):Boolean {
            val matches=tag!=null&&node.config.getOrNull(SemanticsProperties.TestTag)==tag || label!=null&&node.config.getOrNull(SemanticsProperties.Text)?.any{it.text==label}==true
            if(matches){var n:SemanticsNode?=node;while(n!=null){if(n.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke()==true)return true;n=n.parent}}
            return node.children.any{invoke(it,tag,label)}
        }
        fun click(tag:String?=null,label:String?=null) {
            val deadline=SystemClock.uptimeMillis()+15000
            while(SystemClock.uptimeMillis()<deadline) {
                var done=false
                scenarioRef.onActivity {a->done=root(a.window.decorView)?.semanticsOwner?.rootSemanticsNode?.let{invoke(it,tag,label)}==true}
                if(done)return
                SystemClock.sleep(30)
            }
            error("Cannot find expense action: $tag / $label")
        }
        val starts=LongArray(8);var hz=0f
        try {
            ActivityScenario.launch(MainActivity::class.java).use {scenario->
                scenarioRef=scenario
                SystemClock.sleep(1500)
                scenario.onActivity{a->a.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);hz=a.window.decorView.display.refreshRate
                    a.window.addOnFrameMetricsAvailableListener(listener,Handler(worker.looper))}
                val db=WalletDatabase.open(context,true)
                try {
                    val before=runBlocking{db.walletDao().read()}
                    repeat(8){i->
                        click(tag="dock-记账");SystemClock.sleep(220)
                        stage.set(i);starts[i]=System.nanoTime();click(tag="leaf-EXPENSE")
                        SystemClock.sleep(820);stage.set(-1)
                        click(label="‹ 返回");SystemClock.sleep(460)
                    }
                    val after=runBlocking{db.walletDao().read()}
                    assertEquals("Opening expense must never post money",before.entries,after.entries)
                    assertEquals(before.accounts,after.accounts)
                }finally{db.close()}
                scenario.onActivity{it.window.removeOnFrameMetricsAvailableListener(listener)}
                SystemClock.sleep(100)
                fun p(v:List<Double>,f:Double)=v[(ceil(v.size*f).toInt()-1).coerceIn(0,v.lastIndex)]
                val cycles=JSONArray();val budget=1000.0/hz
                repeat(8){i->
                    val frames=samples.filter{it.cycle==i&&it.timestamp>=starts[i]}.sortedBy{it.timestamp}
                    assertTrue("Expense opening should draw frames, cycle $i",frames.size>5)
                    val durations=frames.map{it.total/1_000_000.0}.sorted()
                    cycles.put(JSONObject().put("cycle",i).put("firstEntry",i==0).put("frames",frames.size)
                        .put("p50Ms",p(durations,.5)).put("p95Ms",p(durations,.95)).put("maxMs",durations.last())
                        .put("overBudget",durations.count{it>budget}).put("maxLayoutMs",frames.maxOf{it.layout}/1_000_000.0)
                        .put("maxDrawMs",frames.maxOf{it.draw}/1_000_000.0).put("maxSyncMs",frames.maxOf{it.sync}/1_000_000.0)
                        .put("maxCommandMs",frames.maxOf{it.command}/1_000_000.0)
                        .put("raw",JSONArray(frames.map{f->JSONObject().put("totalMs",f.total/1_000_000.0).put("layoutMs",f.layout/1_000_000.0).put("drawMs",f.draw/1_000_000.0)})))
                }
                report.parentFile!!.mkdirs()
                report.writeText(JSONObject().put("version",version).put("sdk",Build.VERSION.SDK_INT)
                    .put("debuggable",context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
                    .put("device",Build.MODEL).put("displayHz",hz.toDouble()).put("budgetMs",budget).put("lostMetricRecords",lost.get())
                    .put("cycles",cycles).put("limitations","Emulator with real frame clock; eight opens after app setup, no transaction submitted; baseline is debug, final is non-debuggable release; no claim of phone FPS").toString(2))
            }
        }finally{worker.quitSafely()}
    }
}
