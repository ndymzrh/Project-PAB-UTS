package id.ac.stmi.mobileproject

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class KategoriPromo(val label: String) {
    SERVIS("Servis"), SPAREPART("Sparepart"), POIN("Poin")
}

/** Satu promo. Field disamakan dengan data promo di sisi customer. */
data class PromoItem(
    val id: String,
    val judul: String,
    val kategori: KategoriPromo,
    val kode: String,
    val jenisDiskon: String,      // "Persen" atau "Nominal"
    val nilai: Long,
    val mulai: Long,
    val berakhir: Long,
    val berlaku: String,
    val syarat: String,           // satu syarat per baris
    val gambar: String = "bg_promo1" // nama drawable
) {
    val labelDiskon: String
        get() = when {
            jenisDiskon == "Persen" && nilai >= 100 -> "Gratis"
            jenisDiskon == "Persen" -> "$nilai%"
            else -> "Rp " + "%,d".format(nilai).replace(',', '.')
        }
}

fun Long.toTanggal(): String =
    SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).format(Date(this))

fun gambarDefault(k: KategoriPromo) = when (k) {
    KategoriPromo.SERVIS -> "bg_promo1"
    KategoriPromo.POIN -> "bg_promo2"
    KategoriPromo.SPAREPART -> "bg_promo3"
}

/** Ambil id drawable dari nama; jatuh ke bg_promo1 bila tidak ada. */
fun Context.drawableId(nama: String): Int {
    val id = resources.getIdentifier(nama, "drawable", packageName)
    return if (id != 0) id else R.drawable.bg_promo1
}