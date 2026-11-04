package com.example.cheongyangbus

import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cheongyangbus.data.ScheduleRepository
import com.example.cheongyangbus.databinding.ActivityBusResultBinding
import kotlinx.coroutines.launch

class BusResultActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityBusResultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prepareScreen(binding.root, binding.toolbar)
        val origin = intent.getStringExtra("origin") ?: "청양"
        val destination = intent.getStringExtra("destination") ?: "대전"
        binding.routeTitle.text = getString(R.string.route_format, origin, destination)
        binding.busList.layoutManager = LinearLayoutManager(this)
        // Room 시간표에서 조회
        lifecycleScope.launch {
            val buses = ScheduleRepository(this@BusResultActivity).schedule(origin, destination)
            binding.scheduleCount.text = getString(R.string.schedule_count, buses.size)
            if (buses.isEmpty()) {
                Toast.makeText(this@BusResultActivity, "시간표 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            }
            binding.busList.adapter = BusTimeAdapter(buses) {
                startActivity(it.detailIntent(this@BusResultActivity))
            }
        }
    }
}
