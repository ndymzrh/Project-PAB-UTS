package id.ac.stmi.mobileproject

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Reverse geocoding: koordinat -> alamat, lewat Nominatim (OpenStreetMap).
 * Gratis tanpa API key. Kebijakan Nominatim: maksimal 1 permintaan/detik dan wajib
 * User-Agent yang mengidentifikasi aplikasi. Cocok untuk pemakaian ringan seperti
 * input lokasi bengkel oleh petugas; untuk trafik besar pakai layanan berbayar.
 *
 * Network berjalan di thread latar; callback selalu dipanggil di thread utama.
 */
object AlamatHelper {

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun cari(
        ctx: Context,
        lat: Double,
        lng: Double,
        onHasil: (String) -> Unit,
        onGagal: () -> Unit
    ) {
        val userAgent = "${ctx.applicationContext.packageName}/1.0"
        executor.execute {
            val hasil = runCatching { ambil(lat, lng, userAgent) }.getOrNull()
            main.post {
                if (hasil.isNullOrBlank()) onGagal() else onHasil(hasil)
            }
        }
    }

    private fun ambil(lat: Double, lng: Double, userAgent: String): String? {
        val url = URL(
            String.format(
                Locale.US,
                "https://nominatim.openstreetmap.org/reverse" +
                        "?format=jsonv2&lat=%.6f&lon=%.6f&zoom=18&addressdetails=1&accept-language=id",
                lat, lng
            )
        )
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept-Language", "id")
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return susunAlamat(JSONObject(body))
        } finally {
            conn.disconnect()
        }
    }

    /** Susun alamat gaya Indonesia: jalan + no, kelurahan, kecamatan, kota, provinsi, kode pos. */
    private fun susunAlamat(json: JSONObject): String? {
        if (json.has("error")) return null

        val a = json.optJSONObject("address")
        if (a != null) {
            fun ambil(vararg kunci: String): String? =
                kunci.firstNotNullOfOrNull { k -> a.optString(k).takeIf { it.isNotBlank() } }

            val jalan = listOfNotNull(
                ambil("road", "pedestrian", "footway"),
                ambil("house_number")?.let { "No. $it" }
            ).joinToString(" ").ifBlank { null }

            val bagian = listOfNotNull(
                jalan,
                ambil("neighbourhood", "quarter", "hamlet", "village"),
                ambil("suburb"),
                ambil("city_district", "district"),
                ambil("city", "town", "municipality", "regency", "county"),
                ambil("state"),
                ambil("postcode")
            ).distinct()

            if (bagian.isNotEmpty()) return bagian.joinToString(", ")
        }

        // Cadangan: teks lengkap dari Nominatim tanpa nama negara
        return json.optString("display_name")
            .removeSuffix(", Indonesia")
            .takeIf { it.isNotBlank() }
    }
}