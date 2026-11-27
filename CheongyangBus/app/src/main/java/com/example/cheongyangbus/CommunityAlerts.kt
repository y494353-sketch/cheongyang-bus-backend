package com.example.cheongyangbus

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.cheongyangbus.data.Api
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONException
import java.io.IOException

internal object CommunityAlerts {
    private const val CHANNEL = "community_replies"
    private const val JOB_ID = 6241
    private val lock = Mutex()

    private fun prefs(context: Context) = context.getSharedPreferences("community_alerts", Context.MODE_PRIVATE)
    private fun account(context: Context) = AccountStore.loginId(context).orEmpty()
    private fun enabledKey(account: String) = "enabled:$account"

    fun enabled(context: Context) = prefs(context).getBoolean(enabledKey(account(context)), true)

    fun setEnabled(context: Context, enabled: Boolean) {
        val user = account(context)
        prefs(context).edit().putBoolean(enabledKey(user), enabled).apply()
        if (!enabled) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.activeNotifications.filter { it.tag == CHANNEL }.forEach { manager.cancel(it.tag, it.id) }
        }
    }

    fun askOnce(activity: Activity) {
        if (enabled(activity) && !prefs(activity).getBoolean("permission_asked", false)) {
            prefs(activity).edit().putBoolean("permission_asked", true).apply()
            requestPermission(activity)
        }
    }

    fun requestPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), JOB_ID)
        }
    }

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (scheduler.getPendingJob(JOB_ID) != null) return
        scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(context, CommunityAlertJob::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPeriodic(15 * 60 * 1000L)
            .setPersisted(true)
            .build())
    }

    suspend fun check(context: Context) = lock.withLock {
        val token = AccountStore.token(context) ?: return@withLock
        val user = AccountStore.loginId(context) ?: return@withLock
        val settings = prefs(context)
        val key = "after:$user"
        val after = settings.getLong(key, 0)
        try {
            val items = JSONArray(Api.get("/api/community/notifications?after=$after", token))
            if (AccountStore.token(context) != token) return@withLock
            for (index in 0 until items.length()) {
                val notification = items.getJSONObject(index)
                val id = notification.getLong("notification_id")
                if (settings.getBoolean(enabledKey(user), true)) {
                    val post = notification.getLong("post_id")
                    val title = notification.getString("title")
                    val reply = !notification.isNull("parent_id")
                    if (!show(context, id, post, title, reply)) break
                }
                settings.edit().putLong(key, id).apply()
            }
        } catch (_: IOException) {
            // The next foreground or scheduled check retries.
        } catch (_: JSONException) {
            // An incompatible backend response will be retried on the next check.
        }
    }

    private fun show(context: Context, id: Long, post: Long, title: String, reply: Boolean): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return false

        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "커뮤니티 댓글 알림", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val open = PendingIntent.getActivity(context, id.toInt(), CommunityPostActivity.intent(context, post),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_community)
            .setContentTitle(if (reply) "새 답글이 달렸어요" else "새 댓글이 달렸어요")
            .setContentText(title)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(CHANNEL, id.toInt(), notification)
        return true
    }
}

class CommunityAlertJob : JobService() {
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        running = CoroutineScope(Dispatchers.Main).launch {
            CommunityAlerts.check(applicationContext)
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return true
    }
}