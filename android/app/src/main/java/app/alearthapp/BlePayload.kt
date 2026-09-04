package app.alearthapp

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.math.pow

enum class BleSosStatus(val code: Byte) {
    TRAPPED(1),
    INJURED(2),
    OK(3);

    companion object {
        fun fromCode(code: Byte): BleSosStatus = values().find { it.code == code } ?: TRAPPED
    }
}

enum class BleBloodType(val code: Byte, val label: String) {
    UNKNOWN(0, "Unknown"),
    A_POS(1, "A+"),
    A_NEG(2, "A-"),
    B_POS(3, "B+"),
    B_NEG(4, "B-"),
    AB_POS(5, "AB+"),
    AB_NEG(6, "AB-"),
    O_POS(7, "0+"),
    O_NEG(8, "0-");

    companion object {
        fun fromCode(code: Byte): BleBloodType = values().find { it.code == code } ?: UNKNOWN
        fun fromString(str: String): BleBloodType = when (str.trim().uppercase().replace("O", "0")) {
            "A+", "A POS", "A POSITIVE" -> A_POS
            "A-", "A NEG", "A NEGATIVE" -> A_NEG
            "B+", "B POS", "B POSITIVE" -> B_POS
            "B-", "B NEG", "B NEGATIVE" -> B_NEG
            "AB+", "AB POS" -> AB_POS
            "AB-", "AB NEG" -> AB_NEG
            "0+", "0 POS", "O+", "O POS" -> O_POS
            "0-", "0 NEG", "O-", "O NEG" -> O_NEG
            else -> UNKNOWN
        }
    }
}

enum class BleGender(val code: Byte, val label: String) {
    UNKNOWN(0, "Unknown"),
    MALE(1, "M"),
    FEMALE(2, "F"),
    DIVERSE(3, "D");

    companion object {
        fun fromCode(code: Byte): BleGender = values().find { it.code == code } ?: UNKNOWN
        fun fromString(str: String): BleGender = when (str.trim().uppercase()) {
            "M", "MALE", "MÄNNLICH", "ERKEK" -> MALE
            "F", "FEMALE", "WEIBLICH", "KADIN" -> FEMALE
            "D", "DIVERSE", "DIVERS" -> DIVERSE
            else -> UNKNOWN
        }
    }
}

data class BleTriageProfile(
    val bloodType: BleBloodType = BleBloodType.UNKNOWN,
    val gender: BleGender = BleGender.UNKNOWN,
    val age: Int = 0, // 0..120
    val isUserResponsive: Boolean = false, // true = aktiv getippt, false = Deadman Timeout
    val hasInsulinDiabetes: Boolean = false,
    val hasHeartCondition: Boolean = false,
    val hasRespiratoryRisk: Boolean = false,
    val nameInitials: String = "" // Bis zu 4 Zeichen, z.B. "D.K." oder "DENI"
) {
    fun toFlags(): Byte {
        var flags = 0
        if (isUserResponsive) flags = flags or (1 shl 0)
        if (hasInsulinDiabetes) flags = flags or (1 shl 1)
        if (hasHeartCondition) flags = flags or (1 shl 2)
        if (hasRespiratoryRisk) flags = flags or (1 shl 3)
        return flags.toByte()
    }

    companion object {
        fun fromFlags(
            bloodCode: Byte,
            genderCode: Byte,
            ageByte: Byte,
            flagsByte: Byte,
            initialsBytes: ByteArray
        ): BleTriageProfile {
            val flags = flagsByte.toInt()
            val rawName = String(initialsBytes, StandardCharsets.UTF_8).trim('\u0000', ' ')
            return BleTriageProfile(
                bloodType = BleBloodType.fromCode(bloodCode),
                gender = BleGender.fromCode(genderCode),
                age = ageByte.toInt() and 0xFF,
                isUserResponsive = (flags and (1 shl 0)) != 0,
                hasInsulinDiabetes = (flags and (1 shl 1)) != 0,
                hasHeartCondition = (flags and (1 shl 2)) != 0,
                hasRespiratoryRisk = (flags and (1 shl 3)) != 0,
                nameInitials = rawName
            )
        }
    }
}

data class BleSosMessage(
    val status: BleSosStatus,
    val batteryPercent: Int,
    val timestampSec: Long,
    val coarseLat: Float = 0.0f,
    val coarseLon: Float = 0.0f,
    val triage: BleTriageProfile = BleTriageProfile()
) {
    companion object {
        const val MAGIC: Short = 0xAE01.toShort()
        const val LEGACY_PAYLOAD_SIZE: Int = 16
        const val EXTENDED_PAYLOAD_SIZE: Int = 24 // 16 legacy + 1 blood + 1 gender + 1 age + 1 flags + 4 initials

        fun encode(msg: BleSosMessage): ByteArray {
            val buf = ByteBuffer.allocate(EXTENDED_PAYLOAD_SIZE).order(ByteOrder.LITTLE_ENDIAN)
            buf.putShort(MAGIC)
            buf.put(msg.status.code)
            buf.put(msg.batteryPercent.coerceIn(0, 100).toByte())
            buf.putInt((msg.timestampSec and 0xFFFFFFFFL).toInt())
            buf.putFloat(msg.coarseLat)
            buf.putFloat(msg.coarseLon)

            // Erweiterte Triage- & Personendaten
            buf.put(msg.triage.bloodType.code)
            buf.put(msg.triage.gender.code)
            buf.put(msg.triage.age.coerceIn(0, 120).toByte())
            buf.put(msg.triage.toFlags())

            val nameBytes = ByteArray(4)
            val srcBytes = msg.triage.nameInitials.take(4).toByteArray(StandardCharsets.UTF_8)
            System.arraycopy(srcBytes, 0, nameBytes, 0, minOf(srcBytes.size, 4))
            buf.put(nameBytes)

            return buf.array()
        }

        fun decode(bytes: ByteArray?): BleSosMessage? {
            if (bytes == null || bytes.size < LEGACY_PAYLOAD_SIZE) return null
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = buf.short
            if (magic != MAGIC) return null

            val status = BleSosStatus.fromCode(buf.get())
            val battery = buf.get().toInt() and 0xFF
            val ts = buf.int.toLong() and 0xFFFFFFFFL
            val lat = buf.float
            val lon = buf.float

            val triage = if (bytes.size >= EXTENDED_PAYLOAD_SIZE) {
                val blood = buf.get()
                val gender = buf.get()
                val age = buf.get()
                val flags = buf.get()
                val nameBytes = ByteArray(4)
                buf.get(nameBytes)
                BleTriageProfile.fromFlags(blood, gender, age, flags, nameBytes)
            } else {
                BleTriageProfile()
            }

            return BleSosMessage(
                status = status,
                batteryPercent = battery,
                timestampSec = ts,
                coarseLat = lat,
                coarseLon = lon,
                triage = triage
            )
        }

        fun estimateDistanceMeters(rssi: Int, txPower: Int = -59): Double {
            if (rssi == 0) return -1.0
            val ratio = rssi * 1.0 / txPower
            return if (ratio < 1.0) {
                ratio.pow(10.0)
            } else {
                (0.89976) * ratio.pow(7.7095) + 0.111
            }
        }
    }

    fun isValidTimestamp(nowSec: Long = System.currentTimeMillis() / 1000L, maxDeltaSec: Long = 120L): Boolean {
        if (timestampSec <= 0L) return false
        val delta = kotlin.math.abs(nowSec - timestampSec)
        return delta <= maxDeltaSec
    }
}
