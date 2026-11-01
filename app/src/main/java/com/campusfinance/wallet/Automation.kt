package com.campusfinance.wallet

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZonedDateTime

/** Notifications only. Financial entries always require an explicit foreground confirmation. */
object WalletAutomation {
    private const val CHANNEL="tea-journal"
    private fun alarmIntent(context: Context) = PendingIntent.getBroadcast(context,701,
        Intent(context,AutomationReceiver::class.java).setAction("com.campusfinance.wallet.PROCESS_DUE"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun notificationsAllowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT<33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) &&
            context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    suspend fun run(context: Context, notify: Boolean = true) {
        val prefs=context.getSharedPreferences("wallet_settings",Context.MODE_PRIVATE)
        if(!prefs.getBoolean(ConsentPolicy.KEY,false))return
        val mode=prefs.getString("mode",null) ?: return
        val db=WalletDatabase.open(context,mode=="demo")
        try {
            val repo=WalletRepository(db)
            repo.prepare(mode=="demo")
            val state=db.walletDao().read()
            val now=ZonedDateTime.now(financeZone)
            if(notify) remind(context,state,now,mode)
            schedule(context,state,now)
            prefs.edit().remove("automation_error_$mode").apply()
        } catch(e: Exception) {
            prefs.edit().putString("automation_error_$mode",e.message ?: "请打开账本核对固定计划").apply()
            scheduleRetry(context)
        } finally { db.close() }
    }
    @Synchronized internal fun remind(context: Context,s: Ledger,now: ZonedDateTime,mode: String) {
        val r=s.reminder
        if(!r.enabled || !notificationsAllowed(context))return
        val day=ReminderPolicy.windowDay(LocalTime.parse(r.startTime),LocalTime.parse(r.endTime),now) ?: return
        val prefs=context.getSharedPreferences("wallet_settings",Context.MODE_PRIVATE)
        val key="reminded_${mode}_$day"
        if(prefs.getBoolean(key,false))return
        run {
            val manager=context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL,"每日记账提醒",NotificationManager.IMPORTANCE_DEFAULT))
            val launch=PendingIntent.getActivity(context,702,Intent(context,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification=Notification.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_dengfeng_notification)
                .setContentTitle("等风 · 记账提醒")
                .setContentText("${r.selectedCategories().joinToString("、")} · 回看今天的收入与花费")
                .setContentIntent(launch).setAutoCancel(true).build()
            manager.notify(702,notification)
        }
        prefs.edit().putBoolean(key,true).apply()
    }
    private fun schedule(context: Context,s: Ledger,now: ZonedDateTime) {
        val candidates=mutableListOf<ZonedDateTime>()
        if(s.reminder.enabled) {
            candidates+=ReminderPolicy.nextStart(LocalTime.parse(s.reminder.startTime),now)
        }
        val alarm=context.getSystemService(AlarmManager::class.java)
        val next=candidates.minOrNull()
        if(next==null)alarm.cancel(alarmIntent(context))
        else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next.toInstant().toEpochMilli(),alarmIntent(context))
    }
    private fun scheduleRetry(context: Context) {
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis()+60*60*1000L,alarmIntent(context))
    }
}

class AutomationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        if(intent.action !in setOf("com.campusfinance.wallet.PROCESS_DUE",Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED,Intent.ACTION_MY_PACKAGE_REPLACED))return
        val pending=goAsync()
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch {
            try { WalletAutomation.run(context.applicationContext) } finally { pending.finish() }
        }
    }
}
