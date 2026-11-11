package com.example.cheongyangbus.data

import com.example.cheongyangbus.BusTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar

/**
 * 서버(server/ 폴더의 Spring Boot) 호출. 서버가 MySQL 을 읽고 씁니다.
 * 모든 함수는 lifecycleScope.launch { } 안에서 부르고,
 * 서버에 연결이 안 되면 IOException 을 던집니다.
 */
object Api {

    // 서버 주소. 학교/팀 서버 주소로 바꿔 주세요. (에뮬레이터에서 내 PC 서버는 http://10.0.2.2:8080)
    const val BASE_URL = "http://10.0.2.2:8080"

    /** 회원가입. true = 가입됨, false = 이미 있는 아이디 */
    suspend fun signup(loginId: String, password: String): Boolean {
        val (code, _) = request("POST", "/api/auth/signup", account(loginId, password))
        return when (code) {
            201 -> true
            409 -> false
            else -> throw IOException("signup $code")
        }
    }

    /** 로그인. true = 아이디·비밀번호 일치 */
    suspend fun login(loginId: String, password: String): Boolean {
        val (code, _) = request("POST", "/api/auth/login", account(loginId, password))
        return when (code) {
            200 -> true
            401 -> false
            else -> throw IOException("login $code")
        }
    }

    /** 출발 지역 → 도착 지역 시간표 (출발 시각 순) */
    suspend fun schedules(origin: String, destination: String): List<BusTime> =
        busList(get("/api/schedules?origin=${enc(origin)}&dest=${enc(destination)}"))

    /** 내 즐겨찾기 목록 */
    suspend fun favorites(loginId: String): List<BusTime> =
        busList(get("/api/favorites?loginId=${enc(loginId)}"))

    suspend fun addFavorite(loginId: String, scheduleId: Int) {
        val body = JSONObject().put("loginId", loginId).put("scheduleId", scheduleId)
        check(request("POST", "/api/favorites", body))
    }

    suspend fun removeFavorite(loginId: String, scheduleId: Int) {
        check(request("DELETE", "/api/favorites?loginId=${enc(loginId)}&scheduleId=$scheduleId"))
    }

    // ------------------------------------------------------------------ 내부

    private fun account(loginId: String, password: String) =
        JSONObject().put("loginId", loginId).put("password", password)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private suspend fun get(path: String): String {
        val response = request("GET", path)
        check(response)
        return response.second
    }

    private fun check(response: Pair<Int, String>) {
        if (response.first !in 200..299) throw IOException("HTTP ${response.first}")
    }

    /** HTTP 요청 → (응답 코드, 응답 본문) */
    private suspend fun request(method: String, path: String, body: JSONObject? = null): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val conn = URL(BASE_URL + path).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                val stream = if (code < 400) conn.inputStream else conn.errorStream
                code to (stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "")
            } finally {
                conn.disconnect()
            }
        }
}

/** 서버가 보낸 시간표 JSON 배열 → 화면용 BusTime 목록 */
internal fun busList(json: String): List<BusTime> {
    val array = JSONArray(json)
    return List(array.length()) { i ->
        val o = array.getJSONObject(i)
        val departure = o.getString("departure")
        val arrival = o.getString("arrival")
        BusTime(
            origin = label(o.getString("originRegion"), o.getString("originTerminal")),
            destination = label(o.getString("destRegion"), o.getString("destTerminal")),
            departure = departure,
            arrival = arrival,
            duration = o.getString("durationText"),
            status = currentStatus(departure, arrival),
            scheduleId = o.getInt("scheduleId")
        )
    }
}

// 터미널 이름이 지역 이름과 같으면 지역만, 다르면 "대전(유성)"
private fun label(region: String, terminal: String) = if (terminal == region) region else "$region($terminal)"

/** 지금 시각을 분으로 (0시 0분 = 0) */
fun nowMinutes(): Int {
    val now = Calendar.getInstance()
    return now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
}

/** 출발·도착 시각과 지금 시각으로 운행예정 / 운행중 / 운행종료 를 정합니다. */
fun currentStatus(departure: String, arrival: String, nowMin: Int = nowMinutes()): String {
    val dep = minutes(departure)
    val arr = minutes(arrival).let { if (it < dep) it + 24 * 60 else it } // 자정을 넘기는 편
    return when {
        nowMin < dep -> "운행예정"
        nowMin < arr -> "운행중"
        else -> "운행종료"
    }
}

private fun minutes(hhmm: String) = hhmm.substring(0, 2).toInt() * 60 + hhmm.substring(3, 5).toInt()
