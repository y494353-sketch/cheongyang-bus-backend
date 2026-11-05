package com.example.cheongyangbus.data

import android.content.Context

class NoticeRepository(context: Context) {
    private val dao = AppDatabase.get(context).noticeDao()

    suspend fun all(): List<Notice> = dao.getAll()

    suspend fun save(notice: Notice) {
        if (notice.id == 0L) dao.insert(notice) else dao.update(notice)
    }

    suspend fun delete(id: Long) {
        dao.deleteById(id)
    }
}
