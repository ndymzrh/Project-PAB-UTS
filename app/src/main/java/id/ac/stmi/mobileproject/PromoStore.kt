package id.ac.stmi.mobileproject

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Penanda listener agar pemanggil cukup memanggil .remove() di onStop.
 */
class PromoListener(private val stop: () -> Unit) {
    fun remove() = stop()
}

/**
 * Promo disimpan di Cloud Firestore, koleksi "promos".
 * Document ID = kode promo (uppercase).
 * Semua promo dibuat petugas lewat form tambah promo (tidak ada promo bawaan).
 *
 * Klaim / penukaran poin ada di VoucherStore.klaim().
 */
object PromoStore {

    private const val COLLECTION = "promos"

    enum class Hasil {
        SUKSES,
        KODE_ADA,
        GAGAL
    }

    private val db get() = FirebaseFirestore.getInstance()
    private val col get() = db.collection(COLLECTION)

    private fun PromoItem.toMap() = hashMapOf(
        "judul" to judul,
        "kategori" to kategori.name,
        "kode" to kode,
        "jenis" to jenisDiskon,
        "nilai" to nilai,
        "mulai" to mulai,
        "berakhir" to berakhir,
        "berlaku" to berlaku,
        "syarat" to syarat,
        "gambar" to gambar,
        "layananIds" to layananIds,
        "poinDibutuhkan" to poinDibutuhkan
    )

    private fun DocumentSnapshot.toPromo(): PromoItem? {
        if (!exists()) return null
        return try {
            val kategoriStr = getString("kategori") ?: return null

            val kategori = try {
                KategoriPromo.valueOf(kategoriStr)
            } catch (e: Exception) {
                KategoriPromo.values().firstOrNull {
                    it.name.equals(kategoriStr, ignoreCase = true) ||
                            it.label.equals(kategoriStr, ignoreCase = true)
                } ?: KategoriPromo.SERVIS
            }

            PromoItem(
                id = id,
                judul = getString("judul").orEmpty(),
                kategori = kategori,
                kode = getString("kode").orEmpty(),
                jenisDiskon = getString("jenis").orEmpty(),
                nilai = getLong("nilai") ?: 0L,
                mulai = getLong("mulai") ?: 0L,
                berakhir = getLong("berakhir") ?: 0L,
                berlaku = getString("berlaku").orEmpty(),
                syarat = getString("syarat").orEmpty(),
                gambar = getString("gambar") ?: "bg_promo1",
                layananIds = (get("layananIds") as? List<*>).orEmpty().filterIsInstance<String>(),
                poinDibutuhkan = getLong("poinDibutuhkan") ?: 0L
            )
        } catch (e: Exception) {
            Log.e("PromoStore", "Error parsing promo document $id", e)
            null
        }
    }

    /** Dipakai VoucherStore untuk membaca promo di dalam transaction. */
    fun dariDokumen(d: DocumentSnapshot): PromoItem? = d.toPromo()

    fun seedSekali(ctx: Context) {
        col.limit(1).get().addOnSuccessListener { snap ->
            if (snap.isEmpty) {
                val now = System.currentTimeMillis()
                val oneYearLater = now + 365L * 24 * 60 * 60 * 1000
                val defaultPromos = listOf(
                    PromoItem(
                        id = "SERVISHEMAT",
                        judul = "Servis Berkala Hemat",
                        kategori = KategoriPromo.SERVIS,
                        kode = "SERVISHEMAT",
                        jenisDiskon = "Persen",
                        nilai = 20L,
                        mulai = now,
                        berakhir = oneYearLater,
                        berlaku = "Semua Servis Berkala",
                        syarat = "Minimum transaksi Rp 200.000",
                        gambar = "bg_promo1",
                        poinDibutuhkan = 0L
                    ),
                    PromoItem(
                        id = "DISKONOLI",
                        judul = "Diskon Oli Mesin",
                        kategori = KategoriPromo.SPAREPART,
                        kode = "DISKONOLI",
                        jenisDiskon = "Nominal",
                        nilai = 50000L,
                        mulai = now,
                        berakhir = oneYearLater,
                        berlaku = "Pembelian Oli",
                        syarat = "Berlaku untuk semua jenis oli",
                        gambar = "bg_promo3",
                        poinDibutuhkan = 0L
                    ),
                    PromoItem(
                        id = "POINHEMAT",
                        judul = "Voucher Poin 10%",
                        kategori = KategoriPromo.POIN,
                        kode = "POINHEMAT",
                        jenisDiskon = "Persen",
                        nilai = 10L,
                        mulai = now,
                        berakhir = oneYearLater,
                        berlaku = "Semua Servis",
                        syarat = "Tukar dengan 100 poin",
                        gambar = "bg_promo2",
                        poinDibutuhkan = 100L
                    )
                )
                for (p in defaultPromos) {
                    col.document(p.id).set(p.toMap())
                }
            }
        }
    }

    /**
     * Panggil di onStart, lalu .remove() di onStop.
     * Langsung mengirim data terbaru saat ada perubahan.
     */
    fun listen(
        ctx: Context,
        onChange: (List<PromoItem>) -> Unit,
        onError: () -> Unit = {}
    ): PromoListener {
        val reg = col.addSnapshotListener { snap, err ->
            if (err != null || snap == null) {
                Log.e("PromoStore", "Gagal mendengarkan promo dari Firestore", err)
                onError()
                return@addSnapshotListener
            }

            val list = snap.documents
                .mapNotNull { it.toPromo() }
                .sortedByDescending { it.mulai }

            onChange(list)
        }

        return PromoListener { reg.remove() }
    }

    fun get(
        ctx: Context,
        id: String,
        onResult: (PromoItem?) -> Unit
    ) {
        col.document(id).get()
            .addOnSuccessListener {
                onResult(if (it.exists()) it.toPromo() else null)
            }
            .addOnFailureListener {
                Log.e("PromoStore", "Gagal mengambil promo $id", it)
                onResult(null)
            }
    }

    fun tambah(
        ctx: Context,
        p: PromoItem,
        onDone: (Hasil, String?) -> Unit
    ) {
        val kodePromo = p.kode.trim().uppercase()

        if (kodePromo.isBlank()) {
            onDone(Hasil.GAGAL, "Kode promo tidak boleh kosong")
            return
        }

        val ref = col.document(kodePromo)
        val data = p.copy(
            id = kodePromo,
            kode = kodePromo
        ).toMap()

        ref.get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    onDone(Hasil.KODE_ADA, "Kode promo sudah dipakai")
                } else {
                    ref.set(data)
                        .addOnSuccessListener {
                            Log.d("PromoStore", "Promo berhasil disimpan: $kodePromo")
                            onDone(Hasil.SUKSES, null)
                        }
                        .addOnFailureListener { error ->
                            Log.e("PromoStore", "Gagal menyimpan promo: $kodePromo", error)
                            onDone(
                                Hasil.GAGAL,
                                error.localizedMessage ?: "Gagal menyimpan promo ke Firebase"
                            )
                        }
                }
            }
            .addOnFailureListener { error ->
                Log.e("PromoStore", "Gagal memeriksa kode promo: $kodePromo", error)
                onDone(
                    Hasil.GAGAL,
                    error.localizedMessage ?: "Gagal memeriksa kode promo di Firebase"
                )
            }
    }

    fun hapus(
        ctx: Context,
        id: String,
        onDone: (Boolean) -> Unit
    ) {
        col.document(id).delete()
            .addOnSuccessListener { onDone(true) }
            .addOnFailureListener {
                Log.e("PromoStore", "Gagal menghapus promo", it)
                onDone(false)
            }
    }
}