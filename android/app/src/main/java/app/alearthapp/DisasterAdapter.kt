package app.alearthapp

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class DisasterAdapter(
    private val onItemClick: (DisasterEvent) -> Unit
) : RecyclerView.Adapter<DisasterAdapter.ViewHolder>() {

    private var items: List<DisasterEvent> = emptyList()
    private var userLat: Double = 0.0
    private var userLon: Double = 0.0

    fun submitList(newItems: List<DisasterEvent>, uLat: Double = 0.0, uLon: Double = 0.0) {
        items = newItems
        userLat = uLat
        userLon = uLon
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_disaster, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], userLat, userLon, onItemClick)
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val badgeContainer: FrameLayout = itemView.findViewById(R.id.badgeContainer)
        private val tvMagnitude: TextView = itemView.findViewById(R.id.tvMagnitude)
        private val tvPlace: TextView = itemView.findViewById(R.id.tvPlace)
        private val tvMeta: TextView = itemView.findViewById(R.id.tvMeta)
        private val tvSource: TextView = itemView.findViewById(R.id.tvSource)
        private val tvHazardType: TextView = itemView.findViewById(R.id.tvHazardType)

        fun bind(
            event: DisasterEvent,
            uLat: Double,
            uLon: Double,
            onClick: (DisasterEvent) -> Unit
        ) {
            tvPlace.text = event.place.ifEmpty { "Unbekannter Ort" }
            tvSource.text = event.source

            // Badge Color & Text
            val badgeColor: Int
            if (event.type == DisasterType.QUAKE && event.magnitude != null) {
                val m = event.magnitude
                tvMagnitude.text = String.format(Locale.US, "M%.1f", m)
                badgeColor = when {
                    m >= 6.5 -> Color.parseColor("#9C27B0") // Purple
                    m >= 5.0 -> Color.parseColor("#E53935") // Red
                    m >= 4.0 -> Color.parseColor("#FB8C00") // Orange
                    else -> Color.parseColor("#FDD835")     // Yellow
                }
            } else {
                tvMagnitude.text = when (event.alertLevel) {
                    AlertLevel.RED -> "RED"
                    AlertLevel.ORANGE -> "ORG"
                    AlertLevel.GREEN -> "GRN"
                    else -> "!"
                }
                badgeColor = when (event.alertLevel) {
                    AlertLevel.RED -> Color.parseColor("#E53935")
                    AlertLevel.ORANGE -> Color.parseColor("#FB8C00")
                    AlertLevel.GREEN -> Color.parseColor("#43A047")
                    else -> Color.parseColor("#00ACC1")
                }
            }

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(badgeColor)
            }
            badgeContainer.background = bg

            // Hazard Type label & color
            tvHazardType.text = when (event.type) {
                DisasterType.QUAKE -> "Erdbeben"
                DisasterType.TSUNAMI -> "Tsunami"
                DisasterType.STORM -> "Sturm"
                DisasterType.FLOOD -> "Überschwemmung"
                DisasterType.WILDFIRE -> "Waldbrand"
                DisasterType.VOLCANO -> "Vulkan"
                DisasterType.OTHER -> "Naturereignis"
            }

            // Meta Info: Zeit, Tiefe, Distanz
            val timeRel = if (event.timeMs > 0) {
                DateUtils.getRelativeTimeSpanString(
                    event.timeMs,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                    DateUtils.FORMAT_ABBREV_RELATIVE
                ).toString()
            } else ""

            val depthStr = event.depthKm?.let { String.format(Locale.US, " · %.0f km Tiefe", it) } ?: ""
            val distStr = if (uLat != 0.0 || uLon != 0.0) {
                val dist = DisastersRepository.haversineKm(uLat, uLon, event.lat, event.lon)
                String.format(Locale.US, " · %.0f km", dist)
            } else ""

            tvMeta.text = "$timeRel$depthStr$distStr".trimStart(' ', '·')

            itemView.setOnClickListener { onClick(event) }
        }
    }
}
