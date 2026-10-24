package id.ac.stmi.mobileproject

import com.google.firebase.Timestamp

/**
 * Dokumen: users/{uid}/vehicles/{vehicleId}
 * Semua properti punya nilai default supaya bisa dibaca Firestore (toObject).
 */
data class Vehicle(
    val id: String = "",
    val brand: String = "",              // contoh: Toyota
    val model: String = "",              // contoh: Avanza
    val year: Int = 0,                   // contoh: 2019
    val plateNumber: String = "",        // contoh: B 1234 XYZ
    val odometer: Long = 0,              // km saat ini
    val lastServiceOdometer: Long = 0,   // km saat servis terakhir
    val serviceIntervalKm: Long = 5000,  // jarak servis berkala
    val primary: Boolean = false,        // mobil utama yang ditampilkan di beranda
    val createdAt: Timestamp? = null
) {
    val displayName: String
        get() = listOf(brand, model, if (year > 0) year.toString() else "")
            .filter { it.isNotBlank() }
            .joinToString(" ")
}
