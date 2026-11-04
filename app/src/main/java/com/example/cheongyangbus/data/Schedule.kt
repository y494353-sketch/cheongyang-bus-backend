package com.example.cheongyangbus.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.sqlite.db.SupportSQLiteDatabase

/** schedules 테이블: 시외버스 한 편 = 한 줄 (내용은 assets/timetable.csv) */
@Entity(tableName = "schedules")
data class Schedule(
    @PrimaryKey val id: Int,
    val origin: String,          // 출발 지역 (청양, 대전, 공주, 천안, 서울)
    val originStop: String,      // 출발 터미널 (유성, 동대전 등). 없으면 ""
    val destination: String,     // 도착 지역
    val destinationStop: String, // 도착 터미널 (서대전, 강남 등). 없으면 ""
    val departure: String,       // 출발 "HH:mm"
    val arrival: String          // 도착 "HH:mm"
)

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules WHERE origin = :origin AND destination = :destination ORDER BY departure")
    suspend fun find(origin: String, destination: String): List<Schedule>
}

/** assets/timetable.csv 로 schedules 테이블을 다시 채웁니다. CSV를 고치면 다음 실행 때 반영됩니다. */
internal fun fillSchedules(context: Context, db: SupportSQLiteDatabase) {
    val rows = context.assets.open("timetable.csv").bufferedReader().readLines()
        .map { it.removePrefix("﻿").split(",") }
        .filter { it.size >= 7 && it[0].isNotBlank() && !it[0].startsWith("#") && it[0] != "id" }
    db.beginTransaction()
    try {
        db.execSQL("DELETE FROM schedules")
        for (c in rows) {
            db.execSQL(
                "INSERT INTO schedules (id, origin, originStop, destination, destinationStop, departure, arrival) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(c[0].trim().toInt(), c[1], c[2], c[3], c[4], c[5], c[6])
            )
        }
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
}
