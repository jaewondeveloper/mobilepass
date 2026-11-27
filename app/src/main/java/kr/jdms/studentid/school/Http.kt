package kr.jdms.studentid.school

import java.net.HttpURLConnection
import java.net.URL

/** 네트워크 요청(테스트에서는 가짜로 바꾼다) */
interface Fetcher {
    fun get(url: String, charset: String = "UTF-8"): String
}

/**
 * 앱이 직접 보내는 평범한 GET 요청. 우회나 숨김 없이 앱 이름을 밝히고(User-Agent),
 * 연결/읽기 제한 시간을 두며, 일시적인 실패는 짧게 쉬었다가 최대 2번 다시 시도한다.
 * 같은 요청이 반복되지 않도록 호출하는 쪽에서 결과를 저장해 두고 쓴다.
 */
class UrlFetcher(private val retries: Int = 2) : Fetcher {
    override fun get(url: String, charset: String): String {
        var last: Exception? = null
        for (attempt in 0..retries) {
            try {
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 12000
                c.requestMethod = "GET"
                c.setRequestProperty("User-Agent", "JDMS-MobileStudentID/1.0 (Android)")
                c.setRequestProperty("Accept", "*/*")
                try {
                    val code = c.responseCode
                    if (code !in 200..299) throw java.io.IOException("HTTP $code")
                    return c.inputStream.use { String(it.readBytes(), charset(charset)) }
                } finally {
                    c.disconnect()
                }
            } catch (e: Exception) {
                last = e
                if (attempt < retries) Thread.sleep(400L * (attempt + 1))
            }
        }
        throw last ?: java.io.IOException("요청 실패")
    }
}
