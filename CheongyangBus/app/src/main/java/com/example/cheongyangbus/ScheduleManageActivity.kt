package com.example.cheongyangbus

import android.app.AlertDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cheongyangbus.data.AdminApi
import com.example.cheongyangbus.data.ManagedSchedule
import com.example.cheongyangbus.data.Route
import com.example.cheongyangbus.data.isValidTime
import com.example.cheongyangbus.databinding.ActivityScheduleManageBinding
import kotlinx.coroutines.launch

// 관리자: 버스 시간표 목록·추가·수정·삭제 (서버 MySQL schedule 테이블)
// 저장하면 일반 사용자의 버스 조회 결과에 바로 반영됩니다.
class ScheduleManageActivity : BaseActivity() {

    private lateinit var binding: ActivityScheduleManageBinding
    private val schedules = mutableListOf<ManagedSchedule>()
    private val routes = mutableListOf<Route>()
    private lateinit var adapter: ScheduleAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!requireAdmin()) return

        binding = ActivityScheduleManageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prepareScreen(binding.root)

        adapter = ScheduleAdapter(
            schedules,
            onEdit = ::showEditDialog,
            onDelete = ::confirmDelete
        )
        binding.scheduleRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.scheduleRecyclerView.adapter = adapter

        binding.addScheduleButton.setOnClickListener { showAddDialog() }
        binding.backButton.setOnClickListener { finish() }
    }

    // 노선 관리에서 돌아와도 새 노선이 보이도록 화면에 올 때마다 다시 불러옴
    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) loadSchedules()
    }

    private fun loadSchedules() {
        lifecycleScope.launch {
            val loaded = adminCall { token -> AdminApi.routes(token) to AdminApi.schedules(token) } ?: return@launch
            routes.clear()
            routes.addAll(loaded.first)
            schedules.clear()
            schedules.addAll(loaded.second)
            adapter.notifyDataSetChanged()
            binding.scheduleCountText.text = "전체 ${schedules.size}편"
        }
    }

    private fun createInput(hintText: String, value: String = "") = EditText(this).apply {
        hint = hintText
        setText(value)
        isSingleLine = true
    }

    // 노선 선택 목록 (노선은 '노선 관리'에서 추가)
    private fun createRouteSpinner(selectedRouteId: Int?) = Spinner(this).apply {
        adapter = ArrayAdapter(
            this@ScheduleManageActivity,
            android.R.layout.simple_spinner_dropdown_item,
            routes.map { it.label }
        )
        val index = routes.indexOfFirst { it.id == selectedRouteId }
        if (index >= 0) setSelection(index)
    }

    private fun createInputLayout(route: Spinner, departure: EditText, arrival: EditText) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(40, 10, 40, 0)
        addView(TextView(this@ScheduleManageActivity).apply { text = "노선" })
        addView(route)
        addView(departure)
        addView(arrival)
    }

    private fun showAddDialog() {
        if (routes.isEmpty()) {
            Toast.makeText(this, "먼저 노선 관리에서 노선을 추가하세요.", Toast.LENGTH_SHORT).show()
            return
        }
        val route = createRouteSpinner(null)
        val departure = createInput("출발 시간 (예: 09:00)")
        val arrival = createInput("도착 시간 (예: 10:20)")
        showScheduleDialog("시간표 추가", "추가", route, departure, arrival) { routeId, dep, arr ->
            adminCall { token -> AdminApi.addSchedule(token, routeId, dep, arr) } != null
        }
    }

    private fun showEditDialog(position: Int) {
        val original = schedules.getOrNull(position) ?: return
        val route = createRouteSpinner(original.routeId)
        val departure = createInput("출발 시간", original.departure)
        val arrival = createInput("도착 시간", original.arrival)
        showScheduleDialog("시간표 수정", "저장", route, departure, arrival) { routeId, dep, arr ->
            adminCall { token -> AdminApi.updateSchedule(token, original.id, routeId, dep, arr) } != null
        }
    }

    // 입력 검증에 실패하거나 서버가 거절하면 다이얼로그는 그대로 유지됩니다.
    private fun showScheduleDialog(
        title: String,
        action: String,
        route: Spinner,
        departure: EditText,
        arrival: EditText,
        onSave: suspend (routeId: Int, departure: String, arrival: String) -> Boolean
    ) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(createInputLayout(route, departure, arrival))
            .setPositiveButton(action, null)
            .setNegativeButton("취소", null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val routeId = routes.getOrNull(route.selectedItemPosition)?.id
                        val dep = departure.text.toString().trim()
                        val arr = arrival.text.toString().trim()
                        val error = checkInput(routeId, dep, arr)
                        if (error != null) {
                            Toast.makeText(this@ScheduleManageActivity, error, Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        lifecycleScope.launch {
                            if (onSave(routeId!!, dep, arr)) {
                                dismiss()
                                loadSchedules()
                                showSaved()
                            }
                        }
                    }
                }
                show()
            }
    }

    // 입력값 및 시간 형식 검증 (서버에서도 한 번 더 확인)
    private fun checkInput(routeId: Int?, departure: String, arrival: String): String? = when {
        routeId == null -> "노선을 선택하세요."
        !isValidTime(departure) || !isValidTime(arrival) -> "시간은 09:05 처럼 HH:mm 형식으로 입력하세요."
        departure == arrival -> "출발 시각과 도착 시각이 같습니다."
        else -> null
    }

    private fun confirmDelete(position: Int) {
        val schedule = schedules.getOrNull(position) ?: return
        AlertDialog.Builder(this)
            .setTitle("시간표 삭제")
            .setMessage("${schedule.routeLabel} ${schedule.departure} 편을 삭제할까요?\n이 편을 즐겨찾기한 기록도 함께 지워집니다.")
            .setPositiveButton("삭제") { _, _ ->
                lifecycleScope.launch {
                    if (adminCall { token -> AdminApi.deleteSchedule(token, schedule.id) } != null) {
                        loadSchedules()
                        Toast.makeText(this@ScheduleManageActivity, "시간표를 삭제했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showSaved() {
        Toast.makeText(this, "시간표가 저장되었습니다.", Toast.LENGTH_SHORT).show()
    }
}

private class ScheduleAdapter(
    private val schedules: List<ManagedSchedule>,
    private val onEdit: (Int) -> Unit,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<ScheduleAdapter.ScheduleViewHolder>() {

    class ScheduleViewHolder(val layout: LinearLayout) : RecyclerView.ViewHolder(layout)

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ScheduleViewHolder {
        val layout = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            setBackgroundColor(android.graphics.Color.WHITE)
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
        }
        return ScheduleViewHolder(layout)
    }

    override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
        val bus = schedules[position]
        holder.layout.removeAllViews()
        holder.layout.addView(TextView(holder.layout.context).apply {
            text = bus.routeLabel
            textSize = 18f
            setTextColor(android.graphics.Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        holder.layout.addView(TextView(holder.layout.context).apply {
            text = "출발 ${bus.departure}  |  도착 ${bus.arrival}  |  ${bus.duration}"
            textSize = 15f
            setTextColor(android.graphics.Color.DKGRAY)
            setPadding(0, 8, 0, 8)
        })
        val buttons = LinearLayout(holder.layout.context)
        val edit = android.widget.Button(holder.layout.context).apply {
            text = "수정"
            setOnClickListener {
                val current = holder.bindingAdapterPosition
                if (current != RecyclerView.NO_POSITION) onEdit(current)
            }
        }
        val delete = android.widget.Button(holder.layout.context).apply {
            text = "삭제"
            setOnClickListener {
                val current = holder.bindingAdapterPosition
                if (current != RecyclerView.NO_POSITION) onDelete(current)
            }
        }
        buttons.addView(edit)
        buttons.addView(delete)
        holder.layout.addView(buttons)
    }

    override fun getItemCount() = schedules.size
}
