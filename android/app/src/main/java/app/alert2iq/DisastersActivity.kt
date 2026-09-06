package app.alert2iq

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch
import org.json.JSONObject

class DisastersActivity : AppCompatActivity() {

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvCountHeader: TextView
    private lateinit var tvEmptyState: TextView
    private lateinit var adapter: DisasterAdapter

    private var rawEvents: List<DisasterEvent> = emptyList()
    private var currentFilter = DisastersFilter(region = RegionFilter.TURKEY)
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
        setContentView(R.layout.activity_disasters)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        extractUserLocation()

        swipeRefresh = findViewById(R.id.swipeRefresh)
        recyclerView = findViewById(R.id.recyclerViewDisasters)
        progressBar = findViewById(R.id.progressBar)
        tvCountHeader = findViewById(R.id.tvCountHeader)
        tvEmptyState = findViewById(R.id.tvEmptyState)

        adapter = DisasterAdapter { event ->
            val intent = Intent(this, MapActivity::class.java).apply {
                putExtra(MapActivity.EXTRA_FOCUS_LAT, event.lat)
                putExtra(MapActivity.EXTRA_FOCUS_LON, event.lon)
                putExtra(MapActivity.EXTRA_FOCUS_ID, event.id)
            }
            startActivity(intent)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        swipeRefresh.setOnRefreshListener { loadData() }

        findViewById<Button>(R.id.btnOpenMap).setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
        }

        setupFilters()
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

    private fun setupFilters() {
        val chipTr = findViewById<Chip>(R.id.chipRegionTr)
        val chipNear = findViewById<Chip>(R.id.chipRegionNear)
        val chipWorld = findViewById<Chip>(R.id.chipRegionWorld)
        val chipM4 = findViewById<Chip>(R.id.chipMinMag4)
        val chipM5 = findViewById<Chip>(R.id.chipMinMag5)
        val chipQuakes = findViewById<Chip>(R.id.chipOnlyQuakes)

        val listener = {
            val region = when {
                chipNear.isChecked -> RegionFilter.NEARBY
                chipWorld.isChecked -> RegionFilter.ALL
                else -> RegionFilter.TURKEY
            }
            val minMag = when {
                chipM5.isChecked -> 5.0
                chipM4.isChecked -> 4.0
                else -> 0.0
            }
            val types = if (chipQuakes.isChecked) setOf(DisasterType.QUAKE) else emptySet()

            currentFilter = DisastersFilter(
                types = types,
                region = region,
                minMag = minMag,
                timeWindowHours = 48
            )
            applyFilterAndRender()
        }

        chipTr.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) { chipNear.isChecked = false; chipWorld.isChecked = false }
            listener()
        }
        chipNear.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) { chipTr.isChecked = false; chipWorld.isChecked = false }
            listener()
        }
        chipWorld.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) { chipTr.isChecked = false; chipNear.isChecked = false }
            listener()
        }
        chipM4.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) chipM5.isChecked = false
            listener()
        }
        chipM5.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) chipM4.isChecked = false
            listener()
        }
        chipQuakes.setOnCheckedChangeListener { _, _ -> listener() }
    }

    private fun loadData() {
        progressBar.visibility = if (rawEvents.isEmpty()) View.VISIBLE else View.GONE
        tvEmptyState.visibility = View.GONE

        lifecycleScope.launch {
            try {
                rawEvents = DisastersRepository.fetchAll()
                applyFilterAndRender()
            } catch (_: Exception) {
                tvCountHeader.text = getString(R.string.disasters_fetch_error)
            } finally {
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun applyFilterAndRender() {
        val filtered = DisastersRepository.filter(
            rawEvents,
            currentFilter,
            userLat = userLat,
            userLon = userLon
        )
        val sorted = DisastersRepository.sort(filtered, SortOption.TIME)

        adapter.submitList(sorted, userLat, userLon)

        val count = sorted.size
        tvCountHeader.text = getString(R.string.disasters_count_format, count)
        tvEmptyState.visibility = if (count == 0) View.VISIBLE else View.GONE
    }
}
