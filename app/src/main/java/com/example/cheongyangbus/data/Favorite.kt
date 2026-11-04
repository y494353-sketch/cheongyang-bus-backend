package com.example.cheongyangbus.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.example.cheongyangbus.BusTime

/** favorites 테이블: 즐겨찾기한 버스 한 편 = 한 줄 */
@Entity(tableName = "favorites")
data class Favorite(
    @PrimaryKey val busKey: String, // BusTime.key (출발|도착|출발시각|도착시각)
    val origin: String,
    val destination: String,
    val departure: String,
    val arrival: String,
    val duration: String
)

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY departure")
    suspend fun all(): List<Favorite>

    @Query("SELECT COUNT(*) FROM favorites WHERE busKey = :busKey")
    suspend fun count(busKey: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: Favorite)

    @Query("DELETE FROM favorites WHERE busKey = :busKey")
    suspend fun delete(busKey: String)
}

/** 즐겨찾기 목록·저장·해제. 함수들은 lifecycleScope.launch { } 안에서 부릅니다. */
class FavoriteRepository(context: Context) {

    private val dao = AppDatabase.get(context).favoriteDao()

    suspend fun all(): List<BusTime> =
        dao.all().map { BusTime(it.origin, it.destination, it.departure, it.arrival, it.duration) }

    suspend fun isSaved(bus: BusTime): Boolean = dao.count(bus.key) > 0

    /** 저장돼 있으면 해제하고, 없으면 저장합니다. 저장된 상태가 되면 true */
    suspend fun toggle(bus: BusTime): Boolean {
        if (isSaved(bus)) {
            dao.delete(bus.key)
            return false
        }
        dao.insert(Favorite(bus.key, bus.origin, bus.destination, bus.departure, bus.arrival, bus.duration))
        return true
    }
}
