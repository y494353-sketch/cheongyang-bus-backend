
package com.example.cheongyangbus

import android.content.Context

// 회원 정보는 서버(MySQL)에 있고, 기기에는 로그인 상태와 화면 설정만 저장합니다.
object AccountStore {

    private const val SESSION_PREF = "cheongyang_bus_session"
    private const val LOGIN_ID_KEY = "login_id"
    private const val ADMIN_KEY = "is_admin"
    private const val TOKEN_KEY = "token"
    private const val RECENT_ORIGIN_KEY = "recent_origin"
    private const val RECENT_DESTINATION_KEY = "recent_destination"
    private const val ALARMS_ON_KEY = "alarms_on"

    private fun prefs(context: Context) =
        context.getSharedPreferences(SESSION_PREF, Context.MODE_PRIVATE)

    // 서버 로그인 성공 시 저장. token 은 관리자 API를 부를 때 씀
    fun login(context: Context, id: String, isAdmin: Boolean, token: String) {
        prefs(context).edit()
            .putString(LOGIN_ID_KEY, id)
            .putBoolean(ADMIN_KEY, isAdmin)
            .putString(TOKEN_KEY, token)
            .apply()
    }

    // 로그아웃 (일반 회원·관리자 공통)
    fun logout(context: Context) {
        prefs(context).edit()
            .remove(LOGIN_ID_KEY)
            .remove(ADMIN_KEY)
            .remove(TOKEN_KEY)
            .apply()
    }

    // 로그인한 아이디 (로그아웃 상태면 null)
    fun loginId(context: Context): String? =
        prefs(context).getString(LOGIN_ID_KEY, null)

    // 로그인 상태 확인
    fun isLoggedIn(context: Context): Boolean =
        loginId(context) != null

    // 관리자 로그인 여부 (관리자 화면은 이 값이 true 일 때만 열림)
    fun isAdmin(context: Context): Boolean =
        isLoggedIn(context) && prefs(context).getBoolean(ADMIN_KEY, false)

    fun token(context: Context): String? =
        prefs(context).getString(TOKEN_KEY, null)

    // 최근 검색 노선 (메인 화면: 노선 표시와 다음 버스 카운트다운)
    fun recentSearch(context: Context): Pair<String, String>? {
        val origin = prefs(context).getString(RECENT_ORIGIN_KEY, null) ?: return null
        val destination = prefs(context).getString(RECENT_DESTINATION_KEY, null) ?: return null
        return origin to destination
    }

    fun setRecentSearch(context: Context, origin: String, destination: String) {
        prefs(context).edit()
            .putString(RECENT_ORIGIN_KEY, origin)
            .putString(RECENT_DESTINATION_KEY, destination)
            .apply()
    }

    // 출발 알림 켜기/끄기 (내 정보 화면). 기본값은 켜짐
    fun alarmsOn(context: Context): Boolean =
        prefs(context).getBoolean(ALARMS_ON_KEY, true)

    fun setAlarmsOn(context: Context, on: Boolean) {
        prefs(context).edit()
            .putBoolean(ALARMS_ON_KEY, on)
            .apply()
    }
}
