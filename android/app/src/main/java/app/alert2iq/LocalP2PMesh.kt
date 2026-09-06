package app.alert2iq

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Local Subnet P2P Emergency Mesh (Alert2IQ - Back2IQ Studio).
 *
 * Broadcasts ultra-fast 64-byte binary UDP packets across the local Wi-Fi / LAN subnet
 * to alert nearby devices in < 2ms without waiting for cellular tower routing.
 */
object LocalP2PMesh {

    private const val MULTICAST_PORT = 8888
    private const val MULTICAST_GROUP = "239.255.255.250"
    private const val MESH_MAGIC: Short = 0x4132 // "A2" ASCII

    data class MeshAlertPacket(
        val geohash: String,
        val magnitude: Double,
        val timestampMs: Long,
        val deviceIdHash: String
    )

    /**
     * Packs a 64-byte binary alert packet.
     */
    fun packAlertPayload(
        geohash: String,
        magnitude: Double,
        timestampMs: Long,
        deviceIdHash: String
    ): ByteArray {
        val buffer = ByteBuffer.allocate(64).order(ByteOrder.BIG_ENDIAN)
        buffer.putShort(MESH_MAGIC) // 2 bytes
        val geoBytes = geohash.padEnd(8, ' ').take(8).toByteArray(Charsets.UTF_8)
        buffer.put(geoBytes) // 8 bytes
        buffer.putDouble(magnitude) // 8 bytes
        buffer.putLong(timestampMs) // 8 bytes
        val devBytes = deviceIdHash.padEnd(16, ' ').take(16).toByteArray(Charsets.UTF_8)
        buffer.put(devBytes) // 16 bytes
        // 22 bytes padding to reach exactly 64 bytes
        buffer.put(ByteArray(22))
        return buffer.array()
    }

    /**
     * Unpacks a 64-byte binary alert packet.
     */
    fun unpackAlertPayload(data: ByteArray): MeshAlertPacket? {
        if (data.size < 42) return null
        val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        val magic = buffer.short
        if (magic != MESH_MAGIC) return null

        val geoBytes = ByteArray(8)
        buffer.get(geoBytes)
        val geohash = String(geoBytes, Charsets.UTF_8).trim()

        val magnitude = buffer.double
        val timestampMs = buffer.long

        val devBytes = ByteArray(16)
        buffer.get(devBytes)
        val deviceIdHash = String(devBytes, Charsets.UTF_8).trim()

        return MeshAlertPacket(geohash, magnitude, timestampMs, deviceIdHash)
    }

    /**
     * Broadcasts a 64-byte alert payload to the local subnet asynchronously.
     */
    suspend fun broadcastLocalAlert(
        geohash: String,
        magnitude: Double,
        deviceIdHash: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val payload = packAlertPayload(geohash, magnitude, System.currentTimeMillis(), deviceIdHash)
            DatagramSocket().use { socket ->
                socket.broadcast = true
                val groupAddress = InetAddress.getByName(MULTICAST_GROUP)
                val packet = DatagramPacket(payload, payload.size, groupAddress, MULTICAST_PORT)
                socket.send(packet)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
