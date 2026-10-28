package id.ac.stmi.mobileproject

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

enum class BookingStatus(val label: String) {
    MENUNGGU_KONFIRMASI("Menunggu konfirmasi"),
    DIKONFIRMASI("Dikonfirmasi"),
    SEDANG_DISERVIS("Sedang diservis"),
    SELESAI("Selesai"),
    DITOLAK("Ditolak"),
    DIBATALKAN("Dibatalkan");

    /** Status akhir: setelah ini booking tidak bisa diubah lagi oleh petugas. */
    val isFinal: Boolean
        get() = this == SELESAI || this == DITOLAK || this == DIBATALKAN
}

enum class PointsStatus {
    PENDING,
    AWARDED,
    REVERSED,
    NOT_ELIGIBLE
}

data class BookingServiceLine(
    val id: String,
    val name: String,
    val price: Long,
    val duration: String
)

data class BookingDraft(
    val vehicleId: String,
    val vehicleName: String,
    val plateNumber: String,
    val serviceIds: List<String>,
    val workshopId: String,
    val workshopName: String,
    val bookingDate: String,
    val bookingTime: String,
    val notes: String,
    val voucherId: String?,
    val requestedDiscount: Long,   // diskon voucher saja
    val pointsUsed: Long = 0L      // BARU: jumlah poin yang dipakai
)

data class BookingRecord(
    val id: String,
    val customerId: String,
    val customerName: String,
    val vehicleId: String,
    val vehicleName: String,
    val plateNumber: String,
    val services: List<BookingServiceLine>,
    val workshopId: String,
    val workshopName: String,
    val bookingDate: String,
    val bookingTime: String,
    val notes: String,
    val subtotal: Long,
    val voucherId: String?,
    val discount: Long,
    val totalPrice: Long,
    val pointsRate: Double,
    val estimatedPoints: Long,
    val awardedPoints: Long,
    val pointsStatus: PointsStatus,
    val status: BookingStatus,
    val createdAt: Timestamp?,
    val updatedAt: Timestamp?,
    val completedAt: Timestamp?,
    val pointsAwardedAt: Timestamp?
) {
    val serviceNames: String
        get() = services.joinToString(", ") { it.name }

    val scheduleText: String
        get() = "$bookingDate, $bookingTime"

    val totalPriceText: String
        get() = totalPrice.keRupiah()

    val estimatedPointsText: String
        get() = NumberFormat.getIntegerInstance(Locale("id", "ID")).format(estimatedPoints)
}

data class PointsTransaction(
    val id: String,
    val customerId: String,
    val bookingId: String,
    val type: String,
    val points: Long,
    val description: String,
    val createdAt: Timestamp?
)

object BookingRepository {
    const val COLLECTION = "bookings"
    const val POINTS_RATE = 0.10

    /** 1 poin = Rp1. Samakan dengan POINT_VALUE di BookingActivityCustomer. */
    const val POINT_VALUE = 1L

    private val db get() = FirebaseFirestore.getInstance()

    /** Hitung diskon voucher. Dipakai di layar booking (tampilan) dan di create() (validasi server). */
    fun hitungDiskonVoucher(jenis: String, nilai: Long, label: String, subtotal: Long): Long {
        val labelBersih = label.trim()
        val persen = jenis == "Persen" || (jenis.isBlank() && labelBersih.contains("%"))
        val angka = if (nilai > 0L) nilai else labelBersih.filter { it.isDigit() }.toLongOrNull() ?: 0L
        val diskon = if (persen) {
            if (angka >= 100L) subtotal else subtotal * angka / 100L
        } else angka
        return diskon.coerceIn(0L, subtotal)
    }

    fun newBookingId(): String = "BK-${UUID.randomUUID().toString().replace("-", "").take(10).uppercase()}"

    fun estimatedPoints(totalPrice: Long): Long =
        kotlin.math.round(totalPrice.coerceAtLeast(0L) * POINTS_RATE).toLong()

