package com.example.cheongyangbus

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cheongyangbus.data.AdminApi
import com.example.cheongyangbus.data.Route
import com.example.cheongyangbus.databinding.ActivityRouteManageBinding
import kotlinx.coroutines.launch

// 관리자: 노선(출발 터미널 → 도착 터미널) 목록·추가·삭제 (서버 MySQL route 테이블)
// 새 지역·터미널을 입력하면 서버가 함께 만들고, 시간표 관리에서 이 노선을 고를 수 있습니다.
// 삭제는 DB에서 지우지 않고 사용 중지만 합니다. 같은 노선을 다시 추가하면 다시 사용됩니다.
class RouteManageActivity : BaseActivity() {

    private lateinit var binding: ActivityRouteManageBinding
    private val routes = mutableListOf<Route>()
    private lateinit var adapter: RouteAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!requireAdmin()) return

        binding = ActivityRouteManageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prepareScreen(binding.root)

        adapter = RouteAdapter(routes, ::confirmDelete)
        binding.routeRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.routeRecyclerView.adapter = adapter

        binding.addRouteButton.setOnClickListener { showAddDialog() }
        binding.backButton.setOnClickListener { finish() }
        loadRoutes()
    }

    private fun loadRoutes() {
        lifecycleScope.launch {
            val loaded = adminCall { token -> AdminApi.routes(token) } ?: return@launch
            routes.clear()
            routes.addAll(loaded)
            adapter.notifyDataSetChanged()
            binding.emptyRouteText.visibility = if (routes.isEmpty()) View.VISIBLE else View.GONE
            binding.routeRecyclerView.visibility = if (routes.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun createInput(hintText: String) = EditText(this).apply {
        hint = hintText
        isSingleLine = true
    }

    private fun showAddDialog() {
        val originRegion = createInput("출발 지역 (예: 청양)")
        val originTerminal = createInput("출발 터미널 (없으면 비워 두기)")
        val destRegion = createInput("도착 지역 (예: 대전)")
        val destTerminal = createInput("도착 터미널 (예: 유성, 없으면 비워 두기)")
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 10, 40, 0)
            listOf(originRegion, originTerminal, destRegion, destTerminal).forEach(::addView)
        }

        AlertDialog.Builder(this)
            .setTitle("노선 추가")
            .setView(form)
            .setPositiveButton("추가", null)
            .setNegativeButton("취소", null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val origin = originRegion.text.toString().trim()
                        val dest = destRegion.text.toString().trim()
                        if (origin.isEmpty() || dest.isEmpty()) {
                            Toast.makeText(this@RouteManageActivity, "출발 지역과 도착 지역을 입력하세요.", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        lifecycleScope.launch {
                            val saved = adminCall { token ->
                                AdminApi.addRoute(
                                    token,
                                    origin, originTerminal.text.toString().trim(),
                                    dest, destTerminal.text.toString().trim()
                                )
                            }
                            // 서버가 거절하면(이미 있는 노선 등) 다이얼로그를 그대로 둠
                            if (saved != null) {
                                dismiss()
                                loadRoutes()
                                Toast.makeText(this@RouteManageActivity, "노선을 추가했습니다.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                show()
            }
    }

    private fun confirmDelete(route: Route) {
        AlertDialog.Builder(this)
            .setTitle("노선 삭제")
            .setMessage(
                "${route.label} 노선을 삭제할까요?\n" +
                    "이 노선의 시간표 ${route.scheduleCount}편이 버스 조회에서 빠지고, " +
                    "즐겨찾기한 사용자에게는 '운행 폐지'로 연하게 표시됩니다."
            )
            .setPositiveButton("삭제") { _, _ ->
                lifecycleScope.launch {
                    if (adminCall { token -> AdminApi.deleteRoute(token, route.id) } != null) {
                        loadRoutes()
                        Toast.makeText(this@RouteManageActivity, "노선을 삭제했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }
}

private class RouteAdapter(
    private val items: List<Route>,
    private val onDelete: (Route) -> Unit
) : RecyclerView.Adapter<RouteAdapter.Holder>() {

    class Holder(val root: LinearLayout) : RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): Holder {
        val root = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(24, 12, 12, 12)
            setBackgroundColor(android.graphics.Color.WHITE)
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12 }
        }
        return Holder(root)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val route = items[position]
        holder.root.removeAllViews()
        holder.root.addView(TextView(holder.root.context).apply {
            text = "${route.label}\n시간표 ${route.scheduleCount}편"
            textSize = 16f
            setTextColor(android.graphics.Color.rgb(25, 43, 32))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        holder.root.addView(android.widget.Button(holder.root.context).apply {
            text = "삭제"
            setOnClickListener { onDelete(route) }
        })
    }

    override fun getItemCount() = items.size
}
