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

/** 서버가 오류 코드로 답한 경우. message 는 서버가 보낸 이유 (없으면 "HTTP 코드") */
class ApiException(val code: Int, message: String) : IOException(message)

/** 로그인 결과. token 은 관리자 API를 부를 때 씀 */
data class LoginResult(val isAdmin: Boolean, val token: String)

/** 공지사항 한 건 */
data class Notice(val id: Int, val title: String, val content: String, val createdAt: String)

/**
 * 서버(server/ 폴더의 Spring Boot) 호출. 서버가 MySQL 을 읽고 씁니다.
 * 모든 함수는 lifecycleScope.launch { } 안에서 부르고,
 * 서버에 연결이 안 되면 IOException, 서버가 거절하면 ApiException(IOException 의 한 종류)을 던집니다.
 * 관리자용 호출은 AdminApi.kt 에 있습니다.
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

    /** 로그인. 아이디·비밀번호가 틀리면 null */
    suspend fun login(loginId: String, password: String): LoginResult? {
        val (code, body) = request("POST", "/api/auth/login", account(loginId, password))
        return when (code) {
            200 -> JSONObject(body).let { LoginResult(it.getString("role") == "ADMIN", it.getString("token")) }
            401 -> null
            else -> throw IOException("login $code")
        }
    }

    /** 로그아웃 (서버의 토큰 폐기) */
    suspend fun logout(token: String?) {
        request("POST", "/api/auth/logout", token = token)
    }

    /** 버스 조회 화면의 지역 목록. 청양 출발이면 갈 수 있는 지역, 청양 도착이면 오는 지역 */
    suspend fun regions(departingFrom: String? = null, arrivingAt: String? = null): List<String> {
        val query = if (departingFrom != null) "origin=${enc(departingFrom)}" else "dest=${enc(arrivingAt.orEmpty())}"
        val array = JSONArray(get("/api/regions?$query"))
        return List(array.length()) { array.getString(it) }
    }

    /** 출발 지역 → 도착 지역 시간표 (출발 시각 순) */
    suspend fun schedules(origin: String, destination: String): List<BusTime> =
        busList(get("/api/schedules?origin=${enc(origin)}&dest=${enc(destination)}"))

    /** 공지사항 목록 (최신순) */
    suspend fun notices(): List<Notice> {
        val array = JSONArray(get("/api/notices"))
        return List(array.length()) { i ->
            val o = array.getJSONObject(i)
            Notice(o.getInt("noticeId"), o.getString("title"), o.getString("content"), o.getString("createdAt"))
        }
    }

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

    internal suspend fun get(path: String, token: String? = null): String {
        val response = request("GET", path, token = token)
        check(response)
        return response.second
    }

    /** 2xx 가 아니면 ApiException (서버가 {"message": ...} 를 보냈으면 그 문장) */
    internal fun check(response: Pair<Int, String>) {
        if (response.first in 200..299) return
        val message = try {
            JSONObject(response.second).getString("message")
        } catch (e: Exception) {
            "HTTP ${response.first}"
        }
        throw ApiException(response.first, message)
    }

    /** HTTP 요청 → (응답 코드, 응답 본문). token 은 X-Auth-Token 헤더로 보냄 */
    internal suspend fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        token: String? = null
    ): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val conn = URL(BASE_URL + path).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                if (token != null) conn.setRequestProperty("X-Auth-Token", token)
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

/** 지금 시각을 분으로 (0시 0분 = 0, 한국 시간 기준) */
fun nowMinutes(): Int {
    val now = SeoulTime.calendar()
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

/**
 * 메인 화면 카운트다운 문구. buses 는 출발 시각 순 시간표.
 * 예) "다음 버스 09:58 출발 · 12분 후", 오늘 버스가 끝났으면 "오늘 버스는 끝났어요 · 내일 첫차 07:02"
 */
fun nextBusText(buses: List<BusTime>, nowMin: Int = nowMinutes()): String {
    if (buses.isEmpty()) return "시간표 정보가 없습니다."
    val next = buses.firstOrNull { minutes(it.departure) >= nowMin }
        ?: return "오늘 버스는 끝났어요 · 내일 첫차 ${buses.first().departure}"
    val left = minutes(next.departure) - nowMin
    val wait = when {
        left == 0 -> "곧 출발"
        left < 60 -> "${left}분 후"
        left % 60 == 0 -> "${left / 60}시간 후"
        else -> "${left / 60}시간 ${left % 60}분 후"
    }
    return "다음 버스 ${next.departure} 출발 · $wait"
}

private fun minutes(hhmm: String) = hhmm.substring(0, 2).toInt() * 60 + hhmm.substring(3, 5).toInt()
