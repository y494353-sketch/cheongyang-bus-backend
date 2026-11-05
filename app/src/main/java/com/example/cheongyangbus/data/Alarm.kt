package com.example.cheongyangbus.data

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.example.cheongyangbus.BusTime
import com.example.cheongyangbus.R
import java.util.Calendar

/** alarms 테이블: 예약한 출발 알림 (버스 키, 알림이 울릴 시각) */
@Entity(tableName = "alarms")
data class BusAlarm(
    @PrimaryKey val busKey: String,
    val triggerAt: Long // 밀리초
)

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms WHERE busKey = :busKey")
    suspend fun find(busKey: String): BusAlarm?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(alarm: BusAlarm)

    @Query("DELETE FROM alarms WHERE busKey = :busKey")
    suspend fun delete(busKey: String)
}

/** 출발 10분 전 알림 예약·해제. 함수들은 lifecycleScope.launch { } 안에서 부릅니다. */
class AlarmRepository(private val context: Context) {

    private val dao = AppDatabase.get(context).alarmDao()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** 아직 울리지 않은 알림이 있으면 true */
    suspend fun isSet(bus: BusTime): Boolean =
        (dao.find(bus.key)?.triggerAt ?: 0L) > System.currentTimeMillis()

    /** 알림을 예약하고 울릴 시각(밀리초)을 돌려줍니다. 오늘 그 시각이 지났으면 내일로 예약합니다. */
    suspend fun set(bus: BusTime): Long {
        val at = triggerTime(bus.departure, System.currentTimeMillis())
        val pendingIntent = pendingIntent(bus)
        if (Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent)
        } else {
            // 정확한 알람 권한이 없으면 몇 분 늦게 울릴 수 있음
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent)
        }
        dao.insert(BusAlarm(bus.key, at))
        return at
    }

    suspend fun cancel(bus: BusTime) {
        alarmManager.cancel(pendingIntent(bus))
        dao.delete(bus.key)
    }

    private fun pendingIntent(bus: BusTime): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .putExtra("id", bus.key.hashCode())
            .putExtra("text", "${bus.route} ${bus.departure} 출발 10분 전입니다.")
        return PendingIntent.getBroadcast(
            context, bus.key.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/** 출발 시각 10분 전 (오늘 이미 지났으면 내일) */
internal fun triggerTime(departure: String, now: Long): Long {
    val time = SeoulTime.calendar(now).apply {
        set(Calendar.HOUR_OF_DAY, departure.substring(0, 2).toInt())
        set(Calendar.MINUTE, departure.substring(3, 5).toInt())
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.MINUTE, -10)
    }
    if (time.timeInMillis <= now) time.add(Calendar.DAY_OF_MONTH, 1)
    return time.timeInMillis
}

/** 예약한 시각에 휴대폰 알림을 띄웁니다. (AndroidManifest.xml 에 등록) */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 알림 권한을 거부했으면 띄우지 않음 (Android 13 이상)
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        // 내 정보에서 출발 알림을 꺼 두었으면 띄우지 않음
        if (!Prefs.alarmsOn(context)) return

        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "출발 알림", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        // 알림을 누르면 앱이 열림
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(context.getString(R.string.alarm_title))
            .setContentText(intent.getStringExtra("text"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open?.let { PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE) })
            .build()
        manager.notify(intent.getIntExtra("id", 0), notification)
    }

    companion object {
        private const val CHANNEL_ID = "departure_alarm"
    }
}
