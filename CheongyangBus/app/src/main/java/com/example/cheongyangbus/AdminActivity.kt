
package com.example.cheongyangbus

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.cheongyangbus.data.Api
import com.example.cheongyangbus.data.ApiException
import com.example.cheongyangbus.databinding.ActivityAdminBinding
import kotlinx.coroutines.launch
import java.io.IOException

// 관리자 대시보드: 노선·시간표·공지사항·회원 관리로 이동
class AdminActivity : BaseActivity() {

    private lateinit var binding: ActivityAdminBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 관리자 로그인 여부 확인
        if (!requireAdmin()) return

        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prepareScreen(binding.root)

        binding.adminIdText.text = "${AccountStore.loginId(this)} 관리자 계정으로 로그인되었습니다."

        // 노선 관리 화면 이동
        binding.routeManageButton.setOnClickListener {
            startActivity(Intent(this, RouteManageActivity::class.java))
        }

        // 버스 시간표 관리 화면 이동
        binding.scheduleManageButton.setOnClickListener {
            startActivity(Intent(this, ScheduleManageActivity::class.java))
        }

        binding.noticeManageButton.setOnClickListener {
            startActivity(Intent(this, NoticeManageActivity::class.java))
        }

        binding.memberManageButton.setOnClickListener {
            startActivity(Intent(this, MemberManageActivity::class.java))
        }

        // 관리자 로그아웃
        binding.logoutButton.setOnClickListener {
            val token = AccountStore.token(this)
            AccountStore.logout(this)

            lifecycleScope.launch {
                // 서버 토큰도 폐기 (서버에 연결이 안 돼도 기기에서는 로그아웃됨)
                try {
                    Api.logout(token)
                } catch (_: IOException) {
                }

                Toast.makeText(
                    this@AdminActivity,
                    "관리자 로그아웃되었습니다.",
                    Toast.LENGTH_SHORT
                ).show()

                openLogin()
            }
        }
    }
}

// ---------------------------------------------------------------- 관리자 화면 공통

// 관리자 로그인이 아니면 로그인 화면으로 보내고 false
internal fun Activity.requireAdmin(): Boolean {
    if (AccountStore.isAdmin(this)) return true
    AccountStore.logout(this)
    openLogin()
    return false
}

// 로그인 화면으로 이동 (이전 화면은 모두 닫음)
internal fun Activity.openLogin() {
    startActivity(Intent(this, LoginActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    })
    finish()
}

/**
 * 관리자 서버 호출 공통 처리. 성공하면 결과, 실패하면 안내를 띄우고 null.
 * 관리자 로그인이 풀렸으면(서버 재시작 등) 다시 로그인하도록 로그인 화면으로 보냅니다.
 */
internal suspend fun <T> Activity.adminCall(block: suspend (token: String?) -> T): T? =
    try {
        block(AccountStore.token(this))
    } catch (e: ApiException) {
        if (e.code == 401) {
            Toast.makeText(this, "관리자 로그인이 풀렸습니다. 다시 로그인해 주세요.", Toast.LENGTH_SHORT).show()
            AccountStore.logout(this)
            openLogin()
        } else {
            Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
        }
        null
    } catch (e: IOException) {
        Toast.makeText(this, "서버에 연결할 수 없습니다.", Toast.LENGTH_SHORT).show()
        null
    }
