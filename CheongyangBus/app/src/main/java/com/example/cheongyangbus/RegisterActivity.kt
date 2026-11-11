
package com.example.cheongyangbus

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.cheongyangbus.data.Api
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.io.IOException

class RegisterActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_register)

        val root = findViewById<android.view.View>(
            android.R.id.content
        )
        prepareScreen(root)

        val userId = findViewById<TextInputEditText>(
            R.id.userId
        )

        val password = findViewById<TextInputEditText>(
            R.id.password
        )

        val passwordConfirm = findViewById<TextInputEditText>(
            R.id.passwordConfirm
        )

        val registerButton = findViewById<MaterialButton>(
            R.id.registerButton
        )

        registerButton.setOnClickListener {
            val id = userId.text?.toString()?.trim().orEmpty()
            val pw = password.text?.toString().orEmpty()
            val pwConfirm =
                passwordConfirm.text?.toString().orEmpty()

            when {
                id.isEmpty() -> {
                    userId.error = "아이디를 입력하세요."
                    userId.requestFocus()
                }

                pw.isEmpty() -> {
                    password.error = "비밀번호를 입력하세요."
                    password.requestFocus()
                }

                pwConfirm.isEmpty() -> {
                    passwordConfirm.error =
                        "비밀번호를 다시 입력하세요."
                    passwordConfirm.requestFocus()
                }

                pw != pwConfirm -> {
                    passwordConfirm.error =
                        "비밀번호가 일치하지 않습니다."
                    passwordConfirm.requestFocus()
                }

                else -> lifecycleScope.launch {
                    // 서버(MySQL users)에 저장
                    registerButton.isEnabled = false
                    val created = try {
                        Api.signup(id, pw)
                    } catch (e: IOException) {
                        Toast.makeText(
                            this@RegisterActivity,
                            "서버에 연결할 수 없습니다.",
                            Toast.LENGTH_SHORT
                        ).show()
                        registerButton.isEnabled = true
                        return@launch
                    }
                    registerButton.isEnabled = true

                    if (!created) {
                        userId.error = "이미 사용 중인 아이디입니다."
                        userId.requestFocus()
                        return@launch
                    }

                    Toast.makeText(
                        this@RegisterActivity,
                        "회원가입이 완료되었습니다.",
                        Toast.LENGTH_SHORT
                    ).show()

                    val intent = Intent(
                        this@RegisterActivity,
                        LoginActivity::class.java
                    )
                    intent.putExtra("registered_id", id)
                    startActivity(intent)
                    finish()
                }
            }
        }
    }
}
