package com.example.cheongyangbus

import android.content.Intent
import android.os.Bundle
import com.example.cheongyangbus.data.Prefs
import com.example.cheongyangbus.databinding.ActivityMyPageBinding

class MyPageActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMyPageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prepareScreen(binding.root, binding.toolbar)
        binding.userName.text = Prefs.loginId(this) ?: getString(R.string.user)
        binding.favoritesButton.setOnClickListener { startActivity(Intent(this, FavoriteActivity::class.java)) }
        // 출발 알림 켜기/끄기 (끄면 예약해 둔 알림이 울리지 않음)
        binding.notificationsSwitch.isChecked = Prefs.alarmsOn(this)
        binding.notificationsSwitch.setOnCheckedChangeListener { _, on -> Prefs.setAlarmsOn(this, on) }
        binding.logoutButton.setOnClickListener {
            Prefs.setLoginId(this, null)
            // 이전 화면 스택을 비워 뒤로가기로 메인 화면에 재진입하지 않습니다.
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
    }
}
