package com.example.cheongyangbus.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/** users 테이블: 회원 한 명 = 한 줄 */
@Entity(tableName = "users", indices = [Index(value = ["loginId"], unique = true)])
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loginId: String,       // 아이디 (중복 불가)
    val passwordHash: String   // 비밀번호 원문 대신 해시를 저장
)

@Dao
interface UserDao {
    /** 같은 아이디가 있으면 SQLiteConstraintException 이 납니다. */
    @Insert
    suspend fun insert(user: User): Long

    @Query("SELECT * FROM users WHERE loginId = :loginId LIMIT 1")
    suspend fun findByLoginId(loginId: String): User?
}

/** 앱 전체에서 하나만 쓰는 Room DB (파일 이름: cheongyang_bus.db) */
@Database(
    entities = [User::class, Schedule::class, Favorite::class, BusAlarm::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao

    abstract fun scheduleDao(): ScheduleDao

    abstract fun favoriteDao(): FavoriteDao

    abstract fun alarmDao(): AlarmDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cheongyang_bus.db"
                )
                    // 개발 중이라 테이블 구조가 바뀌면 DB를 새로 만듭니다 (이때 가입한 계정도 지워짐)
                    .fallbackToDestructiveMigration()
                    // 앱을 켤 때마다 시간표를 assets/timetable.csv 내용으로 다시 채웁니다
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            fillSchedules(context.applicationContext, db)
                        }
                    })
                    .build().also { instance = it }
            }
    }
}
