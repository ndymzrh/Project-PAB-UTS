package id.ac.stmi.mobileproject

import android.app.Activity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Membuat notifikasi dari perubahan status booking, DI SISI PELANGGAN.
 *
 * Alur:
 *   petugas ubah status booking  ->  dokumen bookings/{id} berubah
 *   aplikasi pelanggan (yang memantau bookings miliknya) menerima perubahan itu
 *   -> menulis notifikasi ke users/{uid sendiri}/notifications
 *
 * Petugas TIDAK menulis notifikasi apa pun. Pelanggan hanya menulis ke datanya sendiri,
 * jadi aturan keamanan Firestore tidak perlu diubah.
 *
 * Dipanggil dari setupTopBar() (Beranda, Promo, Akun) dan NotificationActivity.
 * Listener otomatis berhenti saat halaman tidak tampil.
 */
object BookingNotificationSync {

    // Hanya booking yang berubah dalam 7 hari terakhir yang dijadikan notifikasi,
    // supaya riwayat lama tidak membanjiri halaman notifikasi.
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    fun start(activity: Activity) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        activity.observeWhileStarted {
            db.collection(BookingRepository.COLLECTION)
                .whereEqualTo("customerId", uid)
                .addSnapshotListener { snap, error ->
                    if (error != null || snap == null) return@addSnapshotListener
                    snap.documents.forEach { sync(db, uid, it) }
                }
        }
    }

    private fun sync(db: FirebaseFirestore, uid: String, doc: DocumentSnapshot) {
        val status = runCatching { BookingStatus.valueOf(doc.getString("status").orEmpty()) }
            .getOrNull() ?: return
        if (status == BookingStatus.MENUNGGU_KONFIRMASI) return // belum ada kabar apa pun

        val updatedAt = doc.getTimestamp("updatedAt")
        if (updatedAt != null && System.currentTimeMillis() - updatedAt.toDate().time > MAX_AGE_MS) return

        val namaKendaraan = doc.getString("vehicleName").orEmpty()
        val plat = doc.getString("plateNumber").orEmpty()
        val kendaraan = when {
            namaKendaraan.isBlank() -> "Kendaraan Anda"
            plat.isBlank() -> namaKendaraan
            else -> "$namaKendaraan ($plat)"
        }
        val jadwal = doc.getString("bookingDate").orEmpty() + ", " + doc.getString("bookingTime").orEmpty()
        val poin = (doc.get("awardedPoints") as? Number)?.toLong() ?: 0L

        val isi = isiNotifikasi(status, doc.id, kendaraan, jadwal, poin) ?: return

        // ID tetap per booking+status => satu status hanya pernah menghasilkan satu notifikasi
        val ref = db.collection("users").document(uid)
            .collection("notifications")
            .document("status_${doc.id}_${status.name}")

        db.runTransaction { tx ->
            // Sudah ada (termasuk yang sudah dibaca / dihapus pelanggan) -> jangan dibuat lagi
            if (!tx.get(ref).exists()) {
                val data = HashMap(isi)
                // Waktu notifikasi = waktu petugas mengubah status
                data["waktu"] = updatedAt ?: FieldValue.serverTimestamp()
                data["dibaca"] = false
                data["dihapus"] = false
                tx.set(ref, data)
            }
            null
        }
    }

    /** judul, isi, kategori, aksi. null = tidak perlu notifikasi. */
    private fun isiNotifikasi(
        status: BookingStatus, bookingId: String, kendaraan: String, jadwal: String, poin: Long
    ): Map<String, Any>? {
        fun n(judul: String, isi: String, kategori: String, aksiLabel: String, aksiTarget: String) =
            mapOf<String, Any>(
                "judul" to judul, "isi" to isi, "kategori" to kategori,
                "aksi_label" to aksiLabel, "aksi_target" to aksiTarget
            )

        return when (status) {
            BookingStatus.MENUNGGU_KONFIRMASI -> null
            BookingStatus.DIKONFIRMASI -> n(
                "Booking Servis Dikonfirmasi!",
                "Jadwal servis $kendaraan pada $jadwal telah disetujui bengkel. Kode Booking: #$bookingId.",
                "booking", "Lihat Detail Booking →", "booking"
            )
            BookingStatus.SEDANG_DISERVIS -> n(
                "Mobil Sedang Dikerjakan (Servis Berjalan)",
                "$kendaraan sedang dalam proses pengerjaan oleh teknisi kami.",
                "status_servis", "Lihat Status Servis →", "status_servis"
            )
            BookingStatus.SELESAI -> n(
                "Servis Selesai & Kendaraan Siap Diambil",
                "Pengerjaan servis $kendaraan telah rampung." +
                        if (poin > 0L) " Anda mendapat $poin poin." else "",
                "servis_selesai", "Rincian Tagihan & Garansi →", "riwayat"
            )
            BookingStatus.DITOLAK -> n(
                "Booking Ditolak",
                "Maaf, booking #$bookingId untuk $kendaraan tidak dapat diproses oleh bengkel.",
                "booking", "", ""
            )
            BookingStatus.DIBATALKAN -> n(
                "Booking Dibatalkan",
                "Booking #$bookingId untuk $kendaraan telah dibatalkan.",
                "booking", "", ""
            )
        }
    }
}