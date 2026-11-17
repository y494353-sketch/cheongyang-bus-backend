
package com.example.cheongyangbus

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.cheongyangbus.data.AlarmRepository
import com.example.cheongyangbus.data.Api
import com.example.cheongyangbus.data.SeoulTime
import com.example.cheongyangbus.data.currentStatus
import com.example.cheongyangbus.databinding.ActivityBusDetailBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Date

class BusDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityBusDetailBinding
    private lateinit var bus: BusTime
    private lateinit var alarms: AlarmRepository
    private var isFavorite = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 로그인 여부 확인
        if (!AccountStore.isLoggedIn(this)) {
            val intent = Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            startActivity(intent)
            finish()
            return
        }

        // 버스 상세 화면 설정
        binding = ActivityBusDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prepareScreen(binding.root, binding.toolbar)

        // 버스 정보 가져오기
        bus = BusTime.fromIntent(intent)

        binding.routeTitle.text = bus.route
        binding.departureTime.text = bus.departure
        binding.arrivalTime.text = bus.arrival

        binding.departureLabel.text =
            "${bus.origin} · ${getString(R.string.departure)}"

        binding.arrivalLabel.text =
            "${bus.destination} · ${getString(R.string.arrival)}"

        binding.durationText.text = bus.duration

        alarms = AlarmRepository(this)
        val loginId = AccountStore.loginId(this) ?: return
        updateButtons()

        // 서버(MySQL favorite)에서 즐겨찾기 여부 확인
        lifecycleScope.launch {
            isFavorite = try {
                Api.favorites(loginId).any { it.scheduleId == bus.scheduleId }
            } catch (e: IOException) {
                false
            }
            updateButtons()
        }

        // 즐겨찾기 추가 및 해제 (서버에 저장)
        binding.favoriteButton.setOnClickListener {
            lifecycleScope.launch {
                val saved = isFavorite
                try {
                    if (saved) Api.removeFavorite(loginId, bus.scheduleId)
                    else Api.addFavorite(loginId, bus.scheduleId)
                } catch (e: IOException) {
                    Toast.makeText(
                        this@BusDetailActivity,
                        "서버에 연결할 수 없습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }
                isFavorite = !saved

                updateButtons()

                Snackbar.make(
                    binding.root,
                    if (saved) R.string.favorite_removed
                    else R.string.favorite_saved,
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }

        // 출발 알림: 출발 10분 전에 휴대폰 알림 예약/해제
        binding.alarmButton.setOnClickListener {
            if (alarms.isSet(bus)) {
                alarms.cancel(bus)

                Snackbar.make(
                    binding.root,
                    R.string.alarm_off,
                    Snackbar.LENGTH_SHORT
                ).show()
            } else {
                askNotificationPermission()
                val at = alarms.set(bus)
                val time = SeoulTime.formatter("M월 d일 HH:mm").format(Date(at))
                val off =
                    if (AccountStore.alarmsOn(this)) ""
                    else "\n지금은 내 정보에서 출발 알림이 꺼져 있어 울리지 않습니다."

                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.alarm_title)
                    .setMessage("${time}에 출발 10분 전 알림이 울립니다.$off")
                    .setPositiveButton(R.string.confirm, null)
                    .show()
            }

            updateButtons()
        }
    }

    // 화면에 돌아올 때마다 지금 시각 기준으로 운행 상태 갱신
    override fun onResume() {
        super.onResume()

        if (::binding.isInitialized) {
            binding.statusText.text = currentStatus(bus.departure, bus.arrival)
        }
    }

    // Android 13 이상은 알림 권한을 받아야 알림이 뜸
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
    }

    // 버튼 문구 갱신
    private fun updateButtons() {
        binding.favoriteButton.setText(
            if (isFavorite) {
                R.string.remove_favorite
            } else {
                R.string.add_favorite
            }
        )

        binding.alarmButton.setText(
            if (alarms.isSet(bus)) {
                R.string.cancel_alarm
            } else {
                R.string.set_alarm
            }
        )
    }
}
