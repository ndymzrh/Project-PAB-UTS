
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
        "gambar" to gambar
    )

    private fun DocumentSnapshot.toPromo(): PromoItem? {
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
                gambar = getString("gambar") ?: "bg_promo1"
            )
        } catch (e: Exception) {
            Log.e("PromoStore", "Error parsing promo document $id", e)
            null
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
                Log.e(
                    "PromoStore",
                    "Gagal mendengarkan promo dari Firestore",
                    err
                )
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
                            Log.d(
                                "PromoStore",
                                "Promo berhasil disimpan: $kodePromo"
                            )
                            onDone(Hasil.SUKSES, null)
                        }
                        .addOnFailureListener { error ->
                            Log.e(
                                "PromoStore",
                                "Gagal menyimpan promo: $kodePromo",
                                error
                            )
                            onDone(
                                Hasil.GAGAL,
                                error.localizedMessage
                                    ?: "Gagal menyimpan promo ke Firebase"
                            )
                        }
                }
            }
            .addOnFailureListener { error ->
                Log.e(
                    "PromoStore",
                    "Gagal memeriksa kode promo: $kodePromo",
                    error
                )
                onDone(
                    Hasil.GAGAL,
                    error.localizedMessage
                        ?: "Gagal memeriksa kode promo di Firebase"
                )
            }
    }

    fun hapus(
        ctx: Context,
        id: String,
        onDone: (Boolean) -> Unit
    ) {
        col.document(id).delete()
            .addOnSuccessListener {
                onDone(true)
            }
            .addOnFailureListener {
                Log.e("PromoStore", "Gagal menghapus promo", it)
                onDone(false)
            }
    }

    /**
     * Memperbarui empat promo bawaan berdasarkan kode.
     * Promo petugas dengan kode lain tidak disentuh.
     *
     * Catatan: field empat dokumen ini akan ditulis ulang.
     */
    fun seedSekali(ctx: Context) {
        val batch = db.batch()

        dataAwal().forEach { promo ->
            val kode = promo.kode.trim().uppercase()
            val ref = col.document(kode)

            val data = promo.copy(
                id = kode,
                kode = kode
            ).toMap()

            batch.set(ref, data)
        }

        batch.commit()
            .addOnSuccessListener {
                Log.d(
                    "PromoStore",
                    "Promo default berhasil diperbarui"
                )
            }
            .addOnFailureListener { error ->
                Log.e(
                    "PromoStore",
                    "Gagal memperbarui promo default",
                    error
                )
            }
    }

    private fun dataAwal(): List<PromoItem> {
        val now = System.currentTimeMillis()
        val mulai = now - (30L * 24 * 3600 * 1000)
        val berakhir1 = now + (90L * 24 * 3600 * 1000)
        val berakhir2 = now + (180L * 24 * 3600 * 1000)

        val syarat = "Berlaku di seluruh cabang Annova."

        return listOf(
            PromoItem(
                id = "SERVISHEMAT25",
                judul = "Diskon 25% Servis Berkala",
                kategori = KategoriPromo.SERVIS,
                kode = "SERVISHEMAT25",
                jenisDiskon = "Persen",
                nilai = 25,
                mulai = mulai,
                berakhir = berakhir1,
                berlaku = "Servis berkala",
                syarat = "Berlaku untuk kendaraan jenis mobil penumpang.\n" +
                        "Minimal transaksi servis Rp 200.000.\n" +
                        "Tidak dapat digabung dengan promo lain.\n" +
                        syarat,
                gambar = "bg_promo1"
            ),

            PromoItem(
                id = "POINSERVIS50",
                judul = "Voucher Servis Poin Loyalitas",
                kategori = KategoriPromo.POIN,
                kode = "POINSERVIS50",
                jenisDiskon = "Nominal",
                nilai = 50000,
                mulai = mulai,
                berakhir = berakhir1,
                berlaku = "Poin loyalitas",
                syarat = "Voucher senilai Rp 50.000 untuk transaksi servis.\n" +
                        "Ditukar dengan poin loyalitas yang tersedia.\n" +
                        "Tidak dapat diuangkan.\n" +
                        syarat,
                gambar = "bg_promo2"
            ),

            PromoItem(
                id = "REMTUNE10",
                judul = "Paket Kampas Rem & Tune-up",
                kategori = KategoriPromo.SPAREPART,
                kode = "REMTUNE10",
                jenisDiskon = "Persen",
                nilai = 10,
                mulai = mulai,
                berakhir = berakhir2,
                berlaku = "Kampas rem, tune-up",
                syarat = "Diskon 10% untuk paket kampas rem dan tune-up.\n" +
                        "Berlaku untuk kendaraan jenis mobil penumpang.\n" +
                        "Tidak dapat digabung dengan promo lain.\n" +
                        syarat,
                gambar = "bg_promo3"
            ),

            PromoItem(
                id = "CEKACGRATIS",
                judul = "Gratis Cek AC Mobil",
                kategori = KategoriPromo.SERVIS,
                kode = "CEKACGRATIS",
                jenisDiskon = "Nominal",
                nilai = 0,
                mulai = mulai,
                berakhir = berakhir2,
                berlaku = "Cek AC",
                syarat = "Pengecekan AC gratis tanpa minimal transaksi.\n" +
                        "Satu kali pemakaian per kendaraan.\n" +
                        "Tidak termasuk biaya perbaikan atau penggantian part.\n" +
                        syarat,
                gambar = "bg_promo4"
            )
        )
    }
}
