package com.stasybell.os

import android.content.Context

/**
 * Only DEBUG APKs may use an owner-selected Mac LAN IP. Never permits public
 * HTTP endpoints, hostnames, credentials, custom paths or arbitrary ports.
 */
object LocalTestServer {
    private const val PREFS = "stasy_debug_mac_lan_v1"
    private const val HOST_KEY = "mac_ipv4"

    fun validHost(host: String): Boolean {
        val values = host.split(".")
        if (values.size != 4 || values.any {
                it.isEmpty() || it.length > 3 || it.any { ch -> !ch.isDigit() }
            }) return false
        val bytes = values.map { it.toIntOrNull() ?: return false }
        if (bytes.any { it !in 0..255 }) return false
        return bytes[0] == 10 ||
            (bytes[0] == 172 && bytes[1] in 16..31) ||
            (bytes[0] == 192 && bytes[1] == 168)
    }

    fun host(context: Context): String {
        if (!BuildConfig.DEBUG) return ""
        val candidate = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(HOST_KEY, "").orEmpty()
        return candidate.takeIf(::validHost).orEmpty()
    }

    fun baseUrl(context: Context): String {
        if (!BuildConfig.DEBUG) return BuildConfig.API_BASE_URL
        val current = host(context)
        return if (current.isBlank()) BuildConfig.API_BASE_URL else "http://$current:8000/"
    }

    fun setHost(context: Context, proposed: String): Boolean {
        if (!BuildConfig.DEBUG) return false
        val ip = proposed.trim()
        if (!validHost(ip)) return false
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(HOST_KEY, ip).commit()
    }
}
