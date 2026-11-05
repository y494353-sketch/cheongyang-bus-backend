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
import androidx.room.migration.Migration
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

    @Query("SELECT * FROM users ORDER BY id DESC")
    suspend fun getAllMembers(): List<User>

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteMember(id: Long): Int
}

/** 앱 전체에서 하나만 쓰는 Room DB (파일 이름: cheongyang_bus.db) */
@Database(
    entities = [User::class, Schedule::class, Favorite::class, BusAlarm::class, Notice::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao

    abstract fun scheduleDao(): ScheduleDao

    abstract fun favoriteDao(): FavoriteDao

    abstract fun alarmDao(): AlarmDao

    abstract fun noticeDao(): NoticeDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `notices` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `content` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
                )
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cheongyang_bus.db"
                )
                    .addMigrations(MIGRATION_3_4)
                    // 개발 중이라 테이블 구조가 바뀌면 DB를 새로 만듭니다 (이때 가입한 계정도 지워짐)
                    .fallbackToDestructiveMigration()
                    // 처음 비어 있는 DB일 때만 CSV 예시 시간표를 넣고, 이후 관리자 수정은 보존합니다.
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            fillSchedules(context.applicationContext, db)
                        }
                    })
                    .build().also { instance = it }
            }
    }
}
