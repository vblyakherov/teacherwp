package com.kubyshka.teacherworkspace.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class InMemoryCookieJar : CookieJar {
    private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val key = url.host
        val storedCookies = cookieStore.getOrPut(key) { mutableListOf() }
        cookies.forEach { newCookie ->
            storedCookies.removeAll { it.name == newCookie.name }
            if (!newCookie.hasExpired()) {
                storedCookies.add(newCookie)
            }
        }
        if (storedCookies.isEmpty()) {
            cookieStore.remove(key)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val key = url.host
        val cookies = cookieStore[key] ?: return emptyList()
        val validCookies = cookies.filterNot { it.hasExpired() }
        if (validCookies.size != cookies.size) {
            if (validCookies.isEmpty()) {
                cookieStore.remove(key)
            } else {
                cookieStore[key] = validCookies.toMutableList()
            }
        }
        return validCookies
    }

    private fun Cookie.hasExpired(): Boolean {
        return expiresAt < System.currentTimeMillis()
    }
}
