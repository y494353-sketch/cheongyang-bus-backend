package com.example.cheongyangbus

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cheongyangbus.data.AdminApi
import com.example.cheongyangbus.data.Member
import com.example.cheongyangbus.databinding.ActivityMemberManageBinding
import kotlinx.coroutines.launch

// 관리자: 회원 목록·삭제 (서버 MySQL users 테이블). 관리자 계정은 삭제할 수 없습니다.
class MemberManageActivity : BaseActivity() {

    private lateinit var binding: ActivityMemberManageBinding
    private val members = mutableListOf<Member>()
    private lateinit var adapter: MemberAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireAdmin()) return

        binding = ActivityMemberManageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prepareScreen(binding.root)
        adapter = MemberAdapter(members, ::confirmDelete)
        binding.memberRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.memberRecyclerView.adapter = adapter
        binding.backButton.setOnClickListener { finish() }
        loadMembers()
    }

    private fun loadMembers() {
        lifecycleScope.launch {
            val loaded = adminCall { token -> AdminApi.members(token) } ?: return@launch
            members.clear()
            members.addAll(loaded)
            adapter.notifyDataSetChanged()
            binding.emptyMemberText.visibility = if (members.isEmpty()) View.VISIBLE else View.GONE
            binding.memberRecyclerView.visibility = if (members.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun confirmDelete(member: Member) {
        AlertDialog.Builder(this)
            .setTitle("회원 삭제")
            .setMessage("'${member.loginId}' 회원을 삭제할까요? 즐겨찾기도 함께 지워지고, 이 회원은 다시 가입해야 합니다.")
            .setPositiveButton("삭제") { _, _ ->
                lifecycleScope.launch {
                    if (adminCall { token -> AdminApi.deleteMember(token, member.loginId) } != null) {
                        loadMembers()
                        Toast.makeText(this@MemberManageActivity, "회원을 삭제했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }
}

private class MemberAdapter(
    private val items: List<Member>,
    private val onDelete: (Member) -> Unit
) : RecyclerView.Adapter<MemberAdapter.Holder>() {

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
        val member = items[position]
        holder.root.removeAllViews()
        holder.root.addView(TextView(holder.root.context).apply {
            text = "${member.loginId}${if (member.isAdmin) " (관리자)" else ""}\n가입 ${member.createdAt}"
            textSize = 16f
            setTextColor(android.graphics.Color.rgb(25, 43, 32))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        // 관리자 계정은 삭제 버튼 없음
        if (!member.isAdmin) {
            holder.root.addView(android.widget.Button(holder.root.context).apply {
                text = "삭제"
                setOnClickListener { onDelete(member) }
            })
        }
    }

    override fun getItemCount() = items.size
}
