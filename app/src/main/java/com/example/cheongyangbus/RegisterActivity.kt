
package com.example.cheongyangbus

import android.os.Bundle
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import androidx.lifecycle.lifecycleScope
import com.example.cheongyangbus.data.UserRepository
import kotlinx.coroutines.launch

class RegisterActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 회원가입 XML 화면 불러오기
        setContentView(R.layout.activity_register)

        // 기존 BaseActivity 화면 설정 유지
        val root = findViewById<android.view.View>(
            android.R.id.content
        )
        prepareScreen(root)

        // 입력창 연결
        val userId = findViewById<TextInputEditText>(
            R.id.userId
        )

        val password = findViewById<TextInputEditText>(
            R.id.password
        )

        val passwordConfirm = findViewById<TextInputEditText>(
            R.id.passwordConfirm
        )

        // 회원가입 버튼 연결
        val registerButton = findViewById<MaterialButton>(
            R.id.registerButton
        )

        // 회원가입 버튼 클릭
        registerButton.setOnClickListener {

            val id = userId.text.toString().trim()
            val pw = password.text.toString()
            val pwConfirm = passwordConfirm.text.toString()

            when {

                // 아이디 미입력
                id.isEmpty() -> {
                    userId.error = "아이디를 입력하세요."
                    userId.requestFocus()
                }

                // 비밀번호 미입력
                pw.isEmpty() -> {
                    password.error = "비밀번호를 입력하세요."
                    password.requestFocus()
                }

                // 비밀번호 확인 미입력
                pwConfirm.isEmpty() -> {
                    passwordConfirm.error =
                        "비밀번호를 다시 입력하세요."
                    passwordConfirm.requestFocus()
                }

                // 비밀번호 불일치
                pw != pwConfirm -> {
                    passwordConfirm.error =
                        "비밀번호가 일치하지 않습니다."
                    passwordConfirm.requestFocus()
                }

                // 모든 입력 정상
                else -> {
                    lifecycleScope.launch {
                        val created = UserRepository(this@RegisterActivity).signup(id, pw)
                        if (created) {
                            Toast.makeText(this@RegisterActivity, "회원가입 완료", Toast.LENGTH_SHORT).show()
                            finish()
                        } else {
                            userId.error = "이미 사용 중인 아이디입니다."
                            userId.requestFocus()
                        }
                    }
                }
            }
        }
    }
}

