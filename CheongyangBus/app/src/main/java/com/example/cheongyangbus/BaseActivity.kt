package com.example.cheongyangbus

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar

// 공통 처리만 모아 둡니다. 각 화면의 기능은 해당 Activity에 작성합니다.
open class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    CommunityAlerts.check(applicationContext)
                    delay(12_000)
                }
            }
        }
    }


    protected fun prepareScreen(root: View, toolbar: MaterialToolbar? = null) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, root).isAppearanceLightStatusBars = true
        WindowCompat.getInsetsController(window, root).isAppearanceLightNavigationBars = true
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
        toolbar?.setNavigationOnClickListener { finish() }
    }
}
