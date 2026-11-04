package com.example.cheongyangbus

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cheongyangbus.data.FavoriteRepository
import com.example.cheongyangbus.databinding.ActivityFavoriteBinding
import kotlinx.coroutines.launch

class FavoriteActivity : BaseActivity() {
    private lateinit var binding: ActivityFavoriteBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFavoriteBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prepareScreen(binding.root, binding.toolbar)
        binding.favoriteList.layoutManager = LinearLayoutManager(this)
    }
    override fun onResume() {
        super.onResume()
        // 상세 화면에서 해제한 즐겨찾기도 즉시 반영합니다. (Room favorites 테이블에서 읽음)
        lifecycleScope.launch {
            val list = FavoriteRepository(this@FavoriteActivity).all()
            binding.favoriteList.adapter = BusTimeAdapter(list, true) {
                startActivity(it.detailIntent(this@FavoriteActivity))
            }
            binding.emptyMessage.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}
