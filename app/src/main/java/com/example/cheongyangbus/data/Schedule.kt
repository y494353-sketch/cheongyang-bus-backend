package com.example.cheongyangbus.data

import android.content.Context
import android.util.Log
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Insert
import androidx.room.OnConflictStrategy
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

    @Query("SELECT * FROM schedules ORDER BY origin, destination, departure")
    suspend fun all(): List<Schedule>

    @Query("SELECT region FROM (SELECT origin AS region FROM schedules UNION SELECT destination AS region FROM schedules) ORDER BY region")
    suspend fun allRegions(): List<String>

    @Query("SELECT COALESCE(MAX(id), 0) + 1 FROM schedules")
    suspend fun nextId(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(schedule: Schedule)

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun delete(id: Int)
}

/**
 * Bundled timetable updates only the schedules table. Parse and validate the entire CSV before
 * opening a transaction, then record the version only after the transaction commits.
 */
internal fun fillSchedules(context: Context, db: SupportSQLiteDatabase) {
    val prefs = context.getSharedPreferences("cheongyang_bus_schedule_seed", Context.MODE_PRIVATE)
    val bundledVersion = runCatching {
        context.assets.open("timetable_version.txt").bufferedReader().use { it.readText().trim().toInt() }
    }.getOrElse {
        Log.e("TimetableImport", "Missing or invalid timetable_version.txt", it)
        return
    }
    if (bundledVersion < 1) return

    val existingRows = db.query("SELECT COUNT(*) FROM schedules").use { cursor ->
        if (cursor.moveToFirst()) cursor.getLong(0) else 0L
    }
    // Existing installs predate the version key. Treat their already-seeded schedule as v1 so
    // shipping the current v1 asset does not erase local admin edits on the first app update.
    val appliedVersion = if (prefs.contains(KEY_APPLIED_VERSION)) {
        prefs.getInt(KEY_APPLIED_VERSION, 0)
    } else if (existingRows > 0) {
        1
    } else {
        0
    }
    if (bundledVersion <= appliedVersion && existingRows > 0) {
        if (!prefs.contains(KEY_APPLIED_VERSION)) {
            prefs.edit().putInt(KEY_APPLIED_VERSION, appliedVersion).putBoolean("csv_seeded", true).commit()
        }
        return
    }

    val schedules = try {
        context.assets.open("timetable.csv").bufferedReader(Charsets.UTF_8).use { parseTimetableCsv(it.readText()) }
    } catch (error: Exception) {
        Log.e("TimetableImport", "Bundled timetable CSV validation failed; keeping current schedules", error)
        return
    }

    try {
        db.beginTransaction()
        try {
            db.delete("schedules", null, null)
            schedules.forEach { schedule ->
                db.execSQL(
                    "INSERT INTO schedules (id, origin, originStop, destination, destinationStop, departure, arrival) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any>(schedule.id, schedule.origin, schedule.originStop, schedule.destination,
                        schedule.destinationStop, schedule.departure, schedule.arrival)
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (!prefs.edit().putInt(KEY_APPLIED_VERSION, bundledVersion).putBoolean("csv_seeded", true).commit()) {
            Log.e("TimetableImport", "Schedules updated but version preference could not be persisted")
        }
    } catch (error: Exception) {
        Log.e("TimetableImport", "Could not apply bundled timetable; current transaction rolled back", error)
    }
}

private const val KEY_APPLIED_VERSION = "applied_version"

private fun parseTimetableCsv(csv: String): List<Schedule> {
    val rows = parseCsvRecords(csv.removePrefix("\uFEFF"))
        .filter { row -> row.isNotEmpty() && row.any(String::isNotBlank) && !row[0].trimStart().startsWith("#") }
    require(rows.isNotEmpty()) { "CSV is empty" }
    val header = rows.first().map { it.trim().lowercase() }
    val requiredHeader = listOf("id", "origin", "origin_stop", "destination", "destination_stop", "departure", "arrival")
    require(header.take(requiredHeader.size) == requiredHeader) { "Unexpected timetable CSV header" }

    val schedules = rows.drop(1).map { columns ->
        require(columns.size >= 7) { "Each timetable row must have at least 7 columns" }
        val id = columns[0].trim().toIntOrNull() ?: error("Invalid schedule id")
        val origin = columns[1].trim()
        val originStop = columns[2].trim()
        val destination = columns[3].trim()
        val destinationStop = columns[4].trim()
        val departure = columns[5].trim()
        val arrival = columns[6].trim()
        require(id > 0 && origin.isNotBlank() && destination.isNotBlank()) { "Invalid route values" }
        require(TIME_PATTERN.matches(departure) && TIME_PATTERN.matches(arrival)) { "Invalid schedule time" }
        Schedule(id, origin, originStop, destination, destinationStop, departure, arrival)
    }
    require(schedules.isNotEmpty()) { "CSV contains no timetable rows" }
    require(schedules.map { it.id }.distinct().size == schedules.size) { "Duplicate schedule id" }
    return schedules
}

private val TIME_PATTERN = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

/** RFC 4180-style parser: commas and escaped quotes inside quoted cells are retained. */
private fun parseCsvRecords(text: String): List<List<String>> {
    val records = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val cell = StringBuilder()
    var quoted = false
    var index = 0
    while (index < text.length) {
        val char = text[index]
        when {
            quoted && char == '"' && index + 1 < text.length && text[index + 1] == '"' -> {
                cell.append('"'); index++
            }
            char == '"' -> quoted = !quoted
            char == ',' && !quoted -> { row.add(cell.toString()); cell.setLength(0) }
            (char == '\n' || char == '\r') && !quoted -> {
                row.add(cell.toString()); cell.setLength(0)
                if (row.any(String::isNotBlank)) records.add(row)
                row = mutableListOf()
                if (char == '\r' && index + 1 < text.length && text[index + 1] == '\n') index++
            }
            else -> cell.append(char)
        }
        index++
    }
    require(!quoted) { "Unclosed quoted CSV value" }
    if (cell.isNotEmpty() || row.isNotEmpty()) {
        row.add(cell.toString())
        if (row.any(String::isNotBlank)) records.add(row)
    }
    return records
}
