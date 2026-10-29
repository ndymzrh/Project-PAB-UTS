package id.ac.stmi.mobileproject

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.text.NumberFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Satu transaksi servis yang sudah selesai (dasar perhitungan pendapatan). */
data class TransaksiPendapatan(
    val id: String,
    val tanggal: Date,
    val pelanggan: String,
    val plat: String,
    val layanan: String,
    val petugas: String,
    val subtotal: Long,
    val diskon: Long,
    val total: Long
)

/** Helper rentang tanggal (semua dalam zona waktu lokal perangkat). */
object RentangTanggal {
    fun awalHari(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun akhirHari(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999)
    }.timeInMillis

    fun hariIni(): Pair<Long, Long> {
        val n = System.currentTimeMillis()
        return awalHari(n) to akhirHari(n)
    }

    fun tujuhHari(): Pair<Long, Long> {
        val c = Calendar.getInstance()
        val akhir = akhirHari(c.timeInMillis)
        c.add(Calendar.DAY_OF_YEAR, -6)
        return awalHari(c.timeInMillis) to akhir
    }

    fun bulanIni(): Pair<Long, Long> {
        val c = Calendar.getInstance()
        val akhir = akhirHari(c.timeInMillis)
        c.set(Calendar.DAY_OF_MONTH, 1)
        return awalHari(c.timeInMillis) to akhir
    }

    fun bulanLalu(): Pair<Long, Long> {
        val c = Calendar.getInstance()
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.add(Calendar.MONTH, -1)
        val awal = awalHari(c.timeInMillis)
        c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH))
        return awal to akhirHari(c.timeInMillis)
    }

    /** MaterialDatePicker mengembalikan tengah malam UTC -> ubah ke tanggal lokal yang sama. */
    fun utcKeLokal(utcMillis: Long): Long {
        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
        return Calendar.getInstance().apply {
            clear()
            set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 12, 0)
        }.timeInMillis
    }
}

/**
 * Pengambilan data pendapatan dari Firestore koleksi "bookings".
 * Pendapatan = totalPrice dari booking berstatus selesai.
 *
 * Semua nama field/status ada di konstanta di bawah, sesuaikan jika berbeda.
 */
object PendapatanRepo {

    private const val COL_BOOKING = "bookings"
    private val STATUS_SELESAI = listOf("SELESAI")

    // Dicoba berurutan; yang pertama ada (Timestamp / angka milidetik) dipakai sebagai tanggal transaksi
    private val KEYS_TANGGAL = listOf("completedAt", "finishedAt", "updatedAt", "createdAt")
    private val KEYS_PELANGGAN = arrayOf("customerName", "namaPelanggan", "userName")
    private val KEYS_PLAT = arrayOf("vehiclePlate", "plateNumber", "plate", "platNomor")
    private val KEYS_LAYANAN = arrayOf("serviceNames", "services", "serviceName", "layanan")
    private val KEYS_PETUGAS = arrayOf("technicianName", "petugasName", "staffName")

    private val rupiahFmt: NumberFormat = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
        .apply { maximumFractionDigits = 0 }

    fun rupiah(n: Long): String = rupiahFmt.format(n)

    /**
     * Ambil transaksi selesai dalam rentang [awal, akhir] (milidetik).
     * Filter tanggal dilakukan di sisi aplikasi supaya tidak butuh index Firestore.
     */
    fun ambil(
        awal: Long,
        akhir: Long,
        onResult: (List<TransaksiPendapatan>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        FirebaseFirestore.getInstance().collection(COL_BOOKING)
            .whereIn("status", STATUS_SELESAI)
            .get()
            .addOnSuccessListener { snap ->
                val hasil = snap.documents.mapNotNull { d ->
                    val tgl = KEYS_TANGGAL.firstNotNullOfOrNull { toDate(d.get(it)) }
                        ?: return@mapNotNull null
                    if (tgl.time !in awal..akhir) return@mapNotNull null
                    val total = angka(d, "totalPrice")
                    TransaksiPendapatan(
                        id = d.id,
                        tanggal = tgl,
                        pelanggan = teks(d, *KEYS_PELANGGAN),
                        plat = teks(d, *KEYS_PLAT),
                        layanan = teks(d, *KEYS_LAYANAN),
                        petugas = teks(d, *KEYS_PETUGAS),
                        subtotal = angka(d, "subtotal").takeIf { it > 0 } ?: total,
                        diskon = angka(d, "discount"),
                        total = total
                    )
                }.sortedBy { it.tanggal }
                onResult(hasil)
            }
            .addOnFailureListener { onError(it) }
    }

    private fun toDate(v: Any?): Date? = when (v) {
        is Timestamp -> v.toDate()
        is Date -> v
        is Number -> Date(v.toLong())
        else -> null
    }

    private fun angka(d: DocumentSnapshot, key: String): Long =
        (d.get(key) as? Number)?.toLong() ?: 0L

    /** Ambil teks dari beberapa kemungkinan nama field; mendukung String atau List. */
    private fun teks(d: DocumentSnapshot, vararg keys: String): String {
        for (key in keys) {
            val v = d.get(key) ?: continue
            val s = when (v) {
                is String -> v
                is List<*> -> v.joinToString(", ") { item ->
                    if (item is Map<*, *>) (item["name"] ?: item["serviceName"] ?: "").toString()
                    else item.toString()
                }
                else -> v.toString()
            }
            if (s.isNotBlank()) return s
        }
        return ""
    }
}