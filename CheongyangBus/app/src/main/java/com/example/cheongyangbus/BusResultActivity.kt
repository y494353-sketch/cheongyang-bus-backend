
package com.example.cheongyangbus

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cheongyangbus.data.Api
import com.example.cheongyangbus.databinding.ActivityBusResultBinding
import kotlinx.coroutines.launch
import java.io.IOException

class BusResultActivity : BaseActivity() {

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

        // 버스 결과 화면 설정
        val binding = ActivityBusResultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prepareScreen(binding.root, binding.toolbar)

        // 출발지와 도착지 가져오기
        val origin = intent.getStringExtra("origin") ?: "청양"
        val destination = intent.getStringExtra("destination") ?: "대전"

        // 노선 제목 표시
        binding.routeTitle.text = getString(
            R.string.route_format,
            origin,
            destination
        )

        // 버스 시간 목록 설정
        binding.busList.layoutManager = LinearLayoutManager(this)

        // 서버(MySQL schedule)에서 시간표 조회
        lifecycleScope.launch {
            val buses = try {
                Api.schedules(origin, destination)
            } catch (e: IOException) {
                Toast.makeText(
                    this@BusResultActivity,
                    "서버에 연결할 수 없습니다.",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            binding.scheduleCount.text =
                getString(R.string.schedule_count, buses.size)

            binding.busList.adapter = BusTimeAdapter(buses) {
                startActivity(it.detailIntent(this@BusResultActivity))
            }
        }
    }
}
