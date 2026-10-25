package com.campusfinance.wallet

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.os.PowerManager
import android.app.KeyguardManager
import android.view.FrameMetrics
import android.view.Window
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil

/** Real Choreographer timing, without Compose's test animation clock. Not a release benchmark. */
@RunWith(AndroidJUnit4::class)
class FrameTimingTest {
    @Test fun recordRealTimeNavigationFrames() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        context.getSharedPreferences("wallet_settings",Context.MODE_PRIVATE).edit().putString("mode","demo").putInt(TutorialPolicy.KEY,TutorialPolicy.VERSION).putBoolean("high_refresh",true).putBoolean("motion_enabled",true).commit()
        val version=context.packageManager.getPackageInfo(context.packageName,0).versionName ?: "unknown"
        val samples=ConcurrentLinkedQueue<Pair<Long,Long>>()
        val dropped=AtomicInteger()
        val callbacks=AtomicInteger()
        val firstDraws=AtomicInteger()
        val worker=HandlerThread("frame-observer").apply{start()}
        val listener=Window.OnFrameMetricsAvailableListener { _,metrics,lost ->
            dropped.addAndGet(lost)
            callbacks.incrementAndGet()
            if(metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME)!=0L)firstDraws.incrementAndGet()
            samples.add(metrics.getMetric(FrameMetrics.VSYNC_TIMESTAMP) to metrics.getMetric(FrameMetrics.TOTAL_DURATION))
        }
        lateinit var scenarioRef:ActivityScenario<MainActivity>
        fun findRoot(view:View):RootForTest? {
            if(view is RootForTest)return view
            if(view is ViewGroup)for(i in 0 until view.childCount)findRoot(view.getChildAt(i))?.let{return it}
            return null
        }
        fun click(label:String) {
            val deadline=SystemClock.uptimeMillis()+10000
            while(SystemClock.uptimeMillis()<deadline) {
                var clicked=false
                scenarioRef.onActivity { activity ->
                    fun visit(node:SemanticsNode) {
                        if(clicked)return
                        if(node.config.getOrNull(SemanticsProperties.Text)?.any{it.text==label}==true) {
                            var candidate:SemanticsNode?=node
                            while(candidate!=null && !clicked) {
                                clicked=candidate.config.getOrNull(SemanticsActions.OnClick)?.action?.invoke()==true
                                candidate=candidate.parent
                            }
                        }
                        if(!clicked)node.children.forEach{visit(it)}
                    }
                    findRoot(activity.window.decorView)?.semanticsOwner?.rootSemanticsNode?.let{visit(it)}
                }
                if(clicked)return
                SystemClock.sleep(40)
            }
            error("找不到导航操作：$label")
        }
        var hz=0f;var requested=0f;var rates=emptyList<Float>()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenarioRef=scenario
                SystemClock.sleep(1000)
                scenario.onActivity { activity ->
                    @Suppress("DEPRECATION")
                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
                }
                // Warm up every screen before starting the measured sample.
                listOf("账本","规划","分析","首页").forEach{click(it);SystemClock.sleep(520)}
                scenario.onActivity { activity ->
                    val display=activity.window.decorView.display
                    hz=display.refreshRate
                    requested=activity.window.attributes.preferredRefreshRate
                    rates=display.supportedModes.filter{it.physicalWidth==display.mode.physicalWidth&&it.physicalHeight==display.mode.physicalHeight}.map{it.refreshRate}.distinct()
                    activity.window.addOnFrameMetricsAvailableListener(listener,Handler(worker.looper))
                }
                repeat(3) { listOf("账本","规划","分析","首页").forEach{click(it);SystemClock.sleep(520)} }
                scenario.onActivity{it.window.removeOnFrameMetricsAvailableListener(listener)}
                SystemClock.sleep(80)
                val dir=File(context.getExternalFilesDir(null),"metrics").apply{mkdirs()}
                val diagnostic=JSONObject().put("callbacks",callbacks.get()).put("firstDraws",firstDraws.get()).put("sampleFrames",samples.size)
                scenario.onActivity { a -> diagnostic.put("focus",a.hasWindowFocus()).put("visible",a.window.decorView.isShown)
                    .put("interactive",a.getSystemService(PowerManager::class.java).isInteractive).put("keyguard",a.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
                    .put("requestedHz",a.window.attributes.preferredRefreshRate.toDouble()).put("currentHz",a.window.decorView.display.refreshRate.toDouble()) }
                File(dir,"diagnostic-$version-api${Build.VERSION.SDK_INT}.json").writeText(diagnostic.toString(2))
                assertTrue("应采集到真实绘制帧：$diagnostic",samples.size>5)
                val durations=samples.map{it.second/1_000_000.0}.sorted()
                val intervals=samples.map{it.first}.distinct().sorted().zipWithNext{a,b->(b-a)/1_000_000.0}.filter{it>0.0&&it<=100.0}.sorted()
                fun percentile(values:List<Double>,fraction:Double)=values[(ceil(values.size*fraction).toInt()-1).coerceIn(0,values.lastIndex)]
                val budget=1000.0/hz
                val result=JSONObject().put("version",version).put("sdk",Build.VERSION.SDK_INT).put("displayHz",hz.toDouble())
                    .put("supportedHz",JSONArray(rates)).put("windowRequestedHz",requested.toDouble())
                    .put("frames",durations.size).put("frameBudgetMs",budget).put("durationP50Ms",percentile(durations,.5))
                    .put("durationP90Ms",percentile(durations,.9)).put("durationP95Ms",percentile(durations,.95))
                    .put("durationMaxMs",durations.last()).put("framesOverBudget",durations.count{it>budget})
                    .put("metricsRecordsDropped",dropped.get()).put("firstDrawFrames",firstDraws.get()).put("workflow","12 semantic navigation actions on UI thread, 520 ms apart, after 4 warm-up switches; real animation clock")
                    .put("device",Build.MODEL).put("limitations","debug APK; callback timings, not a guarantee of sustained FPS; idle gaps over 100 ms excluded from cadence")
                if(intervals.isNotEmpty())result.put("activeIntervalP50Ms",percentile(intervals,.5)).put("activeCadenceP50Fps",1000.0/percentile(intervals,.5))
                File(dir,"frames-$version-api${Build.VERSION.SDK_INT}.json").writeText(result.toString(2))
            }
        } finally { worker.quitSafely() }
    }
}

