package id.ac.stmi.mobileproject

import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.text.NumberFormat
import java.util.Locale

/** Kunci Intent untuk mengirim id layanan antar halaman. */
const val EXTRA_LAYANAN_ID = "layanan_id"

/** Kategori layanan = isi chip di halaman Layanan. Di Firestore disimpan sebagai nama enum (mis. "BERKALA"). */
enum class LayananKategori(val label: String) {
    BERKALA("Servis Berkala"),
    OLI("Oli & Pelumasan"),
    REM("Rem & Kaki-kaki"),
    AC("AC & Kabin"),
    SPOORING("Spooring")
}

/** Satu tahapan pengerjaan (ditampilkan di halaman Detail Layanan). */
data class LangkahKerja(val judul: String, val deskripsi: String)

/** Satu layanan servis. */
data class LayananItem(
    val id: String,
    val kategori: LayananKategori,
    val judul: String,
    val deskripsi: String,            // deskripsi singkat di kartu daftar
    val deskripsiDetail: String,      // deskripsi panjang di halaman detail
    val badge: String,
    val badge2: String? = null,       // badge kedua (opsional)
    val durasi: String,               // contoh: "30 - 45 Menit"
    val harga: Long,
    val hargaCoret: Long? = null,     // harga sebelum diskon (opsional)
    val mulaiDari: Boolean = false,   // true -> tampil "Mulai Rp ..."
    val cakupan: List<String> = emptyList(),
    val langkah: List<LangkahKerja>,
    val garansi: String = "Garansi 1.000 KM / 14 Hari",
    val sparepart: String = "Sparepart 100% OEM",
    val paket: String = "Paket Standar",
    val ikonKey: String = "build",    // kunci ikon di Firestore -> lihat IkonLayanan
    val urutan: Int = 0,              // urutan tampil di daftar (kecil = atas)
    val aktif: Boolean = true         // false = disembunyikan dari aplikasi
) {
    val hargaTampil: String
        get() = (if (mulaiDari) "Mulai " else "") + harga.keRupiah()

    /** Drawable untuk ikon (dipetakan dari ikonKey). */
    @get:DrawableRes
    val ikon: Int
        get() = IkonLayanan.resOf(ikonKey)

    /** Bentuk Map untuk ditulis ke Firestore. Tidak dipakai di app customer; berguna untuk form input petugas. */
    fun toMap(): Map<String, Any?> = mapOf(
        "kategori" to kategori.name,
        "judul" to judul,
        "deskripsi" to deskripsi,
        "deskripsiDetail" to deskripsiDetail,
        "badge" to badge,
        "badge2" to badge2,
        "durasi" to durasi,
        "harga" to harga,
        "hargaCoret" to hargaCoret,
        "mulaiDari" to mulaiDari,
        "cakupan" to cakupan,
        "langkah" to langkah.map { mapOf("judul" to it.judul, "deskripsi" to it.deskripsi) },
        "garansi" to garansi,
        "sparepart" to sparepart,
        "paket" to paket,
        "ikon" to ikonKey,
        "urutan" to urutan,
        "aktif" to aktif
    )
}

/** Pemetaan teks "ikon" di Firestore -> drawable. Tambah baris di sini kalau ada ikon baru. */
object IkonLayanan {
    private val peta = mapOf(
        "build" to R.drawable.ic_build,
        "drop" to R.drawable.ic_svc_drop,
        "shield" to R.drawable.ic_shield,
        "snow" to R.drawable.ic_svc_snow,
        "tune" to R.drawable.ic_tune
    )

    @DrawableRes
    fun resOf(key: String): Int = peta[key] ?: R.drawable.ic_build
}

/** 850000 -> "Rp 850.000" */
fun Long.keRupiah(): String =
    "Rp " + NumberFormat.getIntegerInstance(Locale("id", "ID")).format(this)

/**
 * Sumber data layanan = koleksi Firestore "layanan" (document id = id layanan).
 * Aplikasi customer HANYA MEMBACA; isinya diinput petugas. Dokumen dengan `aktif == false` disembunyikan.
 *
 * - [listenSemua]  : daftar layanan realtime (dipakai LayananActivity)
 * - [listenSatu]   : satu layanan realtime (dipakai DetailLayananActivity)
 * - [semua]/[find]/[cari] : membaca salinan terakhir di memori (diisi oleh listenSemua),
 *   jadi pemanggil lama tetap bisa dipakai tanpa query ulang.
 *
 * Listener berhenti otomatis saat Activity ditutup.
 */
object LayananCatalog {

    const val KOLEKSI = "layanan"

    /** Salinan terakhir dari Firestore (sudah disaring `aktif` dan diurutkan). */
    @Volatile
    var semua: List<LayananItem> = emptyList()
        private set

