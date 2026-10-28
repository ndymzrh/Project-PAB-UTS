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
 *
 * Untuk promo kategori POIN, klaim memotong saldo poin customer
 * (users/{uid}.points) dan mencatat riwayat poin keluar
 * (users/{uid}/pointsTransactions, type = "REDEEM").
 */
object VoucherStore {

    private const val COLLECTION = "vouchers"

    enum class Hasil {
        SUKSES,
        SUDAH_DIKLAIM,
        BERAKHIR,
        POIN_KURANG,
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
        "berakhir" to berakhir,
        "layananIds" to layananIds
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
                berakhir = getLong("berakhir") ?: 0L,
                layananIds = (get("layananIds") as? List<*>).orEmpty().filterIsInstance<String>()
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
                Log.e("VoucherStore", "Gagal memeriksa klaim promo $promoId", error)
                onResult(false)
            }
    }

    /**
     * Mengklaim promo.
     *
     * Semuanya dalam satu transaction (atomik):
     *  1. Baca data promo TERBARU dari Firestore (bukan dari objek di layar).
     *  2. Tolak jika promo sudah berakhir atau voucher sudah pernah diklaim.
     *  3. Jika promo kategori POIN: cek saldo poin customer.
     *     Poin kurang -> POIN_KURANG, tidak ada data yang berubah.
     *  4. Simpan voucher; untuk promo POIN, kurangi saldo dan catat riwayat poin keluar.
     */
    fun klaim(
        ctx: Context,
        p: PromoItem,
        onDone: (Hasil) -> Unit
    ) {
        val uid = pemilik(ctx)
        if (uid.isBlank()) {
            onDone(Hasil.GAGAL)
            return
        }

        val promoRef = db.collection("promos").document(p.id)
        val userRef = db.collection("users").document(uid)
        val voucherRef = col.document(idDokumen(ctx, p.id))
        val riwayatRef = userRef.collection("pointsTransactions").document()

        db.runTransaction { trx ->
            // Semua READ dulu, baru WRITE (aturan Firestore transaction).
            val promoSnap = trx.get(promoRef)
            val voucherSnap = trx.get(voucherRef)
            val userSnap = trx.get(userRef)

            val promo = PromoStore.dariDokumen(promoSnap)
                ?: return@runTransaction Hasil.GAGAL

            val now = System.currentTimeMillis()
            if (promo.berakhir < now) {
                return@runTransaction Hasil.BERAKHIR
            }
            if (voucherSnap.exists()) {
                return@runTransaction Hasil.SUDAH_DIKLAIM
            }

            val butuhPoin = promo.bisaDitukarPoin
            val saldo = userSnap.getLong("points") ?: 0L

            if (butuhPoin && saldo < promo.poinDibutuhkan) {
                return@runTransaction Hasil.POIN_KURANG
            }

            val baru = VoucherSaya(
                id = voucherRef.id,
                promoId = promo.id,
                judul = promo.judul,
                label = promo.labelDiskon,
                kode = promo.kode,
                jenisDiskon = promo.jenisDiskon,
                nilaiDiskon = promo.nilai,
                dipakai = false,
                tglKlaim = now,
                berakhir = promo.berakhir,
                layananIds = promo.layananIds
            )
            trx.set(voucherRef, baru.toMap(ctx))

            if (butuhPoin) {
                trx.update(userRef, "points", saldo - promo.poinDibutuhkan)
                trx.set(
                    riwayatRef, hashMapOf(
                        "type" to "REDEEM",
                        "points" to -promo.poinDibutuhkan,
                        "description" to "Penukaran voucher ${promo.judul}",
                        "bookingId" to "",
                        "createdAt" to now
                    )
                )
            }

            Hasil.SUKSES
        }.addOnSuccessListener { hasil ->
            onDone(hasil)
        }.addOnFailureListener { error ->
            Log.e("VoucherStore", "Gagal mengklaim voucher ${p.kode}", error)
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
                Log.e("VoucherStore", "Gagal memperbarui status voucher $id", error)
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
                    Log.e("VoucherStore", "Gagal memuat promo yang sudah diklaim", error)
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