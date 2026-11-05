package com.example.cheongyangbus

import android.app.AlertDialog
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cheongyangbus.data.Schedule
import com.example.cheongyangbus.data.ScheduleRepository
import com.example.cheongyangbus.databinding.ActivityScheduleManageBinding
import kotlinx.coroutines.launch

class ScheduleManageActivity : BaseActivity() {

    private lateinit var binding: ActivityScheduleManageBinding
    private val schedules = mutableListOf<Schedule>()
    private lateinit var adapter: ScheduleAdapter
    private lateinit var repository: ScheduleRepository

    private val exportCsvLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            try {
                val csv = schedulesToCsv(repository.allSchedules())
                val stream = contentResolver.openOutputStream(uri)
                    ?: error("Could not open the selected output file")
                stream.bufferedWriter(Charsets.UTF_8).use { it.write(csv) }
                Toast.makeText(this@ScheduleManageActivity, "시간표 CSV를 저장했습니다.", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(this@ScheduleManageActivity, "CSV 저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!AccountStore.isLoggedIn(this) || !AccountStore.isAdmin(this)) {
            AccountStore.logout(this)
            startActivity(android.content.Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityScheduleManageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prepareScreen(binding.root)
        repository = ScheduleRepository(this)

        adapter = ScheduleAdapter(
            schedules,
            onEdit = ::showEditDialog,
            onDelete = ::confirmDelete
        )
        binding.scheduleRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.scheduleRecyclerView.adapter = adapter

        binding.addScheduleButton.setOnClickListener { showAddDialog() }
        binding.exportCsvButton.setOnClickListener { exportCsvLauncher.launch("timetable.csv") }
        binding.backButton.setOnClickListener { finish() }
        loadSchedules()
    }

    private fun loadSchedules() {
        lifecycleScope.launch {
            try {
                schedules.clear()
                schedules.addAll(repository.allSchedules())
                adapter.notifyDataSetChanged()
            } catch (_: Exception) {
                Toast.makeText(this@ScheduleManageActivity, "시간표를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createInput(hintText: String, value: String = "") = EditText(this).apply {
        hint = hintText
        setText(value)
        isSingleLine = true
    }

    private fun createInputLayout(inputs: List<EditText>) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(40, 10, 40, 0)
        inputs.forEach(::addView)
    }

    private fun showAddDialog() {
        val origin = createInput("출발 지역")
        val destination = createInput("도착 지역")
        val departure = createInput("출발 시간 (예: 09:00)")
        val arrival = createInput("도착 시간 (예: 10:20)")
        showScheduleDialog("시간표 추가", "추가", listOf(origin, destination, departure, arrival)) {
            val originText = origin.text.toString().trim()
            val destinationText = destination.text.toString().trim()
            val departureText = departure.text.toString().trim()
            val arrivalText = arrival.text.toString().trim()
            if (!isValid(originText, destinationText, departureText, arrivalText)) {
                showInvalidInput()
                return@showScheduleDialog
            }
            lifecycleScope.launch {
                repository.save(
                    Schedule(
                        id = repository.nextId(),
                        origin = originText,
                        originStop = "",
                        destination = destinationText,
                        destinationStop = "",
                        departure = departureText,
                        arrival = arrivalText
                    )
                )
                loadSchedules()
                showSaved()
            }
        }
    }

    private fun showEditDialog(position: Int) {
        val original = schedules.getOrNull(position) ?: return
        val origin = createInput("출발 지역", original.origin)
        val destination = createInput("도착 지역", original.destination)
        val departure = createInput("출발 시간", original.departure)
        val arrival = createInput("도착 시간", original.arrival)
        showScheduleDialog("시간표 수정", "저장", listOf(origin, destination, departure, arrival)) {
            val originText = origin.text.toString().trim()
            val destinationText = destination.text.toString().trim()
            val departureText = departure.text.toString().trim()
            val arrivalText = arrival.text.toString().trim()
            if (!isValid(originText, destinationText, departureText, arrivalText)) {
                showInvalidInput()
                return@showScheduleDialog
            }
            lifecycleScope.launch {
                repository.save(
                    original.copy(
                        origin = originText,
                        originStop = if (originText == original.origin) original.originStop else "",
                        destination = destinationText,
                        destinationStop = if (destinationText == original.destination) original.destinationStop else "",
                        departure = departureText,
                        arrival = arrivalText
                    )
                )
                loadSchedules()
                showSaved()
            }
        }
    }

    private fun showScheduleDialog(
        title: String,
        action: String,
        inputs: List<EditText>,
        onSave: () -> Unit
    ) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(createInputLayout(inputs))
            .setPositiveButton(action, null)
            .setNegativeButton("취소", null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        onSave()
                        // 입력 검증에 실패하면 다이얼로그는 유지됩니다.
                        if (inputs.all { it.text.toString().isNotBlank() } &&
                            isValidTime(inputs[2].text.toString().trim()) &&
                            isValidTime(inputs[3].text.toString().trim())
                        ) dismiss()
                    }
                }
                show()
            }
    }

    private fun isValidTime(time: String) = Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(time)

    private fun isValid(origin: String, destination: String, departure: String, arrival: String) =
        origin.isNotBlank() && destination.isNotBlank() &&
            isValidTime(departure) && isValidTime(arrival)

    private fun showInvalidInput() {
        Toast.makeText(this, "출발지, 도착지와 올바른 시간을 입력하세요.", Toast.LENGTH_SHORT).show()
    }

    private fun confirmDelete(position: Int) {
        val schedule = schedules.getOrNull(position) ?: return
        AlertDialog.Builder(this)
            .setTitle("시간표 삭제")
            .setMessage("${schedule.origin} → ${schedule.destination} ${schedule.departure} 편을 삭제할까요?")
            .setPositiveButton("삭제") { _, _ ->
                lifecycleScope.launch {
                    repository.delete(schedule.id)
                    loadSchedules()
                    showSaved()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showSaved() {
        Toast.makeText(this, "시간표가 저장되었습니다.", Toast.LENGTH_SHORT).show()
    }
}

private fun schedulesToCsv(schedules: List<Schedule>): String = buildString {
    appendLine("id,origin,origin_stop,destination,destination_stop,departure,arrival,source")
    schedules.forEach { schedule ->
        appendLine(listOf(
            schedule.id.toString(), schedule.origin, schedule.originStop, schedule.destination,
            schedule.destinationStop, schedule.departure, schedule.arrival, ""
        ).joinToString(",") { it.toCsvCell() })
    }
}

private fun String.toCsvCell(): String =
    if (any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${replace("\"", "\"\"")}\"" else this

private class ScheduleAdapter(
    private val schedules: List<Schedule>,
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
            text = "${displayLabel(bus.origin, bus.originStop)} → ${displayLabel(bus.destination, bus.destinationStop)}"
            textSize = 18f
            setTextColor(android.graphics.Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        holder.layout.addView(TextView(holder.layout.context).apply {
            text = "출발 ${bus.departure}  |  도착 ${bus.arrival}"
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

    private fun displayLabel(region: String, stop: String) = if (stop.isBlank()) region else "$region($stop)"
}
