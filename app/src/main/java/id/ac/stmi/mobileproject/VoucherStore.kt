
package id.ac.stmi.mobileproject

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Voucher disimpan di koleksi Firestore "vouchers".
 * Document ID = UID pemilik + promoId.
 * Satu promo hanya dapat diklaim sekali oleh satu akun.
 */
object VoucherStore {

    private const val COLLECTION = "vouchers"

    enum class Hasil {
        SUKSES,
        SUDAH_DIKLAIM,
        BERAKHIR,
        GAGAL
    }

    private val db get() = FirebaseFirestore.getInstance()
    private val col get() = db.collection(COLLECTION)

    /**
     * Pemilik voucher menggunakan UID Firebase Authentication.
     */
    private fun pemilik(ctx: Context): String {
        return FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    }

    private fun idDokumen(ctx: Context, promoId: String): String {
        return "${pemilik(ctx)}_$promoId"
    }

    /**
     * Mengubah data voucher menjadi Map untuk Firestore.
     */
    private fun VoucherSaya.toMap(ctx: Context) = hashMapOf(
        "pemilik" to pemilik(ctx),
        "promoId" to promoId,
        "judul" to judul,
        "label" to label,
        "kode" to kode,
        "jenisDiskon" to jenisDiskon,
        "nilaiDiskon" to nilaiDiskon,
        "dipakai" to dipakai,
        "tglKlaim" to tglKlaim,
        "berakhir" to berakhir
    )

    /**
     * Membaca dokumen Firestore menjadi VoucherSaya.
     */
    private fun DocumentSnapshot.toVoucher(): VoucherSaya? {
        val promoId = getString("promoId") ?: return null

        return try {
            VoucherSaya(
                id = id,
                promoId = promoId,
                judul = getString("judul").orEmpty(),
                label = getString("label").orEmpty(),
                kode = getString("kode").orEmpty(),
                jenisDiskon = getString("jenisDiskon").orEmpty(),
                nilaiDiskon = getLong("nilaiDiskon") ?: 0L,
                dipakai = getBoolean("dipakai") ?: false,
                tglKlaim = getLong("tglKlaim") ?: 0L,
                berakhir = getLong("berakhir") ?: 0L
            )
        } catch (e: Exception) {
            Log.e("VoucherStore", "Gagal membaca voucher $id", e)
            null
        }
    }

    /**
     * Mendengarkan voucher milik akun yang sedang login.
     */
    fun listen(
        ctx: Context,
        onError: () -> Unit = {},
        onChange: (List<VoucherSaya>) -> Unit
    ): PromoListener {
        val uid = pemilik(ctx)

        if (uid.isBlank()) {
            onError()
            return PromoListener {}
        }

        val reg = col
            .whereEqualTo("pemilik", uid)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    Log.e("VoucherStore", "Gagal memuat voucher", err)
                    onError()
                    return@addSnapshotListener
                }

                val list = snap.documents
                    .mapNotNull { it.toVoucher() }
                    .sortedByDescending { it.tglKlaim }

                onChange(list)
            }

        return PromoListener { reg.remove() }
    }

    /**
     * Memeriksa apakah promo sudah diklaim oleh akun saat ini.
     */
    fun cekDiklaim(
        ctx: Context,
        promoId: String,
        onResult: (Boolean) -> Unit
    ) {
        if (pemilik(ctx).isBlank()) {
            onResult(false)
            return
        }

        col.document(idDokumen(ctx, promoId)).get()
            .addOnSuccessListener { snapshot ->
                onResult(snapshot.exists())
            }
            .addOnFailureListener { error ->
                Log.e(
                    "VoucherStore",
                    "Gagal memeriksa klaim promo $promoId",
                    error
                )
                onResult(false)
            }
    }

    /**
     * Mengklaim promo dan menyimpan data diskon numeriknya.
     */
    fun klaim(
        ctx: Context,
        p: PromoItem,
        onDone: (Hasil) -> Unit
    ) {
        if (pemilik(ctx).isBlank()) {
            onDone(Hasil.GAGAL)
            return
        }

        if (p.berakhir < System.currentTimeMillis()) {
            onDone(Hasil.BERAKHIR)
            return
        }

        val ref = col.document(idDokumen(ctx, p.id))

        val baru = VoucherSaya(
            id = ref.id,
            promoId = p.id,
            judul = p.judul,
            label = p.labelDiskon,
            kode = p.kode,
            jenisDiskon = p.jenisDiskon,
            nilaiDiskon = p.nilai,
            dipakai = false,
            tglKlaim = System.currentTimeMillis(),
            berakhir = p.berakhir
        )

        val map = baru.toMap(ctx)

        ref.get()
            .addOnSuccessListener { snap ->
                if (snap.exists()) {
                    onDone(Hasil.SUDAH_DIKLAIM)
                } else {
                    ref.set(map)
                        .addOnSuccessListener {
                            Log.d(
                                "VoucherStore",
                                "Voucher berhasil diklaim: ${p.kode}"
                            )
                            onDone(Hasil.SUKSES)
                        }
                        .addOnFailureListener { error ->
                            Log.e(
                                "VoucherStore",
                                "Gagal menyimpan voucher ${p.kode}",
                                error
                            )
                            onDone(Hasil.GAGAL)
                        }
                }
            }
            .addOnFailureListener { error ->
                Log.e(
                    "VoucherStore",
                    "Gagal memeriksa voucher ${p.kode}",
                    error
                )
                onDone(Hasil.GAGAL)
            }
    }

    /**
     * Tandai voucher sebagai terpakai setelah booking berhasil.
     */
    fun tandaiDipakai(ctx: Context, id: String) {
        ubah(ctx, id, true)
    }

    /**
     * Kembalikan status voucher jika booking dibatalkan.
     */
    fun kembalikan(ctx: Context, id: String) {
        ubah(ctx, id, false)
    }

    private fun ubah(
        ctx: Context,
        id: String,
        dipakai: Boolean
    ) {
        if (pemilik(ctx).isBlank()) return

        col.document(id)
            .update("dipakai", dipakai)
            .addOnFailureListener { error ->
                Log.e(
                    "VoucherStore",
                    "Gagal memperbarui status voucher $id",
                    error
                )
            }
    }

    /**
     * Mendengarkan promo yang sudah diklaim.
     * Dipakai agar promo tersebut tidak tampil lagi
     * pada daftar promo akun yang sedang login.
     */
    fun dengarkanPromoDiklaim(
        ctx: Context,
        onChange: (Set<String>) -> Unit,
        onError: () -> Unit = {}
    ): PromoListener {
        val uid = pemilik(ctx)

        if (uid.isBlank()) {
            onError()
            return PromoListener {}
        }

        val reg = col
            .whereEqualTo("pemilik", uid)
            .addSnapshotListener { snap, error ->
                if (error != null || snap == null) {
                    Log.e(
                        "VoucherStore",
                        "Gagal memuat promo yang sudah diklaim",
                        error
                    )
                    onError()
                    return@addSnapshotListener
                }

                val ids = snap.documents
                    .mapNotNull { it.getString("promoId") }
                    .toSet()

                onChange(ids)
            }

        return PromoListener { reg.remove() }
    }

    /**
     * Jumlah voucher yang masih tersedia.
     */
    fun jumlahTersedia(list: List<VoucherSaya>): Int {
        return list.count {
            it.status() == StatusVoucher.TERSEDIA
        }
    }
}
