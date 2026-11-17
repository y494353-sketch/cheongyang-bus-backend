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
import com.example.cheongyangbus.data.Api
import com.example.cheongyangbus.data.Notice
import com.example.cheongyangbus.databinding.ActivityNoticeManageBinding
import kotlinx.coroutines.launch

// 관리자: 공지사항 등록·수정·삭제 (서버 MySQL notice 테이블, 일반 사용자 공지 화면과 같은 데이터)
class NoticeManageActivity : BaseActivity() {

    private lateinit var binding: ActivityNoticeManageBinding
    private val notices = mutableListOf<Notice>()
    private lateinit var adapter: NoticeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!requireAdmin()) return

        binding = ActivityNoticeManageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prepareScreen(binding.root)
        adapter = NoticeAdapter(notices, ::editNotice, ::confirmDelete)
        binding.noticeRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.noticeRecyclerView.adapter = adapter
        binding.addNoticeButton.setOnClickListener { editNotice(null) }
        binding.backButton.setOnClickListener { finish() }
        loadNotices()
    }

    private fun loadNotices() {
        lifecycleScope.launch {
            val loaded = adminCall { Api.notices() } ?: return@launch
            notices.clear()
            notices.addAll(loaded)
            adapter.notifyDataSetChanged()
            binding.emptyNoticeText.visibility = if (notices.isEmpty()) View.VISIBLE else View.GONE
            binding.noticeRecyclerView.visibility = if (notices.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun editNotice(existing: Notice?) {
        val title = EditText(this).apply {
            hint = "공지 제목"
            setText(existing?.title.orEmpty())
            isSingleLine = true
        }
        val content = EditText(this).apply {
            hint = "공지 내용"
            setText(existing?.content.orEmpty())
            minLines = 3
            gravity = android.view.Gravity.TOP
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 8, 48, 0)
            addView(title)
            addView(content)
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "공지사항 추가" else "공지사항 수정")
            .setView(form)
            .setPositiveButton(if (existing == null) "등록" else "저장", null)
            .setNegativeButton("취소", null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val titleText = title.text.toString().trim()
                        val contentText = content.text.toString().trim()
                        if (titleText.isBlank() || contentText.isBlank()) {
                            Toast.makeText(this@NoticeManageActivity, "제목과 내용을 입력하세요.", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        lifecycleScope.launch {
                            val saved = adminCall { token ->
                                if (existing == null) AdminApi.addNotice(token, titleText, contentText)
                                else AdminApi.updateNotice(token, existing.id, titleText, contentText)
                            }
                            // 저장에 실패하면 다이얼로그를 그대로 둠
                            if (saved != null) {
                                dismiss()
                                loadNotices()
                                Toast.makeText(this@NoticeManageActivity, "공지사항을 저장했습니다.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                show()
            }
    }

    private fun confirmDelete(notice: Notice) {
        AlertDialog.Builder(this)
            .setTitle("공지사항 삭제")
            .setMessage("'${notice.title}' 공지를 삭제할까요?")
            .setPositiveButton("삭제") { _, _ ->
                lifecycleScope.launch {
                    if (adminCall { token -> AdminApi.deleteNotice(token, notice.id) } != null) {
                        loadNotices()
                        Toast.makeText(this@NoticeManageActivity, "공지사항을 삭제했습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }
}

private class NoticeAdapter(
    private val items: List<Notice>,
    private val onEdit: (Notice) -> Unit,
    private val onDelete: (Notice) -> Unit
) : RecyclerView.Adapter<NoticeAdapter.Holder>() {

    class Holder(val root: LinearLayout) : RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): Holder {
        val root = LinearLayout(parent.context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 16)
            setBackgroundColor(android.graphics.Color.WHITE)
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16 }
        }
        return Holder(root)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val notice = items[position]
        holder.root.removeAllViews()
        holder.root.addView(TextView(holder.root.context).apply {
            text = notice.title
            textSize = 18f
            setTextColor(android.graphics.Color.rgb(25, 43, 32))
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        holder.root.addView(TextView(holder.root.context).apply {
            text = notice.content
            textSize = 15f
            setTextColor(android.graphics.Color.DKGRAY)
            setPadding(0, 8, 0, 4)
        })
        holder.root.addView(TextView(holder.root.context).apply {
            text = notice.createdAt
            textSize = 12f
            setTextColor(android.graphics.Color.GRAY)
        })
        val buttons = LinearLayout(holder.root.context)
        buttons.addView(android.widget.Button(holder.root.context).apply {
            text = "수정"
            setOnClickListener { onEdit(notice) }
        })
        buttons.addView(android.widget.Button(holder.root.context).apply {
            text = "삭제"
            setOnClickListener { onDelete(notice) }
        })
        holder.root.addView(buttons)
    }

    override fun getItemCount() = items.size
}
