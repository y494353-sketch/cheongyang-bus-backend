package com.example.cheongyangbus

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat
import com.example.cheongyangbus.data.Prefs
import com.example.cheongyangbus.data.ScheduleRepository
import com.example.cheongyangbus.databinding.ActivityBusSearchBinding
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class BusSearchActivity : BaseActivity() {
    companion object {
        const val EXTRA_SEARCH_MODE = "searchMode"
        const val DEPART_FROM_CHEONGYANG = "DEPART_FROM_CHEONGYANG"
        const val ARRIVE_AT_CHEONGYANG = "ARRIVE_AT_CHEONGYANG"
    }

    private lateinit var binding: ActivityBusSearchBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBusSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prepareScreen(binding.root, binding.toolbar)

        // 모드가 누락되거나 잘못되면 청양 출발을 기본값으로 사용합니다.
        val departing = intent.getStringExtra(EXTRA_SEARCH_MODE) != ARRIVE_AT_CHEONGYANG
        val cheongyang = getString(R.string.cheongyang)
        var regions = emptyList<String>()
        val fixedLayout = if (departing) binding.originLayout else binding.destinationLayout
        val fixedInput = if (departing) binding.originInput else binding.destinationInput
        val selectableInput = if (departing) binding.destinationInput else binding.originInput
        val savedKey = if (departing) "destination" else "origin"

        binding.searchHeading.setText(if (departing) R.string.search_intro else R.string.search_arrival_intro)
        binding.modeLabel.setText(if (departing) R.string.search_mode_to else R.string.search_mode_from)

        // 고정 필드에는 드롭다운을 연결하지 않고 아이콘과 비활성화 스타일을 적용합니다.
        fixedInput.setText(cheongyang, false)
        fixedLayout.endIconMode = TextInputLayout.END_ICON_CUSTOM
        fixedLayout.setEndIconDrawable(R.drawable.ic_lock)
        fixedLayout.setEndIconContentDescription(R.string.fixed_cheongyang)
        fixedLayout.setEndIconOnClickListener(null)
        fixedLayout.isEndIconCheckable = false
        fixedLayout.boxBackgroundColor = ContextCompat.getColor(this, R.color.green_soft)
        fixedLayout.isEnabled = false
        fixedInput.isEnabled = false
        fixedInput.isFocusable = false
        fixedInput.isClickable = false
        fixedInput.setTextColor(ContextCompat.getColor(this, R.color.muted))
        fixedInput.contentDescription = getString(R.string.fixed_cheongyang)

        selectableInput.setText("", false)
        binding.searchButton.isEnabled = false

        binding.searchButton.setOnClickListener {
            val region = selectableInput.text.toString()
            if (region !in regions) {
                Snackbar.make(binding.root, R.string.select_region, Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // 화면의 고정 필드 값에 의존하지 않고 모드로 방향을 확정합니다.
            val origin = if (departing) cheongyang else region
            val destination = if (departing) region else cheongyang
            Prefs.setRecentRoute(this, getString(R.string.route_format, origin, destination))
            startActivity(Intent(this, BusResultActivity::class.java).apply {
                putExtra("origin", origin)
                putExtra("destination", destination)
            })
        }

        lifecycleScope.launch {
            try {
                regions = ScheduleRepository(this@BusSearchActivity).allRegions()
                    .filter { it.isNotBlank() && it != cheongyang }
                selectableInput.setAdapter(
                    ArrayAdapter(this@BusSearchActivity, android.R.layout.simple_dropdown_item_1line, regions)
                )
                if (regions.isEmpty()) {
                    Snackbar.make(binding.root, "시간표에 선택할 수 있는 지역이 없습니다.", Snackbar.LENGTH_LONG).show()
                    return@launch
                }
                // 화면 재생성 시에도 선택한 지역만 복원하고 청양 고정은 유지합니다.
                val selected = savedInstanceState?.getString(savedKey)?.takeIf { it in regions } ?: regions.first()
                selectableInput.setText(selected, false)
                binding.searchButton.isEnabled = true
            } catch (_: Exception) {
                Snackbar.make(binding.root, "지역 목록을 불러오지 못했습니다.", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("origin", binding.originInput.text.toString())
        outState.putString("destination", binding.destinationInput.text.toString())
        super.onSaveInstanceState(outState)
    }
}
