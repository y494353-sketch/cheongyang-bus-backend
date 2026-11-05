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
import com.example.cheongyangbus.data.Notice
import com.example.cheongyangbus.data.NoticeRepository
import com.example.cheongyangbus.data.SeoulTime
import com.example.cheongyangbus.databinding.ActivityNoticeBinding
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

/** 공지사항을 읽기 전용으로 보여주는 일반 사용자 화면입니다. */
class NoticeActivity : BaseActivity() {
    private lateinit var binding: ActivityNoticeBinding
    private lateinit var repository: NoticeRepository
    private val notices = mutableListOf<Notice>()
    private lateinit var adapter: NoticeReadAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoticeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prepareScreen(binding.root, binding.toolbar)
        repository = NoticeRepository(this)
        adapter = NoticeReadAdapter(notices, ::showNotice)
        binding.noticeRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.noticeRecyclerView.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        if (::repository.isInitialized) loadNotices()
    }

    private fun loadNotices() {
        lifecycleScope.launch {
            try {
                notices.clear()
                notices.addAll(repository.all())
                adapter.notifyDataSetChanged()
                val empty = notices.isEmpty()
                binding.emptyNoticeText.visibility = if (empty) View.VISIBLE else View.GONE
                binding.noticeRecyclerView.visibility = if (empty) View.GONE else View.VISIBLE
            } catch (_: Exception) {
                Toast.makeText(this@NoticeActivity, R.string.notice_load_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showNotice(notice: Notice) {
        val registered = SeoulTime.formatter("yyyy.MM.dd HH:mm").format(notice.createdAt)
        AlertDialog.Builder(this)
            .setTitle(notice.title)
            .setMessage("${notice.content}\n\n${getString(R.string.notice_registered_at)}: $registered")
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}

private class NoticeReadAdapter(
    private val items: List<Notice>,
    private val onClick: (Notice) -> Unit
) : RecyclerView.Adapter<NoticeReadAdapter.Holder>() {

    class Holder(val card: MaterialCardView, val content: LinearLayout) : RecyclerView.ViewHolder(card)

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): Holder {
        val context = parent.context
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 18, 20, 18)
        }
        val card = MaterialCardView(context).apply {
            radius = 20f * resources.displayMetrics.density
            cardElevation = 0f
            strokeWidth = (resources.displayMetrics.density).toInt().coerceAtLeast(1)
            setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(context, R.color.white))
            setStrokeColor(androidx.core.content.ContextCompat.getColor(context, R.color.line))
            isClickable = true
            isFocusable = true
            foreground = androidx.core.content.ContextCompat.getDrawable(context, android.R.drawable.list_selector_background)
            addView(content)
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (12 * resources.displayMetrics.density).toInt() }
        }
        return Holder(card, content)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val notice = items[position]
        holder.content.removeAllViews()
        holder.content.addView(TextView(holder.content.context).apply {
            text = notice.title
            textSize = 18f
            setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.ink))
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        holder.content.addView(TextView(holder.content.context).apply {
            text = notice.content
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            textSize = 14f
            setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.muted))
            setPadding(0, 8, 0, 8)
        })
        holder.content.addView(TextView(holder.content.context).apply {
            text = SeoulTime.formatter("yyyy.MM.dd HH:mm").format(notice.createdAt)
            textSize = 12f
            setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.muted))
        })
        holder.card.setOnClickListener { onClick(notice) }
    }

    override fun getItemCount() = items.size
}
