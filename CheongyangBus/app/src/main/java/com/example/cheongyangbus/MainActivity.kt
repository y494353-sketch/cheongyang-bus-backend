
package com.example.cheongyangbus

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.example.cheongyangbus.data.Api
import com.example.cheongyangbus.data.nextBusText
import com.example.cheongyangbus.databinding.ActivityMainBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private var countdown: Job? = null

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

        // 메인 화면 설정
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prepareScreen(binding.root)

        // 즐겨찾기
        binding.favoriteButton.setOnClickListener {
            startActivity(
                Intent(this, FavoriteActivity::class.java)
            )
        }

        // 공지사항
        binding.noticesButton.setOnClickListener {
            startActivity(
                Intent(this, NoticeActivity::class.java)
            )
        }

        // 마이페이지
        binding.myPageButton.setOnClickListener {
            startActivity(
                Intent(this, MyPageActivity::class.java)
            )
        }

        // 청양 도착 버스 검색
        binding.destinationCard.setOnClickListener {
            openSearch(BusSearchActivity.ARRIVE_AT_CHEONGYANG)
        }

        // 청양 출발 버스 검색
        binding.originCard.setOnClickListener {
            openSearch(BusSearchActivity.DEPART_FROM_CHEONGYANG)
        }
    }

    // 버스 검색 화면 이동
    private fun openSearch(searchMode: String) {
        startActivity(
            Intent(this, BusSearchActivity::class.java)
                .putExtra(
                    BusSearchActivity.EXTRA_SEARCH_MODE,
                    searchMode
                )
        )
    }

    // 최근 경로와 다음 버스 카운트다운 갱신
    override fun onResume() {
        super.onResume()

        if (!::binding.isInitialized) return

        val recent = AccountStore.recentSearch(this)

        binding.recentRoute.text =
            recent?.let { getString(R.string.route_format, it.first, it.second) }
                ?: getString(R.string.recent_empty)

        // 다음 버스 카운트다운: 최근 검색 노선의 다음 출발까지 남은 시간 (30초마다 갱신)
        countdown?.cancel()
        binding.nextBusText.visibility = if (recent == null) View.GONE else View.VISIBLE
        if (recent == null) return

        countdown = lifecycleScope.launch {
            val buses = try {
                Api.schedules(recent.first, recent.second)
            } catch (e: IOException) {
                binding.nextBusText.text = "다음 버스 정보를 불러오지 못했습니다."
                return@launch
            }

            while (isActive) {
                binding.nextBusText.text = nextBusText(buses)
                delay(30_000)
            }
        }
    }

    // 화면을 벗어나면 카운트다운 중지
    override fun onPause() {
        super.onPause()
        countdown?.cancel()
    }
}
