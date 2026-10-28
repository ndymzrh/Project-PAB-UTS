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
    val judul: String,            // nama voucher; harus sama/mirip dengan nama layanan agar bisa dipakai
    val kategori: KategoriPromo,
    val kode: String,
    val jenisDiskon: String,      // "Persen" atau "Nominal"
    val nilai: Long,
    val mulai: Long,
    val berakhir: Long,
    val berlaku: String,
    val syarat: String,           // satu syarat per baris
    val gambar: String = "bg_promo1", // nama drawable
    val layananIds: List<String> = emptyList(), // tidak dipakai lagi; dipertahankan agar data lama tetap terbaca
    val poinDibutuhkan: Long = 0L // poin yang dibutuhkan untuk menukar; hanya dipakai kategori POIN
) {
    val labelDiskon: String
        get() = when {
            jenisDiskon == "Persen" && nilai >= 100 -> "Gratis"
            jenisDiskon == "Persen" -> "$nilai%"
            else -> "Rp " + "%,d".format(nilai).replace(',', '.')
        }

    /** Voucher bisa dipakai jika nama voucher SAMA atau MIRIP dengan nama layanan. */
    fun bisaDipakaiUntuk(namaLayanan: String): Boolean =
        namaLayananMirip(judul, namaLayanan)

    /** True jika promo ini bisa ditukar dengan poin. */
    val bisaDitukarPoin: Boolean
        get() = kategori == KategoriPromo.POIN && poinDibutuhkan > 0

    /** Teks poin untuk ditampilkan di UI, mis. "100 poin". Kosong jika bukan promo poin. */
    val labelPoin: String
        get() = if (bisaDitukarPoin) "%,d poin".format(poinDibutuhkan).replace(',', '.') else ""
}

private fun normalisasiNama(s: String) =
    s.trim().replace(Regex("\\s+"), " ").lowercase()

/** Kata umum yang tidak menentukan jenis layanan. */
private val KATA_UMUM = setOf(
    "servis", "service", "ganti", "paket", "voucher", "diskon", "promo",
    "gratis", "hemat", "dan", "untuk", "&", "dengan", "full"
)

private fun kataKunci(s: String): Set<String> =
    normalisasiNama(s)
        .replace(Regex("[^a-z0-9 ]"), " ")
        .split(" ")
        .filter { it.isNotBlank() && it !in KATA_UMUM }
        .toSet()

/**
 * Sama persis -> true.
 * Mirip -> true jika kata kunci yang sama minimal 60% dari daftar kata kunci yang lebih pendek.
 * Contoh: "Ganti Oli" ~ "Ganti Oli Mesin" (cocok), "Servis Rem" ~ "Servis Berkala" (tidak cocok).
 */
fun namaLayananMirip(a: String, b: String): Boolean {
    if (normalisasiNama(a) == normalisasiNama(b)) return true
    val ka = kataKunci(a)
    val kb = kataKunci(b)
    if (ka.isEmpty() || kb.isEmpty()) return false
    val sama = ka.intersect(kb).size
    return sama.toDouble() / minOf(ka.size, kb.size) >= 0.6
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