    fun listenCustomerBookings(
        uid: String,
        onResult: (List<BookingRecord>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration =
        db.collection(COLLECTION)
            .whereEqualTo("customerId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                val bookings = snapshot?.documents.orEmpty()
                    .mapNotNull { document ->
                        runCatching { document.toBookingRecord() }.getOrNull()
                    }
                    .sortedWith(
                        compareByDescending<BookingRecord> { it.isActive() }
                            .thenByDescending { it.createdAt?.toDate()?.time ?: 0L }
                    )
                onResult(bookings)
            }

    fun listenAllBookings(
        onResult: (List<BookingRecord>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration =
        db.collection(COLLECTION)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                val bookings = snapshot?.documents.orEmpty()
                    .mapNotNull { document ->
                        runCatching { document.toBookingRecord() }.getOrNull()
                    }
                    .sortedWith(
                        compareByDescending<BookingRecord> { it.isActive() }
                            .thenByDescending { it.createdAt?.toDate()?.time ?: 0L }
                    )
                onResult(bookings)
            }

    fun updateStatus(
        bookingId: String,
        status: BookingStatus,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val bookingRef = db.collection(COLLECTION).document(bookingId)

        db.runTransaction { tx ->
            // ===== READ DULU =====
            val booking = tx.get(bookingRef)
            val customerId = booking.getString("customerId").orEmpty()
            val sudahDiberi = booking.getString("pointsStatus") == PointsStatus.AWARDED.name
            val estimasi = (booking.get("estimatedPoints") as? Number)?.toLong() ?: 0L

            val voucherId = booking.getString("voucherId")
            val statusLama = booking.getString("status")
            // Booking yang sudah selesai / ditolak / dibatalkan tidak boleh diubah lagi
            // (juga mencegah poin dan voucher diproses dua kali)
            val statusLamaEnum = runCatching { BookingStatus.valueOf(statusLama.orEmpty()) }.getOrNull()
            if (statusLamaEnum?.isFinal == true) {
                throw IllegalStateException(
                    "Booking sudah ${statusLamaEnum.label.lowercase()}, status tidak bisa diubah lagi."
                )
            }
            val kembalikanVoucher =
                (status == BookingStatus.DIBATALKAN || status == BookingStatus.DITOLAK) &&
                        !voucherId.isNullOrBlank() &&
                        statusLama != BookingStatus.DIBATALKAN.name &&
                        statusLama != BookingStatus.DITOLAK.name

            val beriPoin = status == BookingStatus.SELESAI &&
                    !sudahDiberi && estimasi > 0L && customerId.isNotBlank()

            val userRef = if (beriPoin) db.collection("users").document(customerId) else null
            val saldo = if (userRef != null) {
                (tx.get(userRef).get("points") as? Number)?.toLong() ?: 0L
            } else 0L

            // ===== WRITE =====
            val updates = hashMapOf<String, Any>(
                "status" to status.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            if (status == BookingStatus.SELESAI) {
                updates["completedAt"] = FieldValue.serverTimestamp()
            }

            if (beriPoin && userRef != null) {
                tx.update(userRef, "points", saldo + estimasi)
                tx.set(
                    db.collection("points_transactions").document("earn_$bookingId"),
                    hashMapOf(
                        "customerId" to customerId,
                        "bookingId" to bookingId,
                        "type" to "EARN",
                        "points" to estimasi,
                        "description" to "Poin dari servis selesai",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
                updates["awardedPoints"] = estimasi
                updates["pointsStatus"] = PointsStatus.AWARDED.name
                updates["pointsAwardedAt"] = FieldValue.serverTimestamp()
            }

            // Booking dibatalkan/ditolak: voucher kembali ke tab "Tersedia"
            if (kembalikanVoucher && voucherId != null) {
                tx.update(
                    db.collection("vouchers").document(voucherId),
                    "dipakai", false
                )
            }

            tx.update(bookingRef, updates)
            null
        }.addOnSuccessListener { onSuccess() }
            .addOnFailureListener(onError)
    }

    fun create(
        uid: String,
        bookingId: String,
        draft: BookingDraft,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val bookingRef = db.collection(COLLECTION).document(bookingId)
        val userRef = db.collection("users").document(uid)
        val vehicleRef = db.collection("users").document(uid).collection("vehicles").document(draft.vehicleId)
        val workshopRef = db.collection("bengkel").document(draft.workshopId)
        val serviceRefs = draft.serviceIds.map { db.collection("layanan").document(it) }
        val redeemRef = db.collection("points_transactions").document("redeem_$bookingId")
        val voucherRef = draft.voucherId?.takeIf { it.isNotBlank() }
            ?.let { db.collection("vouchers").document(it) }

        db.runTransaction { transaction ->
            // ===== SEMUA READ DULU =====
            val existingBooking = transaction.get(bookingRef)
            if (existingBooking.exists()) return@runTransaction null

            val userSnapshot = transaction.get(userRef)
            val customerName = userSnapshot.getString("nama")
                ?: userSnapshot.getString("name")
                ?: "Customer"
            val saldoPoin = (userSnapshot.get("points") as? Number)?.toLong() ?: 0L

            val vehicle = transaction.get(vehicleRef)
            require(vehicle.exists()) { "Kendaraan tidak ditemukan." }

            val workshop = transaction.get(workshopRef)
            require(workshop.exists() && workshop.getBoolean("aktif") != false) {
                "Bengkel tidak tersedia."
            }

            val serviceSnapshots = serviceRefs.map { transaction.get(it) }
            require(serviceSnapshots.all { it.exists() && it.getBoolean("aktif") != false }) {
                "Ada layanan yang sudah tidak tersedia. Silakan pilih ulang."
            }

            val voucherSnap = voucherRef?.let { transaction.get(it) }

            val lines = serviceSnapshots.map { service ->
                mapOf(
                    "id" to service.id,
                    "nama" to (service.getString("nama") ?: service.getString("judul") ?: "Layanan"),
                    "harga" to ((service.get("harga") as? Number)?.toLong() ?: 0L),
                    "durasi" to service.get("durasi").toDisplayDuration()
                )
            }
            val subtotal = lines.sumOf { (it["harga"] as Number).toLong() }

            // ===== HITUNG DISKON: voucher dulu, lalu poin =====
            var voucherDiscount = 0L
            if (voucherSnap != null) {
                require(voucherSnap.exists() && voucherSnap.getString("pemilik") == uid) {
                    "Voucher tidak valid."
                }
                require(voucherSnap.getBoolean("dipakai") != true) { "Voucher sudah dipakai." }
                require((voucherSnap.getLong("berakhir") ?: 0L) >= System.currentTimeMillis()) {
                    "Voucher sudah kedaluwarsa."
                }

                // Voucher khusus layanan tertentu: hanya berlaku untuk layanan yang cocok
                val idBerlaku = (voucherSnap.get("layananIds") as? List<*>).orEmpty()
                    .filterIsInstance<String>()
                val dasarDiskon = if (idBerlaku.isEmpty()) {
                    subtotal
                } else {
                    require(lines.any { (it["id"] as String) in idBerlaku }) {
                        "Voucher ini tidak berlaku untuk layanan yang dipilih."
                    }
                    lines.filter { (it["id"] as String) in idBerlaku }
                        .sumOf { (it["harga"] as Number).toLong() }
                }

                voucherDiscount = hitungDiskonVoucher(
                    voucherSnap.getString("jenisDiskon").orEmpty(),
                    voucherSnap.getLong("nilaiDiskon") ?: 0L,
                    voucherSnap.getString("label").orEmpty(),
                    dasarDiskon
                )
            }
            val sisaSetelahVoucher = subtotal - voucherDiscount

            val poinDiminta = draft.pointsUsed.coerceAtLeast(0L)
            require(poinDiminta <= saldoPoin) { "Saldo poin tidak cukup." }
            val poinMaksimal = sisaSetelahVoucher / POINT_VALUE
            val poinDipakai = minOf(poinDiminta, poinMaksimal)
            val pointDiscount = poinDipakai * POINT_VALUE

            val discount = voucherDiscount + pointDiscount
            val total = (subtotal - discount).coerceAtLeast(0L)
            val estimated = estimatedPoints(total)
            val now = FieldValue.serverTimestamp()

            val data = hashMapOf<String, Any?>(
                "bookingId" to bookingId,
                "customerId" to uid,
                "customerName" to customerName,
                "vehicleId" to draft.vehicleId,
                "vehicleName" to (vehicle.getString("brand").orEmpty() + " " + vehicle.getString("model").orEmpty()).trim(),
                "plateNumber" to (vehicle.getString("plateNumber") ?: draft.plateNumber),
                "serviceIds" to serviceSnapshots.map { it.id },
                "serviceNames" to lines.map { it["nama"] as String },
                "serviceDetails" to lines,
                "workshopId" to draft.workshopId,
                "workshopName" to (workshop.getString("nama") ?: draft.workshopName),
                "bookingDate" to draft.bookingDate,
                "bookingTime" to draft.bookingTime,
                "notes" to draft.notes,
                "subtotal" to subtotal,
                "voucherId" to draft.voucherId,
                "discount" to discount,
                "pointsUsed" to poinDipakai,
                "totalPrice" to total,
                "pointsRate" to POINTS_RATE,
                "estimatedPoints" to estimated,
                "awardedPoints" to 0L,
                "pointsStatus" to PointsStatus.PENDING.name,
                "status" to BookingStatus.MENUNGGU_KONFIRMASI.name,
                "createdAt" to now,
                "updatedAt" to now,
                "completedAt" to null,
                "pointsAwardedAt" to null
            )

            // ===== SEMUA WRITE (atomic) =====
            transaction.set(bookingRef, data)

            // Voucher langsung berpindah ke tab "Terpakai" (atomic bersama booking)
            if (voucherRef != null) {
                transaction.update(
                    voucherRef,
                    mapOf(
                        "dipakai" to true,
                        "bookingId" to bookingId,
                        "tglDipakai" to System.currentTimeMillis()
                    )
                )
            }

            if (poinDipakai > 0L) {
                transaction.update(userRef, "points", saldoPoin - poinDipakai)
                transaction.set(
                    redeemRef,
                    hashMapOf(
                        "customerId" to uid,
                        "bookingId" to bookingId,
                        "type" to "REDEEM",
                        "points" to -poinDipakai,   // negatif -> tampil "-500" di riwayat
                        "description" to "Dipakai untuk booking servis",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            null
        }.addOnSuccessListener { onSuccess(bookingId) }
            .addOnFailureListener(onError)
    }

    fun listenOne(
        uid: String,
        bookingId: String,
        onResult: (BookingRecord?) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration =
        db.collection(COLLECTION).document(bookingId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) {
                    onResult(null)
                    return@addSnapshotListener
                }
                if (snapshot.getString("customerId") != uid) {
                    onError(IllegalAccessException("Booking bukan milik customer ini."))
                    return@addSnapshotListener
                }
                onResult(snapshot.toBookingRecord())
            }

    private fun Any?.toDisplayDuration(): String = when (this) {
        is Number -> if (toLong() > 0) "${toLong()} Menit" else ""
        is String -> this
        else -> ""
    }

    private fun DocumentSnapshot.toBookingRecord(): BookingRecord {
        val details = (get("serviceDetails") as? List<*>).orEmpty().mapNotNull { raw ->
            val map = raw as? Map<*, *> ?: return@mapNotNull null
            BookingServiceLine(
                id = map["id"] as? String ?: "",
                name = map["nama"] as? String ?: "Layanan",
                price = (map["harga"] as? Number)?.toLong() ?: 0L,
                duration = map["durasi"] as? String ?: ""
            )
        }
        return BookingRecord(
            id = id,
            customerId = getString("customerId").orEmpty(),
            customerName = getString("customerName") ?: getString("customerId").orEmpty(),
            vehicleId = getString("vehicleId").orEmpty(),
            vehicleName = getString("vehicleName").orEmpty(),
            plateNumber = getString("plateNumber").orEmpty(),
            services = details,
            workshopId = getString("workshopId").orEmpty(),
            workshopName = getString("workshopName").orEmpty(),
            bookingDate = getString("bookingDate").orEmpty(),
            bookingTime = getString("bookingTime").orEmpty(),
            notes = getString("notes").orEmpty(),
            subtotal = (get("subtotal") as? Number)?.toLong() ?: 0L,
            voucherId = getString("voucherId"),
            discount = (get("discount") as? Number)?.toLong() ?: 0L,
            totalPrice = (get("totalPrice") as? Number)?.toLong() ?: 0L,
            pointsRate = (get("pointsRate") as? Number)?.toDouble() ?: POINTS_RATE,
            estimatedPoints = (get("estimatedPoints") as? Number)?.toLong() ?: 0L,
            awardedPoints = (get("awardedPoints") as? Number)?.toLong() ?: 0L,
            pointsStatus = runCatching { PointsStatus.valueOf(getString("pointsStatus").orEmpty()) }
                .getOrDefault(PointsStatus.PENDING),
            status = runCatching { BookingStatus.valueOf(getString("status").orEmpty()) }
                .getOrDefault(BookingStatus.MENUNGGU_KONFIRMASI),
            createdAt = getTimestamp("createdAt"),
            updatedAt = getTimestamp("updatedAt"),
            completedAt = getTimestamp("completedAt"),
            pointsAwardedAt = getTimestamp("pointsAwardedAt")
        )
    }

    private fun BookingRecord.isActive(): Boolean =
        status in setOf(
            BookingStatus.MENUNGGU_KONFIRMASI,
            BookingStatus.DIKONFIRMASI,
            BookingStatus.SEDANG_DISERVIS
        )
}

object PointsRepository {
    private val db get() = FirebaseFirestore.getInstance()

    fun listen(
        uid: String,
        onBalance: (Long) -> Unit,
        onTransactions: (List<PointsTransaction>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        val userListener = db.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) onError(error)
                else onBalance((snapshot?.get("points") as? Number)?.toLong() ?: 0L)
            }
        val transactionListener = db.collection("points_transactions")
            .whereEqualTo("customerId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents.orEmpty().map { doc ->
                    PointsTransaction(
                        id = doc.id,
                        customerId = doc.getString("customerId").orEmpty(),
                        bookingId = doc.getString("bookingId").orEmpty(),
                        type = doc.getString("type").orEmpty(),
                        points = (doc.get("points") as? Number)?.toLong() ?: 0L,
                        description = doc.getString("description").orEmpty(),
                        createdAt = doc.getTimestamp("createdAt")
                    )
                }.sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
                onTransactions(list)
            }
        return ListenerRegistration {
            userListener.remove()
            transactionListener.remove()
        }
    }

    fun displayDate(timestamp: Timestamp?): String = timestamp?.toDate()?.let {
        java.text.SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(it)
    } ?: "Menunggu waktu server"
}