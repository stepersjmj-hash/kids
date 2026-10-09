package io.github.stepersjmj.kids

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

/**
 * 하이쮸 재생목록 TV 앱.
 * - 웹페이지(?tv=1)를 WebView 로 띄우고 재생·목록·동기화는 페이지가 담당한다.
 * - 구글은 WebView 안 로그인을 막으므로, 로그인은 OAuth 기기 코드 흐름(폰으로 QR/코드 입력)으로
 *   앱이 직접 하고, 받은 액세스 토큰을 페이지의 window.kidsTvToken() 으로 넘긴다.
 */
class MainActivity : Activity() {

    private lateinit var web: WebView
    private lateinit var loginView: LinearLayout
    private lateinit var loginQr: ImageView
    private lateinit var loginCode: TextView
    private lateinit var loginMsg: TextView

    private val main = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("auth", Context.MODE_PRIVATE) }
    private var pageReady = false
    private var accessToken: String? = null
    private var accessExp = 0L
    private var polling = false
    private var lastBack = 0L

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.userAgentString = settings.userAgentString + " KidsTV/1"
            setBackgroundColor(Color.BLACK)
            addJavascriptInterface(Bridge(), "KidsTV")
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    pageReady = true
                    if (hasRefreshToken()) refreshAndInject() else showLogin()
                }
            }
        }

        loginView = buildLoginView()
        val root = FrameLayout(this)
        root.addView(web, FrameLayout.LayoutParams(-1, -1))
        root.addView(loginView, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        // 테스트용: adb shell am start ... --es url "https://..." 로 시작 주소를 바꿀 수 있다
        loadPage()
        web.requestFocus()

        // 액세스 토큰(1시간)을 만료 전에 갱신
        main.postDelayed(object : Runnable {
            override fun run() {
                if (hasRefreshToken() && System.currentTimeMillis() > accessExp - 10 * 60_000) refreshAndInject()
                main.postDelayed(this, 5 * 60_000)
            }
        }, 5 * 60_000)
    }

    /* ---------- 페이지 ↔ 앱 ---------- */
    inner class Bridge {
        @JavascriptInterface fun login() = main.post { startDeviceLogin() }
        @JavascriptInterface fun logout() = main.post {
            prefs.edit().remove("refresh").apply()
            accessToken = null; accessExp = 0
            showLogin()
        }
        @JavascriptInterface fun isApp() = true
        @JavascriptInterface fun reload() = main.post { loadPage() }   // TV 목록의 "새로고침" 줄
    }

    private fun inject() {
        val t = accessToken ?: return
        if (!pageReady) return
        web.evaluateJavascript("window.kidsTvToken && window.kidsTvToken(${JSONObject.quote(t)}, $accessExp)", null)
    }

    /* ---------- 리모컨 ---------- */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            // 로그인 화면은 뒤로 버튼으로 닫을 수 있다 (로그인 없이 이 TV의 목록만 쓰기)
            if (loginView.visibility == View.VISIBLE) { hideLogin(); return true }
            // 페이지가 목록 오버레이를 닫았으면 true, 아니면 두 번 눌러 종료
            web.evaluateJavascript("(window.kidsTvBack && window.kidsTvBack()) ? 1 : 0") { r ->
                if (r == "1") return@evaluateJavascript
                val now = System.currentTimeMillis()
                if (now - lastBack < 2000) finish()
                else { lastBack = now; Toast.makeText(this, "한 번 더 누르면 종료합니다", Toast.LENGTH_SHORT).show() }
            }
            return true
        }
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return true
        // 방향키·확인·미디어 키는 WebView 의 포커스 이동에 맡기지 않고 페이지로 직접 넘긴다
        // (그렇지 않으면 화살표가 하단 버튼 사이를 옮겨 다니고 확인 키가 그 버튼을 누름)
        val jsKey = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> "ArrowLeft"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "ArrowRight"
            KeyEvent.KEYCODE_DPAD_UP -> "ArrowUp"
            KeyEvent.KEYCODE_DPAD_DOWN -> "ArrowDown"
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> "Enter"
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> "MediaPlayPause"
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "MediaTrackNext"
            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_REWIND -> "MediaTrackPrevious"
            else -> null
        }
        if (jsKey != null && loginView.visibility != View.VISIBLE) {
            if (event.action == KeyEvent.ACTION_DOWN && (event.repeatCount == 0 || jsKey == "ArrowUp" || jsKey == "ArrowDown"))
                web.evaluateJavascript("window.kidsTvKey && window.kidsTvKey('$jsKey')", null)
            return true
        }
        // 로그인 화면에서 가운데 버튼 → 새 코드 (Chromecast 리모컨엔 메뉴 키가 없음). 메뉴 키가 있는 리모컨은 메뉴 키도 동작
        if (loginView.visibility == View.VISIBLE && event.action == KeyEvent.ACTION_UP && (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER)) { if (!polling) { loginCode.text = ""; startDeviceLogin() }; return true }
        if (loginView.visibility == View.VISIBLE && (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER)) return true
        if (event.keyCode == KeyEvent.KEYCODE_MENU && event.action == KeyEvent.ACTION_UP) { startDeviceLogin(); return true }
        return super.dispatchKeyEvent(event)
    }

    /* ---------- OAuth 기기 코드 흐름 ---------- */
    // 기기 코드 흐름은 drive.appdata 를 허용하지 않음(Invalid device flow scope) → drive.file 사용
    private val scope = "openid email profile https://www.googleapis.com/auth/drive.file"

    private fun hasRefreshToken() = prefs.getString("refresh", null) != null

    private fun post(url: String, params: Map<String, String>): Pair<Int, JSONObject> {
        val body = params.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        c.connectTimeout = 15000; c.readTimeout = 15000
        c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        val text = (if (code < 400) c.inputStream else c.errorStream).bufferedReader().use { it.readText() }
        return code to JSONObject(text.ifBlank { "{}" })
    }

    private fun refreshAndInject() {
        val refresh = prefs.getString("refresh", null) ?: return
        thread {
            try {
                val (code, j) = post("https://oauth2.googleapis.com/token", mapOf(
                    "client_id" to BuildConfig.TV_CLIENT_ID,
                    "client_secret" to BuildConfig.TV_CLIENT_SECRET,
                    "refresh_token" to refresh,
                    "grant_type" to "refresh_token"))
                main.post {
                    if (code == 200) {
                        accessToken = j.getString("access_token")
                        accessExp = System.currentTimeMillis() + (j.optLong("expires_in", 3600) - 60) * 1000
                        hideLogin(); inject()
                    } else if (j.optString("error") == "invalid_grant") {
                        // 권한 철회·만료(테스트 모드는 7일) → 다시 로그인
                        prefs.edit().remove("refresh").apply()
                        startDeviceLogin("로그인이 만료되었습니다. 다시 로그인해 주세요.")
                    }
                }
            } catch (e: Exception) {
                main.postDelayed({ refreshAndInject() }, 30_000)   // 네트워크 오류 → 잠시 후 재시도
            }
        }
    }

    private fun startDeviceLogin(notice: String? = null) {
        // 주의: 여기서 showLogin() 을 부르면 안 됨 (showLogin → startDeviceLogin 무한 재귀로 크래시했던 부분)
        loginView.visibility = View.VISIBLE
        if (BuildConfig.TV_CLIENT_ID.isBlank()) { loginMsg.text = "TV용 클라이언트 ID가 빌드에 없습니다 (local.properties 확인)"; return }
        if (polling) return                      // 이미 코드 발급·대기 중
        polling = true                           // 메인 스레드에서 바로 세워 중복 요청 방지
        loginMsg.text = notice ?: "로그인 코드를 받는 중…"
        thread {
            try {
                val (code, j) = post("https://oauth2.googleapis.com/device/code",
                    mapOf("client_id" to BuildConfig.TV_CLIENT_ID, "scope" to scope))
                if (code != 200) { polling = false; main.post { loginMsg.text = "코드 발급 실패: ${j.optString("error_description", j.optString("error"))}" }; return@thread }
                val url = j.getString("verification_url")
                val user = j.getString("user_code")
                main.post {
                    loginCode.text = user
                    loginQr.setImageBitmap(qr(url, 360))
                    loginMsg.text = (notice?.let { "$it\n" } ?: "") +
                        "휴대폰으로 QR을 찍거나 $url 에 접속해 위 코드를 입력하세요.\n뒤로 버튼: 닫기 · 가운데 버튼: 새 코드"
                }
                pollToken(j.getString("device_code"), j.optLong("interval", 5), System.currentTimeMillis() + j.optLong("expires_in", 1800) * 1000)
            } catch (e: Exception) {
                polling = false
                main.post { loginMsg.text = "네트워크 오류: ${e.message}\n가운데 버튼을 눌러 다시 시도하세요." }
            }
        }
    }

    private fun pollToken(deviceCode: String, intervalSec: Long, deadline: Long) {
        var interval = intervalSec
        try {
            while (System.currentTimeMillis() < deadline) {
                Thread.sleep(interval * 1000)
                val (code, j) = post("https://oauth2.googleapis.com/token", mapOf(
                    "client_id" to BuildConfig.TV_CLIENT_ID,
                    "client_secret" to BuildConfig.TV_CLIENT_SECRET,
                    "device_code" to deviceCode,
                    "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"))
                if (code == 200) {
                    prefs.edit().putString("refresh", j.getString("refresh_token")).apply()
                    main.post {
                        accessToken = j.getString("access_token")
                        accessExp = System.currentTimeMillis() + (j.optLong("expires_in", 3600) - 60) * 1000
                        hideLogin(); inject()
                        Toast.makeText(this, "로그인했습니다", Toast.LENGTH_SHORT).show()
                    }
                    return
                }
                when (j.optString("error")) {
                    "authorization_pending" -> {}
                    "slow_down" -> interval += 5
                    "access_denied" -> { main.post { loginMsg.text = "로그인이 거부되었습니다. 가운데 버튼을 눌러 다시 시도하세요." }; return }
                    else -> { main.post { loginMsg.text = "로그인 실패: ${j.optString("error")}. 가운데 버튼을 눌러 다시 시도하세요." }; return }
                }
            }
            main.post { loginMsg.text = "코드가 만료되었습니다. 가운데 버튼을 눌러 새 코드를 받으세요." }
        } catch (e: Exception) {
            main.post { loginMsg.text = "네트워크 오류: ${e.message}\n가운데 버튼을 눌러 다시 시도하세요." }
        } finally {
            polling = false
        }
    }

    /* ---------- 로그인 화면 ---------- */
    private fun showLogin() {
        loginView.visibility = View.VISIBLE
        if (BuildConfig.TV_CLIENT_ID.isBlank()) loginMsg.text = "TV용 클라이언트 ID가 빌드에 없습니다 (local.properties 확인)\n뒤로 버튼으로 닫을 수 있습니다."
        else if (loginCode.text.isNullOrBlank() && !polling) startDeviceLogin()
    }
    private fun hideLogin() { loginView.visibility = View.GONE; web.requestFocus() }

    private fun buildLoginView(): LinearLayout {
        fun tv(size: Float, bold: Boolean = false, color: Int = Color.rgb(232, 234, 240)) = TextView(this).apply {
            textSize = size; setTextColor(color); gravity = Gravity.CENTER
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
        loginQr = ImageView(this)
        loginCode = tv(44f, true, Color.WHITE).apply { letterSpacing = 0.1f }
        loginMsg = tv(18f, color = Color.rgb(154, 163, 181)).apply { setPadding(0, 24, 0, 0) }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(15, 17, 21))
            visibility = View.GONE
            isClickable = true
            addView(tv(30f, true).apply { text = "하이쮸 재생목록 — Google 로그인"; setPadding(0, 0, 0, 24) })
            addView(loginQr, LinearLayout.LayoutParams(360, 360).apply { gravity = Gravity.CENTER })
            addView(loginCode)
            addView(loginMsg)
        }
    }

    private fun qr(text: String, size: Int): Bitmap {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size) bmp.setPixel(x, y, if (m[x, y]) Color.BLACK else Color.WHITE)
        return bmp
    }

    // 페이지(index.html)는 항상 최신으로: 캐시 무시용 쿼리를 붙인다 (유튜브 플레이어 등 나머지는 캐시 사용)
    private fun loadPage() {
        val base = intent.getStringExtra("url") ?: BuildConfig.START_URL
        pageReady = false
        web.loadUrl(base + (if ('?' in base) "&" else "?") + "_v=" + System.currentTimeMillis())
    }

    // 홈으로 나갔다 오면 Activity 가 살아 있어 예전 페이지가 그대로 남는다 →
    // 10분 넘게 나가 있었으면 새로 불러온다 (페이지가 마지막 영상부터 이어 재생)
    private var pausedAt = 0L
    override fun onResume() {
        super.onResume(); web.onResume()
        if (pausedAt > 0 && System.currentTimeMillis() - pausedAt > 10 * 60_000) loadPage()
        else if (hasRefreshToken() && System.currentTimeMillis() > accessExp) refreshAndInject()
    }
    override fun onPause() { pausedAt = System.currentTimeMillis(); web.onPause(); super.onPause() }
    override fun onDestroy() { polling = false; main.removeCallbacksAndMessages(null); web.destroy(); super.onDestroy() }
}
