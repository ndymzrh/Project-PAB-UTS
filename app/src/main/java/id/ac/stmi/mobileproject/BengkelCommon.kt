package id.ac.stmi.mobileproject

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.DocumentSnapshot
import org.osmdroid.config.Configuration
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Dokumen Firestore: koleksi "bengkel"
 *   nama (String), alamat (String), telepon (String), jamOperasional (String),
 *   lokasi (GeoPoint), aktif (Boolean), createdAt / updatedAt (Timestamp)
 */
data class Bengkel(
    val id: String,
    val nama: String,
    val alamat: String,
    val telepon: String,
    val jamOperasional: String,
    val lat: Double,
    val lng: Double,
    val aktif: Boolean
)

/** Dokumen tanpa field "lokasi" dianggap belum lengkap dan dilewati. */
fun DocumentSnapshot.toBengkel(): Bengkel? {
    val gp = getGeoPoint("lokasi") ?: return null
    return Bengkel(
        id = id,
        nama = getString("nama") ?: "-",
        alamat = getString("alamat") ?: "",
        telepon = getString("telepon") ?: "",
        jamOperasional = getString("jamOperasional") ?: "",
        lat = gp.latitude,
        lng = gp.longitude,
        aktif = getBoolean("aktif") ?: true
    )
}

/** Konfigurasi osmdroid (peta OpenStreetMap, tanpa API key). Panggil SEBELUM setContentView. */
object OsmSetup {
    fun init(ctx: Context) {
        val cfg = Configuration.getInstance()
        cfg.userAgentValue = ctx.packageName
        val dasar = File(ctx.cacheDir, "osmdroid")
        cfg.osmdroidBasePath = dasar
        cfg.osmdroidTileCache = File(dasar, "tiles")
    }
}

object Jarak {
    fun meter(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val hasil = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, hasil)
        return hasil[0]
    }

    fun format(meter: Float): String =
        if (meter < 1000f) "${meter.roundToInt()} m"
        else String.format(Locale("id", "ID"), "%.1f km", meter / 1000f)
}

object LokasiHelper {
    val IZIN = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    fun punyaIzin(ctx: Context): Boolean = IZIN.any {
        ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Ambil lokasi terakhir; jika belum ada, minta satu pembacaan baru. */
    @SuppressLint("MissingPermission")
    fun ambil(ctx: Context, onHasil: (Location) -> Unit, onGagal: () -> Unit) {
        if (!punyaIzin(ctx)) { onGagal(); return }
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val aktif = lm.getProviders(true)

        val terakhir = aktif
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (terakhir != null) { onHasil(terakhir); return }

        val provider = when {
            LocationManager.GPS_PROVIDER in aktif -> LocationManager.GPS_PROVIDER
            LocationManager.NETWORK_PROVIDER in aktif -> LocationManager.NETWORK_PROVIDER
            else -> null
        }
        if (provider == null) { onGagal(); return }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            lm.getCurrentLocation(
                provider,
                null,
                ContextCompat.getMainExecutor(ctx)
            ) { loc -> if (loc != null) onHasil(loc) else onGagal() }
        } else {
            @Suppress("DEPRECATION")
            lm.requestSingleUpdate(
                provider,
                { loc -> onHasil(loc) },
                ctx.mainLooper
            )
        }
    }

    /**
     * Berlangganan pembaruan lokasi secara realtime.
     * Mengembalikan LocationListener agar pemanggil dapat menghentikannya dengan stopPantau(ctx, listener).
     */
    @SuppressLint("MissingPermission")
    fun pantau(
        ctx: Context,
        minWaktuMs: Long = 2000L,
        minJarakMeter: Float = 1f,
        onUpdate: (Location) -> Unit
    ): LocationListener? {
        if (!punyaIzin(ctx)) return null
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val aktif = lm.getProviders(true)
        if (aktif.isEmpty()) return null

        val listener = LocationListener { loc -> onUpdate(loc) }
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (provider in aktif) {
                runCatching {
                    lm.requestLocationUpdates(
                        provider,
                        minWaktuMs,
                        minJarakMeter,
                        listener,
                        ctx.mainLooper
                    )
                }
            }
        }
        return listener
    }

    fun stopPantau(ctx: Context, listener: LocationListener?) {
        if (listener == null) return
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        runCatching { lm.removeUpdates(listener) }
    }
}

/** Buka aplikasi peta (Google Maps dll.) untuk navigasi ke bengkel. */
fun bukaPeta(ctx: Context, b: Bengkel) {
    val geo = Uri.parse("geo:${b.lat},${b.lng}?q=${b.lat},${b.lng}(${Uri.encode(b.nama)})")
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, geo))
    } catch (e: ActivityNotFoundException) {
        val web = Uri.parse("https://www.google.com/maps/search/?api=1&query=${b.lat},${b.lng}")
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, web))
        } catch (_: ActivityNotFoundException) {
        }
    }
}