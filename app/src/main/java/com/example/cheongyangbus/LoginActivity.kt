
package com.example.cheongyangbus

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.cheongyangbus.data.Prefs
import com.example.cheongyangbus.data.UserRepository
import com.example.cheongyangbus.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prepareScreen(binding.root)

        // 로그인: Room DB에서 아이디·비밀번호 확인
        binding.loginButton.setOnClickListener {
            val id = binding.userId.text.toString().trim()
            val pw = binding.password.text.toString()

            // 관리자 계정은 Room 회원 테이블이 아닌 시연용 관리자 계정으로 확인합니다.
            if (AccountStore.isAdminAccount(id, pw)) {
                AccountStore.setLoggedIn(this, loggedIn = true, isAdmin = true)
                startActivity(Intent(this, AdminActivity::class.java))
                finish()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                if (UserRepository(this@LoginActivity).login(id, pw)) {
                    // 일반 회원 로그인에서는 이전 관리자 세션이 남지 않게 합니다.
                    AccountStore.logout(this@LoginActivity)
                    Prefs.setLoginId(this@LoginActivity, id) // 내 정보 화면에 표시
                    startActivity(
                        Intent(this@LoginActivity, MainActivity::class.java)
                    )
                    finish()
                } else {
                    Toast.makeText(
                        this@LoginActivity,
                        "아이디 또는 비밀번호가 올바르지 않습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        // 회원가입 화면으로 이동
        binding.registerButton.setOnClickListener {
            startActivity(
                Intent(this, RegisterActivity::class.java)
            )
        }
    }
}
