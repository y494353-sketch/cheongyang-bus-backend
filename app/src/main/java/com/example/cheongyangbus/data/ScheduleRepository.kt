package com.example.cheongyangbus.data

import android.content.Context
import com.example.cheongyangbus.BusTime
import java.util.Calendar

/** 시간표 조회. schedule() 은 lifecycleScope.launch { } 안에서 부릅니다. */
class ScheduleRepository(context: Context) {

    private val dao = AppDatabase.get(context).scheduleDao()

    /** 출발 지역 → 도착 지역 시간표 (출발 시각 순) */
    suspend fun schedule(origin: String, destination: String): List<BusTime> {
        val nowMin = nowMinutes()
        return dao.find(origin, destination).map { toBusTime(it, nowMin) }
    }

    /** 관리자 시간표 편집 화면용 전체 시간표 */
    suspend fun allSchedules(): List<Schedule> = dao.all()

    /** Room 시간표에서 현재 사용 가능한 출발·도착 지역 목록 */
    suspend fun allRegions(): List<String> = dao.allRegions()

    /** Room 시간표를 추가하거나 수정합니다. */
    suspend fun save(schedule: Schedule) = dao.save(schedule)

    /** Room 시간표를 삭제합니다. */
    suspend fun delete(id: Int) = dao.delete(id)

    suspend fun nextId(): Int = dao.nextId()
}

/** 지금 시각을 분으로 (0시 0분 = 0) */
fun nowMinutes(): Int {
    val now = SeoulTime.calendar()
    return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
}

/** 출발·도착 시각과 지금 시각으로 운행예정 / 운행중 / 운행종료 를 정합니다. */
fun currentStatus(departure: String, arrival: String, nowMin: Int = nowMinutes()): String {
    val dep = minutes(departure)
    val arr = arrivalMinutes(dep, arrival)
    return when {
        nowMin < dep -> "운행예정"
        nowMin < arr -> "운행중"
        else -> "운행종료"
    }
}

/** DB 한 줄을 화면용 BusTime 으로 바꿉니다. 소요시간과 운행 상태는 시각으로 계산합니다. */
internal fun toBusTime(s: Schedule, nowMin: Int): BusTime {
    val dep = minutes(s.departure)
    val arr = arrivalMinutes(dep, s.arrival)
    return BusTime(
        origin = label(s.origin, s.originStop),
        destination = label(s.destination, s.destinationStop),
        departure = s.departure,
        arrival = s.arrival,
        duration = durationText(arr - dep),
        status = currentStatus(s.departure, s.arrival, nowMin)
    )
}

private fun arrivalMinutes(dep: Int, arrival: String) =
    minutes(arrival).let { if (it < dep) it + 24 * 60 else it } // 자정을 넘기는 편

private fun label(region: String, stop: String) = if (stop.isEmpty()) region else "$region($stop)"

private fun minutes(hhmm: String) = hhmm.substring(0, 2).toInt() * 60 + hhmm.substring(3, 5).toInt()

private fun durationText(min: Int): String {
    val h = min / 60
    val m = min % 60
    return when {
        h == 0 -> "${m}분"
        m == 0 -> "${h}시간"
        else -> "${h}시간 ${m}분"
    }
}
