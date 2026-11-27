
package com.example.cheongyangbus

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.cheongyangbus.data.Api
import com.example.cheongyangbus.databinding.ActivityBusSearchBinding
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.io.IOException

class BusSearchActivity : BaseActivity() {

    companion object {
        const val EXTRA_SEARCH_MODE = "searchMode"
        const val DEPART_FROM_CHEONGYANG = "DEPART_FROM_CHEONGYANG"
        const val ARRIVE_AT_CHEONGYANG = "ARRIVE_AT_CHEONGYANG"
    }

    private lateinit var binding: ActivityBusSearchBinding

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

        binding = ActivityBusSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prepareScreen(binding.root, binding.toolbar)

        // 검색 방향 설정
        val departing =
            intent.getStringExtra(EXTRA_SEARCH_MODE) != ARRIVE_AT_CHEONGYANG

        val cheongyang = getString(R.string.cheongyang)

        // 지역 목록은 서버에서 받아옴 (아래 lifecycleScope)
        var regions = emptyList<String>()

        val fixedLayout =
            if (departing) binding.originLayout
            else binding.destinationLayout

        val fixedInput =
            if (departing) binding.originInput
            else binding.destinationInput

        val selectableInput =
            if (departing) binding.destinationInput
            else binding.originInput

        val savedKey =
            if (departing) "destination"
            else "origin"

        binding.searchHeading.setText(
            if (departing) R.string.search_intro
            else R.string.search_arrival_intro
        )

        binding.modeLabel.setText(
            if (departing) R.string.search_mode_to
            else R.string.search_mode_from
        )

        // 청양 고정 필드 설정
        fixedInput.setText(cheongyang, false)
        fixedLayout.endIconMode = TextInputLayout.END_ICON_CUSTOM
        fixedLayout.setEndIconDrawable(R.drawable.ic_lock)
        fixedLayout.setEndIconContentDescription(
            R.string.fixed_cheongyang
        )
        fixedLayout.setEndIconOnClickListener(null)
        fixedLayout.isEndIconCheckable = false

        fixedLayout.boxBackgroundColor =
            ContextCompat.getColor(this, R.color.green_soft)

        fixedLayout.isEnabled = false
        fixedInput.isEnabled = false
        fixedInput.isFocusable = false
        fixedInput.isClickable = false

        fixedInput.setTextColor(
            ContextCompat.getColor(this, R.color.muted)
        )

        fixedInput.contentDescription =
            getString(R.string.fixed_cheongyang)

        // 선택 가능한 지역 목록: 서버 시간표에 있는 지역만
        // (관리자가 노선·시간표를 추가하면 바로 나타남)
        binding.searchButton.isEnabled = false
        lifecycleScope.launch {
            regions = try {
                val list =
                    if (departing) Api.regions(departingFrom = cheongyang)
                    else Api.regions(arrivingAt = cheongyang)
                list.filter { it != cheongyang }
            } catch (e: IOException) {
                Snackbar.make(
                    binding.root,
                    "서버에 연결할 수 없습니다.",
                    Snackbar.LENGTH_LONG
                ).show()
                return@launch
            }

            if (regions.isEmpty()) {
                Snackbar.make(
                    binding.root,
                    "시간표에 선택할 수 있는 지역이 없습니다.",
                    Snackbar.LENGTH_LONG
                ).show()
                return@launch
            }

            selectableInput.setAdapter(
                ArrayAdapter(
                    this@BusSearchActivity,
                    android.R.layout.simple_dropdown_item_1line,
                    regions
                )
            )

            // 화면 회전 시 선택 지역 복원
            val selected = savedInstanceState
                ?.getString(savedKey)
                ?.takeIf { it in regions }
                ?: regions.first()

            selectableInput.setText(selected, false)
            binding.searchButton.isEnabled = true
        }

        // 검색 버튼
        binding.searchButton.setOnClickListener {
            val region = selectableInput.text.toString()

            if (region !in regions) {
                Snackbar.make(
                    binding.root,
                    R.string.select_region,
                    Snackbar.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            // 검색 방향 확정
            val origin =
                if (departing) cheongyang else region

            val destination =
                if (departing) region else cheongyang

            // 최근 경로 저장 (메인 화면 카운트다운에도 사용)
            AccountStore.setRecentSearch(this, origin, destination)

            // 버스 결과 화면 이동
            startActivity(
                Intent(this, BusResultActivity::class.java).apply {
                    putExtra("origin", origin)
                    putExtra("destination", destination)
                }
            )
        }
    }

    // 화면 회전 시 선택 지역 저장
    override fun onSaveInstanceState(outState: Bundle) {
        if (::binding.isInitialized) {
            outState.putString(
                "origin",
                binding.originInput.text.toString()
            )

            outState.putString(
                "destination",
                binding.destinationInput.text.toString()
            )
        }

        super.onSaveInstanceState(outState)
    }
}
