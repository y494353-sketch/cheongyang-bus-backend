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
import com.example.cheongyangbus.data.User
import com.example.cheongyangbus.data.UserRepository
import com.example.cheongyangbus.databinding.ActivityMemberManageBinding
import kotlinx.coroutines.launch

class MemberManageActivity : BaseActivity() {

    private lateinit var binding: ActivityMemberManageBinding
    private lateinit var repository: UserRepository
    private val members = mutableListOf<User>()
    private lateinit var adapter: MemberAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AccountStore.isLoggedIn(this) || !AccountStore.isAdmin(this)) {
            AccountStore.logout(this)
            startActivity(android.content.Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityMemberManageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prepareScreen(binding.root)
        repository = UserRepository(this)
        adapter = MemberAdapter(members, ::confirmDelete)
        binding.memberRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.memberRecyclerView.adapter = adapter
        binding.backButton.setOnClickListener { finish() }
        loadMembers()
    }

    private fun loadMembers() {
        lifecycleScope.launch {
            try {
                members.clear()
                members.addAll(repository.allMembers())
                adapter.notifyDataSetChanged()
                binding.emptyMemberText.visibility = if (members.isEmpty()) View.VISIBLE else View.GONE
                binding.memberRecyclerView.visibility = if (members.isEmpty()) View.GONE else View.VISIBLE
            } catch (_: Exception) {
                Toast.makeText(this@MemberManageActivity, "회원 목록을 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmDelete(member: User) {
        AlertDialog.Builder(this)
            .setTitle("회원 삭제")
            .setMessage("'${member.loginId}' 회원을 삭제할까요? 이 회원은 다시 가입해야 합니다.")
            .setPositiveButton("삭제") { _, _ ->
                lifecycleScope.launch {
                    try {
                        repository.deleteMember(member.id)
                        loadMembers()
                        Toast.makeText(this@MemberManageActivity, "회원을 삭제했습니다.", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {
                        Toast.makeText(this@MemberManageActivity, "회원 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }
}

private class MemberAdapter(
    private val items: List<User>,
    private val onDelete: (User) -> Unit
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
            text = "${member.loginId}\n회원 번호 ${member.id}"
            textSize = 16f
            setTextColor(android.graphics.Color.rgb(25, 43, 32))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        holder.root.addView(android.widget.Button(holder.root.context).apply {
            text = "삭제"
            setOnClickListener { onDelete(member) }
        })
    }

    override fun getItemCount() = items.size
}
