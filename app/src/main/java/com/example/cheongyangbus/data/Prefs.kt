package com.example.cheongyangbus.data

import android.content.Context

/** 로그인한 아이디, 최근 검색 경로, 출발 알림 켜기/끄기를 기기에 저장합니다. */
object Prefs {
    private fun prefs(context: Context) = context.getSharedPreferences("cheongyang_bus", Context.MODE_PRIVATE)

    fun loginId(context: Context): String? = prefs(context).getString("loginId", null)
    fun setLoginId(context: Context, id: String?) = prefs(context).edit().putString("loginId", id).apply()

    fun recentRoute(context: Context): String? = prefs(context).getString("recentRoute", null)
    fun setRecentRoute(context: Context, route: String) = prefs(context).edit().putString("recentRoute", route).apply()

    /** 꺼 두면 예약한 출발 알림이 울리지 않습니다. 기본값은 켜짐. */
    fun alarmsOn(context: Context): Boolean = prefs(context).getBoolean("alarmsOn", true)
    fun setAlarmsOn(context: Context, on: Boolean) = prefs(context).edit().putBoolean("alarmsOn", on).apply()
}
