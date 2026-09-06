package app.alert2iq

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.util.Locale

class MapActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FOCUS_LAT = "focus_lat"
        const val EXTRA_FOCUS_LON = "focus_lon"
        const val EXTRA_FOCUS_ID = "focus_id"
    }

    private lateinit var mapView: MapView
    private lateinit var progressBar: ProgressBar
    private lateinit var eventCard: CardView
    private lateinit var tvMapEventMag: TextView
    private lateinit var tvMapEventPlace: TextView
    private lateinit var tvMapEventMeta: TextView
    private lateinit var mapBadgeContainer: FrameLayout
    private lateinit var btnShareEvent: Button
    private lateinit var btnOpenSource: Button

    private var rawEvents: List<DisasterEvent> = emptyList()
    private var activeLayer = "ALL" // "ALL", "QUAKES", "TSUNAMI", "HAZARDS"
    private var selectedEvent: DisasterEvent? = null

    private var userLat: Double = 0.0
    private var userLon: Double = 0.0

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withAppLocale())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Prefs.init(this)
        Prefs.applyNightMode()
        setTheme(Prefs.themeStyleRes(alert = false))
        super.onCreate(savedInstanceState)

        // OSMDroid Konfiguration
        Configuration.getInstance().userAgentValue = "Alert2IQ/1.0 (Android)"
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))

        setContentView(R.layout.activity_map)

        val toolbar = findViewById<Toolbar>(R.id.mapToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        extractUserLocation()

        mapView = findViewById(R.id.mapView)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        progressBar = findViewById(R.id.mapProgressBar)
        eventCard = findViewById(R.id.eventCard)
        tvMapEventMag = findViewById(R.id.tvMapEventMag)
        tvMapEventPlace = findViewById(R.id.tvMapEventPlace)
        tvMapEventMeta = findViewById(R.id.tvMapEventMeta)
        mapBadgeContainer = findViewById(R.id.mapBadgeContainer)
        btnShareEvent = findViewById(R.id.btnShareEvent)
        btnOpenSource = findViewById(R.id.btnOpenSource)

        findViewById<ImageButton>(R.id.btnCloseCard).setOnClickListener {
            eventCard.visibility = View.GONE
            selectedEvent = null
        }

        btnShareEvent.setOnClickListener {
            selectedEvent?.let { ev ->
                val magStr = ev.magnitude?.let { "M$it" } ?: ev.type.name
                val text = "$magStr · ${ev.place} (Quelle: ${ev.source}) — via Alert2IQ"
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                startActivity(Intent.createChooser(sendIntent, getString(R.string.report_share)))
            }
        }

        btnOpenSource.setOnClickListener {
            selectedEvent?.url?.takeIf { it.isNotEmpty() }?.let { url ->
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
        }

        val fabLocate = findViewById<FloatingActionButton>(R.id.fabLocate)
        fabLocate.setOnClickListener {
            if (userLat != 0.0 || userLon != 0.0) {
                mapView.controller.animateTo(GeoPoint(userLat, userLon), 10.0, 800L)
            } else {
                mapView.controller.animateTo(GeoPoint(39.0, 35.0), 6.5, 800L)
            }
        }

        setupLayerButtons()

        // Initiale Karten-Positionierung
        val focusLat = intent.getDoubleExtra(EXTRA_FOCUS_LAT, 0.0)
        val focusLon = intent.getDoubleExtra(EXTRA_FOCUS_LON, 0.0)
        if (focusLat != 0.0 && focusLon != 0.0) {
            mapView.controller.setZoom(9.0)
            mapView.controller.setCenter(GeoPoint(focusLat, focusLon))
        } else if (userLat != 0.0 && userLon != 0.0) {
            mapView.controller.setZoom(7.5)
            mapView.controller.setCenter(GeoPoint(userLat, userLon))
        } else {
            mapView.controller.setZoom(6.2)
            mapView.controller.setCenter(GeoPoint(39.0, 35.0))
        }

        loadData()
    }

    private fun extractUserLocation() {
        val subs = Prefs.pushSubscriptions
        runCatching {
            val arr = JSONObject(subs).optJSONArray("subscriptions")
            if (arr != null && arr.length() > 0) {
                val first = arr.optJSONObject(0)
                if (first != null && first.has("lat") && first.has("lon")) {
                    userLat = first.optDouble("lat")
                    userLon = first.optDouble("lon")
                }
            }
        }
    }

    private fun setupLayerButtons() {
        val btnQuakes = findViewById<TextView>(R.id.btnLayerQuakes)
        val btnTsunami = findViewById<TextView>(R.id.btnLayerTsunami)
        val btnHazards = findViewById<TextView>(R.id.btnLayerHazards)

        fun updateUI() {
            btnQuakes.setTextColor(if (activeLayer == "ALL" || activeLayer == "QUAKES") Color.WHITE else Color.parseColor("#80FFFFFF"))
            btnTsunami.setTextColor(if (activeLayer == "TSUNAMI") Color.WHITE else Color.parseColor("#80FFFFFF"))
            btnHazards.setTextColor(if (activeLayer == "HAZARDS") Color.WHITE else Color.parseColor("#80FFFFFF"))
            renderMarkers()
        }

        btnQuakes.setOnClickListener {
            activeLayer = if (activeLayer == "QUAKES") "ALL" else "QUAKES"
            updateUI()
        }
        btnTsunami.setOnClickListener {
            activeLayer = if (activeLayer == "TSUNAMI") "ALL" else "TSUNAMI"
            updateUI()
        }
        btnHazards.setOnClickListener {
            activeLayer = if (activeLayer == "HAZARDS") "ALL" else "HAZARDS"
            updateUI()
        }
    }

    private fun loadData() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                rawEvents = DisastersRepository.fetchAll()
                renderMarkers()

                // Falls ein bestimmtes Event fokussiert werden soll:
                val focusId = intent.getStringExtra(EXTRA_FOCUS_ID)
                if (!focusId.isNullOrEmpty()) {
                    rawEvents.find { it.id == focusId }?.let { showEventCard(it) }
                }
            } catch (_: Exception) { }
            finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun renderMarkers() {
        mapView.overlays.clear()

        // 1. User Position Marker
        if (userLat != 0.0 || userLon != 0.0) {
            val userPoint = GeoPoint(userLat, userLon)
            val userMarker = Marker(mapView).apply {
                position = userPoint
                title = "Ihr Standort"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            }
            mapView.overlays.add(userMarker)

            // 150 km Beobachtungskreis
            val circle = Polygon.pointsAsCircle(userPoint, 150_000.0)
            val circleOverlay = Polygon(mapView).apply {
                points = circle
                outlinePaint.color = Color.parseColor("#3000BCD4")
                outlinePaint.strokeWidth = 2f
                fillPaint.color = Color.parseColor("#1000BCD4")
            }
            mapView.overlays.add(circleOverlay)
        }

        // 2. Disaster Markers
        val eventsToShow = when (activeLayer) {
            "QUAKES" -> rawEvents.filter { it.type == DisasterType.QUAKE }
            "TSUNAMI" -> rawEvents.filter { it.type == DisasterType.TSUNAMI }
            "HAZARDS" -> rawEvents.filter { it.type != DisasterType.QUAKE }
            else -> rawEvents
        }

        for (ev in eventsToShow) {
            val point = GeoPoint(ev.lat, ev.lon)
            val marker = Marker(mapView).apply {
                position = point
                title = ev.place
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

                setOnMarkerClickListener { _, _ ->
                    showEventCard(ev)
                    true
                }
            }

            // Erdbeben-Erschütterungskreise
            if (ev.type == DisasterType.QUAKE && (ev.magnitude ?: 0.0) >= 4.0) {
                val mag = ev.magnitude!!
                val radiusMeters = when {
                    mag >= 6.5 -> 120_000.0
                    mag >= 5.0 -> 60_000.0
                    else -> 25_000.0
                }
                val colorHex = when {
                    mag >= 6.5 -> "#409C27B0"
                    mag >= 5.0 -> "#40E53935"
                    else -> "#30FB8C00"
                }

                val quakeCircle = Polygon(mapView).apply {
                    points = Polygon.pointsAsCircle(point, radiusMeters)
                    outlinePaint.color = Color.parseColor(colorHex.replace("#40", "#A0").replace("#30", "#80"))
                    outlinePaint.strokeWidth = 3f
                    fillPaint.color = Color.parseColor(colorHex)
                }
                mapView.overlays.add(quakeCircle)
            }

            mapView.overlays.add(marker)
        }

        mapView.invalidate()
    }

    private fun showEventCard(event: DisasterEvent) {
        selectedEvent = event
        eventCard.visibility = View.VISIBLE

        tvMapEventPlace.text = event.place.ifEmpty { "Ereignis" }

        val badgeColor: Int
        if (event.type == DisasterType.QUAKE && event.magnitude != null) {
            val m = event.magnitude
            tvMapEventMag.text = String.format(Locale.US, "M%.1f", m)
            badgeColor = when {
                m >= 6.5 -> Color.parseColor("#9C27B0")
                m >= 5.0 -> Color.parseColor("#E53935")
                m >= 4.0 -> Color.parseColor("#FB8C00")
                else -> Color.parseColor("#FDD835")
            }
        } else {
            tvMapEventMag.text = event.type.name.take(3)
            badgeColor = Color.parseColor("#00ACC1")
        }

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(badgeColor)
        }
        mapBadgeContainer.background = bg

        val timeRel = if (event.timeMs > 0) {
            DateUtils.getRelativeTimeSpanString(
                event.timeMs,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            ).toString()
        } else ""

        val depthStr = event.depthKm?.let { String.format(Locale.US, " · %.0f km Tiefe", it) } ?: ""
        val distStr = if (userLat != 0.0 || userLon != 0.0) {
            val dist = DisastersRepository.haversineKm(userLat, userLon, event.lat, event.lon)
            String.format(Locale.US, " · %.0f km entfernt", dist)
        } else ""

        tvMapEventMeta.text = "$timeRel$depthStr$distStr · Quelle: ${event.source}".trimStart(' ', '·')
        btnOpenSource.visibility = if (event.url.isNotEmpty()) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        mapView.onDetach()
        super.onDestroy()
    }
}
