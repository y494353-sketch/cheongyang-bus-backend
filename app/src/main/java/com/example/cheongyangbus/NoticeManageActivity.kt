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
import com.example.cheongyangbus.data.Notice
import com.example.cheongyangbus.data.NoticeRepository
import com.example.cheongyangbus.data.SeoulTime
import com.example.cheongyangbus.databinding.ActivityNoticeManageBinding
import kotlinx.coroutines.launch

class NoticeManageActivity : BaseActivity() {

    private lateinit var binding: ActivityNoticeManageBinding
    private lateinit var repository: NoticeRepository
    private val notices = mutableListOf<Notice>()
    private lateinit var adapter: NoticeAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!AccountStore.isLoggedIn(this) || !AccountStore.isAdmin(this)) {
            AccountStore.logout(this)
            startActivity(android.content.Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityNoticeManageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prepareScreen(binding.root)
        repository = NoticeRepository(this)
        adapter = NoticeAdapter(notices, ::editNotice, ::confirmDelete)
        binding.noticeRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.noticeRecyclerView.adapter = adapter
        binding.addNoticeButton.setOnClickListener { editNotice(null) }
        binding.backButton.setOnClickListener { finish() }
        loadNotices()
    }

    private fun loadNotices() {
        lifecycleScope.launch {
            try {
                notices.clear()
                notices.addAll(repository.all())
                adapter.notifyDataSetChanged()
                binding.emptyNoticeText.visibility = if (notices.isEmpty()) View.VISIBLE else View.GONE
                binding.noticeRecyclerView.visibility = if (notices.isEmpty()) View.GONE else View.VISIBLE
            } catch (_: Exception) {
                Toast.makeText(this@NoticeManageActivity, "공지사항을 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
            }
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
                        val notice = existing?.copy(title = titleText, content = contentText)
                            ?: Notice(title = titleText, content = contentText)
                        lifecycleScope.launch {
                            try {
                                repository.save(notice)
                                loadNotices()
                                Toast.makeText(this@NoticeManageActivity, "공지사항을 저장했습니다.", Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) {
                                Toast.makeText(this@NoticeManageActivity, "공지사항 저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
                            }
                        }
                        dismiss()
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
                    try {
                        repository.delete(notice.id)
                        loadNotices()
                        Toast.makeText(this@NoticeManageActivity, "공지사항을 삭제했습니다.", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {
                        Toast.makeText(this@NoticeManageActivity, "공지사항 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show()
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
            text = SeoulTime.formatter("yyyy.MM.dd HH:mm").format(notice.createdAt)
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
