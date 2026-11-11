
package com.example.cheongyangbus

import android.content.Context

// 회원 정보는 서버(MySQL)에 있고, 기기에는 로그인 상태와 화면 설정만 저장합니다.
object AccountStore {

    private const val SESSION_PREF = "cheongyang_bus_session"
    private const val LOGIN_ID_KEY = "login_id"
    private const val RECENT_ROUTE_KEY = "recent_route"
    private const val ALARMS_ON_KEY = "alarms_on"

    private fun prefs(context: Context) =
        context.getSharedPreferences(SESSION_PREF, Context.MODE_PRIVATE)

    // 로그인한 아이디 (로그아웃 상태면 null)
    fun loginId(context: Context): String? =
        prefs(context).getString(LOGIN_ID_KEY, null)

    // 서버 로그인 성공 시 아이디 저장, 로그아웃 시 null
    fun setLoginId(context: Context, id: String?) {
        prefs(context).edit()
            .putString(LOGIN_ID_KEY, id)
            .apply()
    }

    // 로그인 상태 확인
    fun isLoggedIn(context: Context): Boolean =
        loginId(context) != null

    // 최근 검색 노선 (메인 화면)
    fun recentRoute(context: Context): String? =
        prefs(context).getString(RECENT_ROUTE_KEY, null)

    fun setRecentRoute(context: Context, route: String) {
        prefs(context).edit()
            .putString(RECENT_ROUTE_KEY, route)
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
