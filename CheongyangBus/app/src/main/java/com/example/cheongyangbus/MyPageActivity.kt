
package com.example.cheongyangbus

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.example.cheongyangbus.data.Api
import com.example.cheongyangbus.databinding.ActivityMyPageBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.io.IOException

class MyPageActivity : BaseActivity() {

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

        // 마이페이지 화면 설정
        val binding = ActivityMyPageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prepareScreen(binding.root, binding.toolbar)

        // 로그인한 아이디 표시
        binding.userName.text =
            AccountStore.loginId(this) ?: getString(R.string.user)

        // 즐겨찾기
        binding.favoritesButton.setOnClickListener {
            startActivity(
                Intent(this, FavoriteActivity::class.java)
            )
        }

        // 출발 알림 켜기/끄기 (끄면 예약해 둔 알림이 울리지 않음)
        binding.notificationsSwitch.isChecked = AccountStore.alarmsOn(this)
        binding.notificationsSwitch.setOnCheckedChangeListener { _, on ->
            AccountStore.setAlarmsOn(this, on)
        }

        // 로그아웃
        binding.logoutButton.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("로그아웃")
                .setMessage("로그아웃하시겠습니까?")
                .setNegativeButton("취소", null)
                .setPositiveButton("로그아웃") { _, _ ->

                    // 로그인 상태 해제 (서버 토큰도 폐기)
                    val token = AccountStore.token(this)
                    AccountStore.logout(this)
                    lifecycleScope.launch {
                        try {
                            Api.logout(token)
                        } catch (_: IOException) {
                        }

                        // 이전 화면을 제거하고 로그인 화면으로 이동
                        val intent = Intent(
                            this@MyPageActivity,
                            LoginActivity::class.java
                        ).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }

                        startActivity(intent)
                        finish()
                    }
                }
                .show()
        }
    }
}
