package com.example.cheongyangbus

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.cheongyangbus.data.Api
import com.example.cheongyangbus.data.ApiException
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import android.graphics.drawable.GradientDrawable
import android.view.inputmethod.InputMethodManager
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException

class CommunityPostActivity : BaseActivity() {
    private lateinit var token: String
    private lateinit var rows: LinearLayout
    private lateinit var scroll: ScrollView
    private var replyTo: Long? = null
    private val path get() = "/api/community/posts/${intent.getLongExtra(POST_ID, 0)}"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        token = AccountStore.token(this) ?: run { finish(); return }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.background))
        }
        val toolbar = MaterialToolbar(this).apply {
            title = "게시글"
            setNavigationIcon(R.drawable.ic_back)
            navigationContentDescription = "뒤로"
        }
        root.addView(toolbar)
        rows = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(16))
        }
        scroll = ScrollView(this).apply { addView(rows) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        prepareScreen(root, toolbar)
        load()
    }

    private fun load() {
        replyTo = null
        rows.removeAllViews()
        line("불러오는 중…")
        lifecycleScope.launch {
            try {
                val result = JSONObject(Api.get(path, token))
                val post = result.getJSONObject("post")
                val category = when (post.getString("category")) {
                    "QUESTION" -> "질문"
                    "GENERAL" -> "일반"
                    else -> "정보"
                }
                rows.removeAllViews()
                val body = LinearLayout(this@CommunityPostActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    minimumHeight = dp((resources.configuration.screenHeightDp * 0.38f).toInt())
                    setPadding(0, 0, 0, dp(20))
                }
                rows.addView(body)
                line("[$category] ${post.getString("title")}", 22f, container = body)
                line(post.getString("content"), container = body)

                val comments = result.getJSONArray("comments")
                val section = LinearLayout(this@CommunityPostActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), dp(8), dp(16), dp(12))
                    background = GradientDrawable().apply {
                        setColor(android.graphics.Color.WHITE)
                        cornerRadius = dp(16).toFloat()
                        setStroke(dp(1), getColor(R.color.green))
                    }
                }
                rows.addView(section, LinearLayout.LayoutParams(-1, -2))
                line("댓글 · ${comments.length()}개", 19f, container = section).apply {
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }
                val focusComposer = addComposer(section)
                if (comments.length() == 0) line("첫 댓글을 남겨보세요.", 14f, container = section)
                val children = (0 until comments.length()).map(comments::getJSONObject)
                    .groupBy { if (it.isNull("parent_id")) null else it.getLong("parent_id") }
                val pending = ArrayDeque<Pair<JSONObject, Int>>()
                children[null].orEmpty().asReversed().forEach { pending.addLast(it to 0) }
                while (pending.isNotEmpty()) {
                    val (comment, depth) = pending.removeLast()
                    val id = comment.getLong("comment_id")
                    val indent = depth.coerceAtMost(3) * 14
                    line((if (depth > 0) "↳ " else "") + comment.getString("author_id"), 14f, indent, section)
                    line(comment.getString("content"), 16f, indent, section)
                    section.addView(MaterialButton(this@CommunityPostActivity, null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                        text = "답글"
                        setOnClickListener { focusComposer(id, comment.getString("author_id")) }
                    }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(indent) })
                    children[id].orEmpty().asReversed().forEach { pending.addLast(it to depth + 1) }
                }

            } catch (error: IOException) {
                if (expired(error)) return@launch
                rows.removeAllViews()
                line("게시글을 불러오지 못했어요.")
                rows.addView(MaterialButton(this@CommunityPostActivity).apply {
                    text = "다시 시도"
                    setOnClickListener { load() }
                })
            }
        }
    }

    private fun addComposer(section: LinearLayout): (Long, String) -> Unit {
        val field = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = "댓글을 입력하세요"
        }
        val input = TextInputEditText(field.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
            maxLines = 5
            gravity = Gravity.TOP
        }
        field.addView(input)
        section.addView(field, LinearLayout.LayoutParams(-1, -2))
        val actions = LinearLayout(this).apply { gravity = Gravity.END }
        section.addView(actions, LinearLayout.LayoutParams(-1, -2))
        val cancel = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "답글 취소"
            visibility = android.view.View.GONE
        }
        val submit = MaterialButton(this).apply { text = "등록"; isEnabled = false }
        actions.addView(cancel)
        actions.addView(submit)
        cancel.setOnClickListener {
            replyTo = null
            field.hint = "댓글을 입력하세요"
            cancel.visibility = android.view.View.GONE
        }
        input.doAfterTextChanged {
            field.error = null
            submit.isEnabled = !it.isNullOrBlank()
        }
        submit.setOnClickListener {
            val content = input.text.toString().trim()
            if (content.length !in 1..2000) {
                field.error = "댓글은 1~2000자로 입력해 주세요."
                return@setOnClickListener
            }
            val parent = replyTo
            submit.isEnabled = false
            cancel.isEnabled = false
            field.isEnabled = false
            lifecycleScope.launch {
                try {
                    val body = JSONObject().put("content", content)
                    if (parent != null) body.put("parentId", parent)
                    Api.check(Api.request("POST", "$path/comments", body, token))
                    getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(input.windowToken, 0)
                    load()
                } catch (error: IOException) {
                    if (expired(error)) return@launch
                    field.isEnabled = true
                    field.error = (error as? ApiException)?.message ?: "댓글을 등록하지 못했어요."
                    cancel.isEnabled = true
                    submit.isEnabled = true
                }
            }
        }
        return { parent, author ->
            if (field.isEnabled) {
                replyTo = parent
                field.hint = "$author 님에게 답글"
                cancel.visibility = android.view.View.VISIBLE
                input.requestFocus()
                input.post { getSystemService(InputMethodManager::class.java).showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }
            }
        }
    }
    private fun expired(error: IOException): Boolean {
        if (error !is ApiException || error.code != 401) return false
        AccountStore.logout(this)
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
        return true
    }

    private fun line(value: String, size: Float = 16f, indent: Int = 0, container: LinearLayout = rows): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(getColor(R.color.ink))
            setPaddingRelative(dp(indent), dp(8), 0, dp(8))
        }.also { container.addView(it) }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        private const val POST_ID = "community_post_id"
        fun intent(context: Context, postId: Long) =
            Intent(context, CommunityPostActivity::class.java).putExtra(POST_ID, postId)
    }
}