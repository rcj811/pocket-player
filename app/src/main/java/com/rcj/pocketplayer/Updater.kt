package com.rcj.pocketplayer

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 每次開啟 App 檢查 GitHub Releases 有沒有新版（新版會帶最新的 NewPipe 解析引擎），
 * 有的話一鍵下載並開啟安裝畫面。
 */
object Updater {
    private const val REPO = "rcj811/pocket-player"
    private const val APK_NAME = "pocket-player.apk"

    data class Release(val build: Int, val apkUrl: String, val notes: String)

    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
    }

    fun currentBuild(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }

    /** 在背景執行緒呼叫。沒有新版或失敗回傳 null。 */
    fun checkNewer(context: Context): Release? {
        return try {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val json = JSONObject(resp.body?.string() ?: return null)
            val build = json.optString("tag_name").removePrefix("build-").toIntOrNull() ?: return null
            if (build <= currentBuild(context)) return null
            val assets = json.optJSONArray("assets") ?: return null
            var url: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name") == APK_NAME) url = a.optString("browser_download_url")
            }
            Release(build, url ?: return null, json.optString("body"))
        }
    } catch (_: Exception) {
        null
    }
    }

    /** 允許安裝未知來源 App（第一次更新時需要）。回傳 false 表示已帶使用者去設定頁。 */
    fun ensureInstallPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()) return true
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return false
    }

    fun downloadAndInstall(context: Context, release: Release) {
        val app = context.applicationContext
        val dm = app.getSystemService(DownloadManager::class.java)
        val request = DownloadManager.Request(Uri.parse(release.apkUrl))
            .setTitle("隨身聽 build ${release.build}")
            .setDescription("下載新版中…")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "pocket-player-${release.build}.apk")
        val id = dm.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return
                try {
                    app.unregisterReceiver(this)
                } catch (_: Exception) {
                }
                val uri = dm.getUriForDownloadedFile(id) ?: return
                app.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
        ContextCompat.registerReceiver(
            app, receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED,
        )
    }
}
