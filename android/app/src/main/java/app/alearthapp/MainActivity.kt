package app.alearthapp

import android.annotation.SuppressLint
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat

/**
 * Hybrid-Hülle: hostet die vollständige, gestaltete Web-Oberfläche (assets/webapp,
 * identisch zu tda/webapp) als installierte App. Die Inhalts-Screens (Start, Karte,
 * Recent Disasters, Onboarding, Vorsorge, FAQ, Einstellungen) sind das Original-Design;
 * die lebensrettende Native-Schicht (Vollbild-Alarm, Nachbeben-Dienst, Notsignal,
 * Beacon) wird als nächster Schritt über eine JS-Brücke angebunden.
 *
 * Geladen über [WebViewAssetLoader] unter https://appassets.androidplatform.net/... ,
 * damit die Cross-Origin-Fetches der Web-App (USGS/EMSC/EONET, Fonts, Leaflet) und
 * localStorage (tda_prefs: Onboarding, Sprache, Standort) wie im Browser funktionieren.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    companion object { private const val REQ_PICK_SOUND = 301 }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.init(this)
        Prefs.applyNightMode()
        setTheme(Prefs.themeStyleRes(alert = false))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        NotificationChannels.ensure(this)
        requestRuntimePermissions()

        // FCM-Token sicherstellen (onNewToken feuert nur bei Neuvergabe) und ggf. registrieren.
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
            .addOnSuccessListener { t ->
                Prefs.fcmToken = t
                PushRegistrar.register(applicationContext)
            }

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView = findViewById(R.id.webView)
        webView.addJavascriptInterface(WebBridge(this), "AndroidBridge")
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setGeolocationEnabled(true)
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        webView.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.host == "appassets.androidplatform.net") return false
                // Externe Links (Amazon-/Google-Suche, Quellen-URLs) im System-Browser öffnen.
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, url)) }
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                callback.invoke(origin, true, false)
            }
        }

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl("https://appassets.androidplatform.net/assets/webapp/index.html")
        }
    }

    private fun requestRuntimePermissions() {
        val wanted = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            wanted += android.Manifest.permission.POST_NOTIFICATIONS
        }
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            wanted += android.Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (wanted.isNotEmpty()) requestPermissions(wanted.toTypedArray(), 101)
    }

    /** Nativer System-Ringtone-Picker (Typ Alarm); Auswahl landet in Prefs.alarmSoundUri. */
    fun pickAlarmSound() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.pick_alarm_sound))
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            Prefs.alarmSoundUri.takeIf { it.isNotEmpty() }?.let {
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it))
            }
        }
        runCatching { startActivityForResult(intent, REQ_PICK_SOUND) }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK_SOUND && resultCode == RESULT_OK) {
            val uri: Uri? = data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            Prefs.alarmSoundUri = uri?.toString() ?: ""
            Toast.makeText(this, R.string.alarm_sound_saved, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (this::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
