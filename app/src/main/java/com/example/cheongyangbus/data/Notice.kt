package com.example.cheongyangbus.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update

@Entity(tableName = "notices")
data class Notice(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface NoticeDao {
    @Query("SELECT * FROM notices ORDER BY createdAt DESC, id DESC")
    suspend fun getAll(): List<Notice>

    @Insert
    suspend fun insert(notice: Notice): Long

    @Update
    suspend fun update(notice: Notice)

    @Query("DELETE FROM notices WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
