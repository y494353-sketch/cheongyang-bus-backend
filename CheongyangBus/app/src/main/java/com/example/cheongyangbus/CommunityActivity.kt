package com.example.cheongyangbus

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.example.cheongyangbus.data.Api
import com.example.cheongyangbus.data.ApiException
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class CommunityActivity : BaseActivity() {
    private enum class Category(val label: String) {
        QUESTION("질문"), GENERAL("일반"), INFO("정보")
    }

    private lateinit var token: String
    private lateinit var tabs: TabLayout
    private lateinit var posts: LinearLayout
    private var loading: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val session = AccountStore.token(this)
        if (!AccountStore.isLoggedIn(this) || session.isNullOrBlank()) {
            openLogin()
            return
        }
        token = session
        val root = FrameLayout(this).apply { setBackgroundColor(getColor(R.color.background)) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(column, FrameLayout.LayoutParams(-1, -1))
        val toolbar = MaterialToolbar(this).apply {
            setTitle(R.string.community)
            setNavigationIcon(R.drawable.ic_back)
            navigationContentDescription = "뒤로"
        }
        column.addView(toolbar)
        tabs = TabLayout(this).apply {
            addTab(newTab().setText("전체"))
            Category.entries.forEach { addTab(newTab().setText(it.label)) }
        }
        column.addView(tabs)
        posts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(88))
        }
        val scroll = ScrollView(this).apply { addView(posts) }
        column.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val write = MaterialButton(this).apply {
            text = "글쓰기"
            setOnClickListener { chooseCategory() }
        }
        root.addView(write, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply {
            marginEnd = dp(16)
            bottomMargin = dp(16)
        })
        setContentView(root)
        prepareScreen(root, toolbar)

        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = loadPosts()
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = loadPosts()
        })
    }

    override fun onResume() {
        super.onResume()
        if (::tabs.isInitialized) loadPosts()
    }

    private fun loadPosts() {
        loading?.cancel()
        val category = Category.entries.getOrNull(tabs.selectedTabPosition - 1)
        val query = category?.let { "?category=${it.name}" }.orEmpty()
        posts.removeAllViews()
        showText("글을 불러오는 중…")
        loading = lifecycleScope.launch {
            try {
                val items = JSONArray(Api.get(PATH + query, token))
                posts.removeAllViews()
                if (items.length() == 0) showText("아직 등록된 글이 없어요.")
                for (index in 0 until items.length()) {
                    val post = items.getJSONObject(index)
                    val tag = Category.valueOf(post.getString("category")).label
                    val title = post.getString("title")
                    val count = post.optInt("comment_count", -1)
                    posts.addView(MaterialButton(this@CommunityActivity, null,
                        com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                        text = if (count >= 0) "[$tag] $title  ·  💬 $count" else "[$tag] $title"
                        isAllCaps = false
                        gravity = Gravity.CENTER_VERTICAL or Gravity.START
                        setOnClickListener {
                            if (count >= 0) {
                                startActivity(CommunityPostActivity.intent(this@CommunityActivity, post.getLong("post_id")))
                            } else {
                                MaterialAlertDialogBuilder(this@CommunityActivity)
                                    .setTitle(title)
                                    .setMessage(post.optString("content") + "\n\n댓글은 서버 업데이트 후 사용할 수 있어요.")
                                    .setPositiveButton("확인", null)
                                    .show()
                            }
                        }
                    })
                }
            } catch (e: IOException) {
                if (handleExpired(e)) return@launch
                posts.removeAllViews()
                showText("글을 불러오지 못했어요.")
                posts.addView(MaterialButton(this@CommunityActivity).apply {
                    text = "다시 시도"
                    setOnClickListener { loadPosts() }
                })
            }
        }
    }

    private fun chooseCategory() {
        MaterialAlertDialogBuilder(this)
            .setTitle("말머리 선택")
            .setItems(Category.entries.map { it.label }.toTypedArray()) { _, index ->
                compose(Category.entries[index])
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun compose(category: Category) {
        val title = TextInputEditText(this).apply { hint = "제목"; inputType = android.text.InputType.TYPE_CLASS_TEXT; setSingleLine(true) }
        val content = TextInputEditText(this).apply {
            hint = "본문"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 4
            gravity = Gravity.TOP
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(8))
            addView(title)
            addView(content)
        }
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("글쓰기 · ${category.label}")
            .setView(ScrollView(this).apply { addView(form) })
            .setNegativeButton("취소", null)
            .setPositiveButton("등록", null)
            .show()
        val submit = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
        submit.setOnClickListener {
            val heading = title.text.toString().trim()
            val body = content.text.toString().trim()
            if (heading.length !in 1..120) {
                title.error = "제목은 1~120자로 입력해 주세요."
                return@setOnClickListener
            }
            if (body.length !in 1..5000) {
                content.error = "본문은 1~5000자로 입력해 주세요."
                return@setOnClickListener
            }
            submit.isEnabled = false
            dialog.setCancelable(false)
            dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).isEnabled = false
            lifecycleScope.launch {
                try {
                    val payload = JSONObject().put("category", category.name)
                        .put("title", heading).put("content", body)
                    Api.check(Api.request("POST", PATH, payload, token))
                    dialog.dismiss()
                    tabs.getTabAt(0)?.select()
                } catch (e: IOException) {
                    if (e is ApiException && e.code == 401) {
                        dialog.dismiss()
                        handleExpired(e)
                    } else {
                        content.error = (e as? ApiException)?.message ?: "글을 등록하지 못했어요. 다시 시도해 주세요."
                        submit.isEnabled = true
                        dialog.setCancelable(true)
                        dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).isEnabled = true
                    }
                }
            }
        }
    }

    private fun handleExpired(error: IOException): Boolean {
        if (error !is ApiException || error.code != 401) return false
        AccountStore.logout(this)
        openLogin()
        return true
    }

    private fun openLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun showText(value: String, size: Float = 15f) {
        posts.addView(TextView(this).apply {
            text = value
            textSize = size
            setTextColor(getColor(R.color.ink))
            setPadding(0, dp(8), 0, dp(8))
        })
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val PATH = "/api/community/posts"
    }
}
