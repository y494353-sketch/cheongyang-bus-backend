package com.example.cheongyangbus.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** 노선 (출발 터미널 → 도착 터미널) */
data class Route(
    val id: Int,
    val originRegion: String,
    val originTerminal: String,
    val destRegion: String,
    val destTerminal: String,
    val scheduleCount: Int
) {
    // 터미널 이름이 지역 이름과 같으면 지역만, 다르면 "대전(유성)"
    val label: String
        get() = "${place(originRegion, originTerminal)} → ${place(destRegion, destTerminal)}"
}

/** 관리자 시간표 목록의 한 편 */
data class ManagedSchedule(
    val id: Int,
    val routeId: Int,
    val routeLabel: String,
    val departure: String,
    val arrival: String,
    val duration: String
)

/** 회원 관리 목록의 한 명 (비밀번호 해시는 받지 않음) */
data class Member(val loginId: String, val isAdmin: Boolean, val createdAt: String)

/**
 * 관리자 화면에서 부르는 서버 API. 로그인 때 받은 token 을 같이 보냅니다.
 * 관리자 로그인이 풀렸으면 ApiException(code = 401), 서버가 거절하면 ApiException(이유 문장).
 */
object AdminApi {

    // ---------------------------------------------------------------- 노선

    suspend fun routes(token: String?): List<Route> {
        val array = JSONArray(Api.get("/api/admin/routes", token))
        return List(array.length()) { i ->
            val o = array.getJSONObject(i)
            Route(
                o.getInt("routeId"),
                o.getString("originRegion"), o.getString("originTerminal"),
                o.getString("destRegion"), o.getString("destTerminal"),
                o.getInt("scheduleCount")
            )
        }
    }

    /** 노선 추가. 터미널은 비워 두면 지역 이름을 씀 */
    suspend fun addRoute(token: String?, originRegion: String, originTerminal: String, destRegion: String, destTerminal: String) {
        val body = JSONObject()
            .put("originRegion", originRegion).put("originTerminal", originTerminal)
            .put("destRegion", destRegion).put("destTerminal", destTerminal)
        Api.check(Api.request("POST", "/api/admin/routes", body, token))
    }

    suspend fun deleteRoute(token: String?, routeId: Int) {
        Api.check(Api.request("DELETE", "/api/admin/routes/$routeId", token = token))
    }

    // ---------------------------------------------------------------- 시간표

    suspend fun schedules(token: String?): List<ManagedSchedule> {
        val array = JSONArray(Api.get("/api/admin/schedules", token))
        return List(array.length()) { i ->
            val o = array.getJSONObject(i)
            ManagedSchedule(
                id = o.getInt("scheduleId"),
                routeId = o.getInt("routeId"),
                routeLabel = "${place(o.getString("originRegion"), o.getString("originTerminal"))} → " +
                    place(o.getString("destRegion"), o.getString("destTerminal")),
                departure = o.getString("departure"),
                arrival = o.getString("arrival"),
                duration = o.getString("durationText")
            )
        }
    }

    suspend fun addSchedule(token: String?, routeId: Int, departure: String, arrival: String) {
        Api.check(Api.request("POST", "/api/admin/schedules", schedule(routeId, departure, arrival), token))
    }

    suspend fun updateSchedule(token: String?, scheduleId: Int, routeId: Int, departure: String, arrival: String) {
        Api.check(Api.request("PUT", "/api/admin/schedules/$scheduleId", schedule(routeId, departure, arrival), token))
    }

    suspend fun deleteSchedule(token: String?, scheduleId: Int) {
        Api.check(Api.request("DELETE", "/api/admin/schedules/$scheduleId", token = token))
    }

    // ---------------------------------------------------------------- 공지사항 (목록은 Api.notices)

    suspend fun addNotice(token: String?, title: String, content: String) {
        Api.check(Api.request("POST", "/api/admin/notices", notice(title, content), token))
    }

    suspend fun updateNotice(token: String?, noticeId: Int, title: String, content: String) {
        Api.check(Api.request("PUT", "/api/admin/notices/$noticeId", notice(title, content), token))
    }

    suspend fun deleteNotice(token: String?, noticeId: Int) {
        Api.check(Api.request("DELETE", "/api/admin/notices/$noticeId", token = token))
    }

    // ---------------------------------------------------------------- 회원

    suspend fun members(token: String?): List<Member> {
        val array = JSONArray(Api.get("/api/admin/users", token))
        return List(array.length()) { i ->
            val o = array.getJSONObject(i)
            Member(o.getString("loginId"), o.getString("role") == "ADMIN", o.getString("createdAt"))
        }
    }

    suspend fun deleteMember(token: String?, loginId: String) {
        val id = URLEncoder.encode(loginId, "UTF-8").replace("+", "%20")
        Api.check(Api.request("DELETE", "/api/admin/users/$id", token = token))
    }

    // ---------------------------------------------------------------- 내부

    private fun schedule(routeId: Int, departure: String, arrival: String) =
        JSONObject().put("routeId", routeId).put("departure", departure).put("arrival", arrival)

    private fun notice(title: String, content: String) =
        JSONObject().put("title", title).put("content", content)
}

/** 시간 형식 확인: 00:00 ~ 23:59 */
fun isValidTime(time: String) = Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(time)

private fun place(region: String, terminal: String) = if (terminal == region) region else "$region($terminal)"
