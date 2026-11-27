
package com.example.cheongyangbus

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cheongyangbus.data.Api
import com.example.cheongyangbus.databinding.ActivityFavoriteBinding
import kotlinx.coroutines.launch
import java.io.IOException

class FavoriteActivity : BaseActivity() {

    private lateinit var binding: ActivityFavoriteBinding

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

        // 즐겨찾기 화면 설정
        binding = ActivityFavoriteBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prepareScreen(binding.root, binding.toolbar)

        binding.favoriteList.layoutManager =
            LinearLayoutManager(this)
    }

    override fun onResume() {
        super.onResume()

        // 상세 화면에서 해제한 즐겨찾기도 즉시 반영
        if (!::binding.isInitialized) return

        val loginId = AccountStore.loginId(this) ?: return

        // 서버(MySQL favorite)에서 내 즐겨찾기 조회
        lifecycleScope.launch {
            val favorites = try {
                Api.favorites(loginId)
            } catch (e: IOException) {
                Toast.makeText(
                    this@FavoriteActivity,
                    "서버에 연결할 수 없습니다.",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            binding.favoriteList.adapter = BusTimeAdapter(
                favorites,
                true
            ) {
                startActivity(it.detailIntent(this@FavoriteActivity))
            }

            // 즐겨찾기가 없으면 안내 메시지 표시
            binding.emptyMessage.visibility =
                if (favorites.isEmpty()) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
        }
    }
}
