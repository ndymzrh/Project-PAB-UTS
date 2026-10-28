package id.ac.stmi.mobileproject

enum class StatusVoucher(val label: String) {
    TERSEDIA("Tersedia"),
    DIPAKAI("Terpakai"),
    KEDALUWARSA("Kedaluwarsa")
}

/**
 * Voucher milik customer yang berasal dari klaim promo.
 */
data class VoucherSaya(
    val id: String,
    val promoId: String,
    val judul: String,
    val label: String,
    val kode: String,
    val dipakai: Boolean,
    val tglKlaim: Long,
    val berakhir: Long,
    val jenisDiskon: String = "",
    val nilaiDiskon: Long = 0L,
    val layananIds: List<String> = emptyList() // tidak dipakai lagi untuk pencocokan; dipertahankan agar data lama terbaca
) {
    fun status(now: Long = System.currentTimeMillis()): StatusVoucher {
        return when {
            dipakai -> StatusVoucher.DIPAKAI
            berakhir < now -> StatusVoucher.KEDALUWARSA
            else -> StatusVoucher.TERSEDIA
        }
    }
}

/** Voucher bisa dipakai untuk satu layanan jika namanya sama/mirip. */
fun VoucherSaya.bisaDipakaiUntuk(namaLayanan: String): Boolean =
    namaLayananMirip(judul, namaLayanan)

/** Voucher bisa dipakai jika cocok dengan salah satu layanan yang dipilih. */
fun VoucherSaya.bisaDipakaiUntuk(namaLayananDipilih: Collection<String>): Boolean =
    namaLayananDipilih.any { namaLayananMirip(judul, it) }