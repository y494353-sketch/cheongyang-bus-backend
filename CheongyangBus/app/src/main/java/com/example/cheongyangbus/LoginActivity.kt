
package com.example.cheongyangbus

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.cheongyangbus.data.Api
import com.example.cheongyangbus.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch
import java.io.IOException

class LoginActivity : BaseActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prepareScreen(binding.root)

        // 이미 로그인한 상태라면 메인(관리자는 관리자) 화면으로 이동
        if (AccountStore.isLoggedIn(this)) {
            openMainScreen(AccountStore.isAdmin(this))
            return
        }

        // 회원가입 후 전달받은 아이디 자동 입력
        binding.userId.setText(
            intent.getStringExtra("registered_id").orEmpty()
        )

        // 로그인 버튼
        binding.loginButton.setOnClickListener {
            val id = binding.userId.text?.toString()
                ?.trim().orEmpty()

            val password = binding.password.text?.toString()
                .orEmpty()

            when {
                id.isEmpty() -> {
                    binding.userId.error = "아이디를 입력하세요."
                    binding.userId.requestFocus()
                }

                password.isEmpty() -> {
                    binding.password.error = "비밀번호를 입력하세요."
                    binding.password.requestFocus()
                }

                else -> login(id, password)
            }
        }

        // 회원가입 화면으로 이동
        binding.registerButton.setOnClickListener {
            startActivity(
                Intent(this, RegisterActivity::class.java)
            )
        }
    }

    // 서버(MySQL users)에서 아이디·비밀번호 확인. 관리자 계정(role = ADMIN)도 같은 화면에서 로그인
    private fun login(id: String, password: String) {
        binding.loginButton.isEnabled = false
        lifecycleScope.launch {
            val result = try {
                Api.login(id, password)
            } catch (e: IOException) {
                Toast.makeText(
                    this@LoginActivity,
                    "서버에 연결할 수 없습니다.",
                    Toast.LENGTH_SHORT
                ).show()
                binding.loginButton.isEnabled = true
                return@launch
            }
            binding.loginButton.isEnabled = true

            if (result == null) {
                Toast.makeText(
                    this@LoginActivity,
                    "아이디 또는 비밀번호가 올바르지 않습니다.",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            AccountStore.login(this@LoginActivity, id, result.isAdmin, result.token)

            Toast.makeText(
                this@LoginActivity,
                if (result.isAdmin) "관리자로 로그인되었습니다." else "로그인되었습니다.",
                Toast.LENGTH_SHORT
            ).show()

            openMainScreen(result.isAdmin)
        }
    }

    // 관리자는 관리자 대시보드, 일반 회원은 메인 화면
    private fun openMainScreen(isAdmin: Boolean) {
        val screen = if (isAdmin) AdminActivity::class.java else MainActivity::class.java
        val intent = Intent(this, screen).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        startActivity(intent)
        finish()
    }
}