    private val db get() = FirebaseFirestore.getInstance()

    fun listenSemua(
        activity: AppCompatActivity,
        onError: (Exception) -> Unit = {},
        onResult: (List<LayananItem>) -> Unit
    ) {
        db.collection(KOLEKSI).addSnapshotListener(activity) { snap, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            val list = snap?.documents.orEmpty()
                .mapNotNull { it.keLayanan() }
                .sortedWith(compareBy<LayananItem> { it.urutan }.thenBy { it.judul })
            semua = list
            onResult(list)
        }
    }

    /** onResult(null) = dokumen memang tidak ada / sudah dinonaktifkan. */
    fun listenSatu(
        activity: AppCompatActivity,
        id: String,
        onError: (Exception) -> Unit = {},
        onResult: (LayananItem?) -> Unit
    ) {
        db.collection(KOLEKSI).document(id)
            .addSnapshotListener(activity) { snap, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                // Offline & belum pernah dimuat: jangan buru-buru dianggap "tidak ada"
                if (!snap.exists() && snap.metadata.isFromCache) return@addSnapshotListener
                onResult(snap.keLayanan())
            }
    }

    fun find(id: String): LayananItem? = semua.firstOrNull { it.id == id }

    /** Saring daftar berdasarkan kategori (null = semua) dan kata kunci pencarian. */
    fun cari(kategori: LayananKategori?, kataKunci: String): List<LayananItem> {
        val q = kataKunci.trim().lowercase()
        return semua.filter { item ->
            (kategori == null || item.kategori == kategori) &&
                    (q.isEmpty() ||
                            item.judul.lowercase().contains(q) ||
                            item.deskripsi.lowercase().contains(q) ||
                            item.kategori.label.lowercase().contains(q))
        }
    }

    /**
     * Dokumen Firestore -> LayananItem. Dibaca manual (bukan toObject) supaya
     * enum, list bersarang, dan field yang hilang tidak membuat aplikasi crash.
     * Mengembalikan null kalau dokumen tidak ada, `aktif == false`, atau judul kosong.
     */
    private fun DocumentSnapshot.keLayanan(): LayananItem? {
        if (!exists() || getBoolean("aktif") == false) return null
        val judul = getString("judul") ?: return null

        val kategori = getString("kategori")
            ?.let { nama -> LayananKategori.values().firstOrNull { it.name == nama } }
            ?: LayananKategori.BERKALA

        val langkah = (get("langkah") as? List<*>).orEmpty().mapNotNull { row ->
            val m = row as? Map<*, *> ?: return@mapNotNull null
            val j = m["judul"] as? String ?: return@mapNotNull null
            LangkahKerja(j, m["deskripsi"] as? String ?: "")
        }

        val default = LayananItem(
            id = id, kategori = kategori, judul = judul, deskripsi = "",
            deskripsiDetail = "", badge = "", durasi = "", harga = 0, langkah = emptyList()
        )

        return LayananItem(
            id = id,
            kategori = kategori,
            judul = judul,
            deskripsi = getString("deskripsi").orEmpty(),
            deskripsiDetail = getString("deskripsiDetail").orEmpty(),
            badge = getString("badge").orEmpty(),
            badge2 = getString("badge2")?.takeIf { it.isNotBlank() },
            durasi = getString("durasi").orEmpty(),
            harga = getLong("harga") ?: 0L,
            hargaCoret = getLong("hargaCoret"),
            mulaiDari = getBoolean("mulaiDari") ?: false,
            cakupan = (get("cakupan") as? List<*>).orEmpty().filterIsInstance<String>(),
            langkah = langkah,
            garansi = getString("garansi") ?: default.garansi,
            sparepart = getString("sparepart") ?: default.sparepart,
            paket = getString("paket") ?: default.paket,
            ikonKey = getString("ikon") ?: default.ikonKey,
            urutan = getLong("urutan")?.toInt() ?: 0,
            aktif = true
        )
    }
}

/**
 * Dengarkan mobil utama user (realtime). Kalau tidak ada yang bertanda "primary",
 * ambil mobil pertama. onResult(null) = belum punya mobil.
 * Listener berhenti otomatis saat Activity ditutup.
 */
fun AppCompatActivity.listenKendaraanUtama(uid: String, onResult: (Vehicle?) -> Unit) {
    FirebaseFirestore.getInstance()
        .collection("users").document(uid).collection("vehicles")
        .addSnapshotListener(this) { snap, error ->
            if (error != null) return@addSnapshotListener
            val list = snap?.documents.orEmpty().mapNotNull { doc ->
                doc.toObject(Vehicle::class.java)?.copy(id = doc.id)
            }
            onResult(list.firstOrNull { it.primary } ?: list.firstOrNull())
        }
}