
package com.example.cheongyangbus

import android.content.Intent
import android.os.Bundle
import com.example.cheongyangbus.databinding.ActivityMainBinding

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

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

    // 최근 경로 갱신
    override fun onResume() {
        super.onResume()

        if (::binding.isInitialized) {
            binding.recentRoute.text =
                AccountStore.recentRoute(this)
                    ?: getString(R.string.recent_empty)
        }
    }
}
