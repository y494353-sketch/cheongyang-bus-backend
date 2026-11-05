package com.example.cheongyangbus

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.example.cheongyangbus.data.AlarmRepository
import com.example.cheongyangbus.data.FavoriteRepository
import com.example.cheongyangbus.data.Prefs
import com.example.cheongyangbus.data.SeoulTime
import com.example.cheongyangbus.data.currentStatus
import com.example.cheongyangbus.databinding.ActivityBusDetailBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class BusDetailActivity : BaseActivity() {
    private lateinit var binding: ActivityBusDetailBinding
    private lateinit var bus: BusTime
    private lateinit var favorites: FavoriteRepository
    private lateinit var alarms: AlarmRepository
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBusDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prepareScreen(binding.root, binding.toolbar)
        bus = BusTime.fromIntent(intent)
        binding.routeTitle.text = bus.route
        binding.departureTime.text = bus.departure
        binding.arrivalTime.text = bus.arrival
        binding.departureLabel.text = "${bus.origin} · ${getString(R.string.departure)}"
        binding.arrivalLabel.text = "${bus.destination} · ${getString(R.string.arrival)}"
        binding.durationText.text = bus.duration
        favorites = FavoriteRepository(this)
        alarms = AlarmRepository(this)
        lifecycleScope.launch { updateButtons() }
        // 즐겨찾기: Room favorites 테이블에 저장/해제
        binding.favoriteButton.setOnClickListener {
            lifecycleScope.launch {
                val saved = favorites.toggle(bus)
                updateButtons()
                Snackbar.make(binding.root, if (saved) R.string.favorite_saved else R.string.favorite_removed,
                    Snackbar.LENGTH_SHORT).show()
            }
        }
        // 출발 알림: 출발 10분 전에 휴대폰 알림 예약/해제
        binding.alarmButton.setOnClickListener {
            lifecycleScope.launch {
                if (alarms.isSet(bus)) {
                    alarms.cancel(bus)
                    Snackbar.make(binding.root, R.string.alarm_off, Snackbar.LENGTH_SHORT).show()
                } else {
                    askNotificationPermission()
                    val at = alarms.set(bus)
                    val time = SeoulTime.formatter("M월 d일 HH:mm").format(at)
                    val off = if (Prefs.alarmsOn(this@BusDetailActivity)) ""
                    else "\n지금은 내 정보에서 출발 알림이 꺼져 있어 울리지 않습니다."
                    MaterialAlertDialogBuilder(this@BusDetailActivity).setTitle(R.string.alarm_title)
                        .setMessage("${time}에 출발 10분 전 알림이 울립니다.$off")
                        .setPositiveButton(R.string.confirm, null).show()
                }
                updateButtons()
            }
        }
    }
    // 화면에 돌아올 때마다 지금 시각 기준으로 운행 상태 갱신
    override fun onResume() {
        super.onResume()
        binding.statusText.text = currentStatus(bus.departure, bus.arrival)
    }
    private suspend fun updateButtons() {
        binding.favoriteButton.setText(if (favorites.isSaved(bus)) R.string.remove_favorite else R.string.add_favorite)
        binding.alarmButton.setText(if (alarms.isSet(bus)) R.string.cancel_alarm else R.string.set_alarm)
    }
    // Android 13 이상은 알림 권한을 물어봐야 알림이 뜸
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
    }
}
