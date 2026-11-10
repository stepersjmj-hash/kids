package io.github.stepersjmj.kids

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * 앱 업데이트 알림 (v1.5+).
 * 켤 때 GitHub 최신 릴리스(tv-vX.Y)를 확인해 지금 버전보다 새것이면 "새 버전" 창을 띄우고,
 * [업데이트] 를 누르면 고정 주소의 kids-tv.apk 를 받아 PackageInstaller 세션으로 설치한다.
 * - Play 스토어 밖 앱이라 안드로이드가 설치 확인 화면을 한 번 띄운다(첫 업데이트 때는 "이 출처 허용" 설정도).
 * - Android 12+ 에서는 자기 자신이 설치한 뒤부터 확인 없이 설치될 수도 있다(USER_ACTION_NOT_REQUIRED 요청).
 * - 서명이 같아야 덮어써지므로 늘 같은 PC(같은 debug 키)에서 빌드할 것.
 */
object Updater {
    private const val API = "https://api.github.com/repos/stepersjmj-hash/kids/releases/latest"
    private const val APK = "https://github.com/stepersjmj-hash/kids/releases/latest/download/kids-tv.apk"
    private const val CHECK_EVERY = 6 * 60 * 60_000L   // 켜 둔 채로 있으면 6시간마다 다시 확인
    private val main = Handler(Looper.getMainLooper())
    private var lastCheck = 0L
    private var busy = false

    // "tv-v1.6" → [1, 6]
    private fun parse(v: String) = Regex("""\d+(\.\d+)*""").find(v)?.value?.split('.')?.map { it.toInt() } ?: emptyList()
    private fun newer(a: List<Int>, b: List<Int>): Boolean {
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    fun check(act: Activity, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (busy || (!force && now - lastCheck < CHECK_EVERY)) return
        lastCheck = now
        thread {
            try {
                val c = URL(API).openConnection() as HttpURLConnection
                c.setRequestProperty("User-Agent", "kids-tv-app")
                c.setRequestProperty("Accept", "application/vnd.github+json")
                c.connectTimeout = 15000; c.readTimeout = 15000
                if (c.responseCode != 200) return@thread
                val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                val tag = j.optString("tag_name")
                val latest = parse(tag)
                if (!tag.startsWith("tv-v") || !newer(latest, parse(BuildConfig.VERSION_NAME))) return@thread
                val notes = j.optString("body").trim().take(300)
                main.post { if (!act.isFinishing) ask(act, latest.joinToString("."), notes) }
            } catch (_: Exception) { /* 네트워크 오류는 조용히 넘김 — 다음에 다시 확인 */ }
        }
    }

    private fun ask(act: Activity, ver: String, notes: String) {
        AlertDialog.Builder(act, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("새 버전 v$ver 이 있어요")
            .setMessage("지금 버전: v${BuildConfig.VERSION_NAME}" + (if (notes.isNotEmpty()) "\n\n$notes" else "") +
                "\n\n[업데이트] 를 누르면 받아서 설치합니다. 설치 확인 화면이 나오면 '설치' 를 눌러 주세요. 끝나면 하이쮸를 다시 열면 됩니다.")
            .setPositiveButton("업데이트") { _, _ -> start(act) }
            .setNegativeButton("나중에", null)
            .show()
            .getButton(AlertDialog.BUTTON_POSITIVE).requestFocus()
    }

    private fun start(act: Activity) {
        // 이 앱이 다른 앱(자기 새 버전)을 설치해도 되는지 — 처음 한 번은 설정에서 허용해야 함
        if (Build.VERSION.SDK_INT >= 26 && !act.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(act, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("설치 허용이 필요해요 (처음 한 번)")
                .setMessage("다음 화면에서 '하이쮸' 를 허용으로 바꾼 뒤, 뒤로 돌아와 다시 [업데이트] 를 눌러 주세요.\n\n" +
                    "화면이 안 열리면: 설정 → 개인정보 보호 → 보안 → 알 수 없는 소스 → 하이쮸 켜기")
                .setPositiveButton("설정 열기") { _, _ ->
                    lastCheck = 0   // 돌아오면(onResume) 바로 다시 묻도록
                    try { act.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + act.packageName))) }
                    catch (_: ActivityNotFoundException) {
                        try { act.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
                        catch (_: ActivityNotFoundException) { Toast.makeText(act, "설정 → 개인정보 보호 → 보안 → 알 수 없는 소스 에서 하이쮸를 켜 주세요", Toast.LENGTH_LONG).show() }
                    }
                }
                .setNegativeButton("나중에", null)
                .show()
            return
        }
        busy = true
        val progress = AlertDialog.Builder(act, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("새 버전 받는 중…").setMessage("0%").setCancelable(false).show()
        thread {
            try {
                val c = URL(APK).openConnection() as HttpURLConnection   // github.com → objects.githubusercontent.com 리다이렉트(https→https 는 자동)
                c.setRequestProperty("User-Agent", "kids-tv-app")
                c.connectTimeout = 15000; c.readTimeout = 30000
                if (c.responseCode != 200) throw Exception("HTTP ${c.responseCode}")
                val total = c.contentLengthLong
                val pi = act.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                val id = pi.createSession(params)
                pi.openSession(id).use { s ->
                    c.inputStream.use { input ->
                        s.openWrite("kids-tv.apk", 0, if (total > 0) total else -1).use { out ->
                            val buf = ByteArray(64 * 1024); var done = 0L; var shown = -1
                            while (true) {
                                val n = input.read(buf); if (n < 0) break
                                out.write(buf, 0, n); done += n
                                val pct = if (total > 0) (done * 100 / total).toInt() else -1
                                if (pct != shown && pct >= 0) { shown = pct; main.post { progress.setMessage("$pct%") } }
                            }
                            s.fsync(out)
                        }
                    }
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    val pending = PendingIntent.getBroadcast(act, id, Intent(act, InstallReceiver::class.java), flags)
                    s.commit(pending.intentSender)
                }
                main.post { progress.dismiss() }
            } catch (e: Exception) {
                main.post { progress.dismiss(); Toast.makeText(act, "업데이트 받기 실패: ${e.message}", Toast.LENGTH_LONG).show() }
            } finally { busy = false }
        }
    }
}

/** PackageInstaller 결과: 확인이 필요하면 시스템 설치 확인 화면을 띄우고, 실패하면 알림 */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                               else intent.getParcelableExtra(Intent.EXTRA_INTENT)) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> { /* 새 버전으로 바뀌며 앱이 닫힘 — 다시 열면 됨 */ }
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
                Toast.makeText(ctx, "업데이트 설치 실패 $msg", Toast.LENGTH_LONG).show()
            }
        }
    }
}
