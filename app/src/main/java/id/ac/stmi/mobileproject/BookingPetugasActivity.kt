package id.ac.stmi.mobileproject

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import id.ac.stmi.mobileproject.databinding.ActivityBookingPetugasBinding

/**
 * Kelola booking (petugas): daftar realtime semua booking, pencarian, dan perubahan status.
 * Saat status menjadi SELESAI, points customer otomatis ditambahkan (BookingRepository.updateStatus).
 */
class BookingPetugasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookingPetugasBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private var listener: ListenerRegistration? = null
    private lateinit var adapter: BookingPetugasAdapter
    private val updatingIds = mutableSetOf<String>()
    private var semua: List<BookingRecord> = emptyList()
    private var filter = Filter.AKTIF

    private enum class Filter { AKTIF, RIWAYAT, SEMUA }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityBookingPetugasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Header biru tua -> ikon status bar terang
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false

        val headerTop = binding.header.paddingTop
        val listBottom = binding.rvBookings.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.header.updatePadding(top = headerTop + bars.top)
            binding.rvBookings.updatePadding(bottom = listBottom + bars.bottom)
            insets
        }

        binding.btnBack.setOnClickListener { finish() }

        adapter = BookingPetugasAdapter(
            allowStatusUpdate = true,
            onUpdateStatus = { booking, status -> mintaUbahStatus(booking, status) }
        )
        binding.rvBookings.layoutManager = LinearLayoutManager(this)
        binding.rvBookings.adapter = adapter
        binding.etCari.doAfterTextChanged { tampilkan() }

        binding.chipAktif.setOnClickListener { pilihFilter(Filter.AKTIF) }
        binding.chipRiwayat.setOnClickListener { pilihFilter(Filter.RIWAYAT) }
        binding.chipSemua.setOnClickListener { pilihFilter(Filter.SEMUA) }
    }

    private fun pilihFilter(baru: Filter) {
        if (filter == baru) return
        filter = baru
        tampilkan()
        binding.rvBookings.scrollToPosition(0)
    }

    override fun onStart() {
        super.onStart()
        val uid = auth.currentUser?.uid
        if (uid == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        listenBookings()
    }

    override fun onStop() {
        listener?.remove()
        listener = null
        super.onStop()
    }

    private fun listenBookings() {
        binding.progress.isVisible = true
        listener?.remove()
        listener = BookingRepository.listenAllBookings(
            onResult = { bookings ->
                semua = bookings
                binding.progress.isVisible = false
                tampilkan()
            },
            onError = {
                binding.progress.isVisible = false
                binding.tvEmpty.text = "Gagal memuat booking"
                binding.tvEmpty.isVisible = true
                toast("Gagal memuat booking: ${it.message}", panjang = true)
            }
        )
    }

    private fun tampilkan() {
        // Jumlah di chip dihitung dari semua booking (tidak terpengaruh kata pencarian)
        val jumlahAktif = semua.count { !it.status.isFinal }
        val jumlahRiwayat = semua.size - jumlahAktif
        binding.chipAktif.text = "Aktif ($jumlahAktif)"
        binding.chipRiwayat.text = "Riwayat ($jumlahRiwayat)"
        binding.chipSemua.text = "Semua (${semua.size})"
        aturChip(binding.chipAktif, filter == Filter.AKTIF)
        aturChip(binding.chipRiwayat, filter == Filter.RIWAYAT)
        aturChip(binding.chipSemua, filter == Filter.SEMUA)

        val kata = binding.etCari.text?.toString()?.trim()?.lowercase().orEmpty()
        val sesuaiFilter = when (filter) {
            Filter.AKTIF -> semua.filter { !it.status.isFinal }
            Filter.RIWAYAT -> semua.filter { it.status.isFinal }
            Filter.SEMUA -> semua
        }
        val dicari = if (kata.isEmpty()) sesuaiFilter else sesuaiFilter.filter { b ->
            listOf(b.id, b.customerName, b.workshopName, b.plateNumber, b.vehicleName, b.serviceNames)
                .any { it.lowercase().contains(kata) }
        }
        // Tab Aktif: yang perlu dikonfirmasi paling atas, lalu terbaru
        val hasil = if (filter == Filter.AKTIF) {
            dicari.sortedWith(
                compareBy<BookingRecord> { it.status.ordinal }
                    .thenByDescending { it.createdAt?.toDate()?.time ?: 0L }
            )
        } else dicari
        adapter.submitList(hasil)

        binding.tvTotal.text = "${hasil.size} booking ditampilkan"
        binding.tvEmpty.text = when {
            kata.isNotEmpty() -> "Tidak ada booking yang cocok"
            filter == Filter.AKTIF -> "Tidak ada booking aktif"
            filter == Filter.RIWAYAT -> "Belum ada riwayat booking"
            else -> "Belum ada booking"
        }
        binding.tvEmpty.isVisible = hasil.isEmpty()
        binding.rvBookings.isVisible = hasil.isNotEmpty()
    }

    private fun aturChip(chip: TextView, aktif: Boolean) {
        chip.setBackgroundResource(if (aktif) R.drawable.bg_filter_chip_on else R.drawable.bg_filter_chip_off)
        chip.setTextColor(
            ContextCompat.getColor(this, if (aktif) android.R.color.white else R.color.bk_text_secondary)
        )
    }

    /** Status yang tidak bisa dibatalkan (selesai/ditolak/dibatalkan) dikonfirmasi dulu. */
    private fun mintaUbahStatus(booking: BookingRecord, status: BookingStatus) {
        if (booking.status == status) {
            toast("Status booking belum berubah")
            return
        }
        val nama = booking.customerName.ifBlank { "customer" }
        val konfirmasi: Pair<String, String>? = when (status) {
            BookingStatus.SELESAI -> "Selesaikan servis?" to
                    "Points ${booking.estimatedPointsText} akan langsung ditambahkan ke $nama " +
                    "dan status tidak bisa diubah lagi."
            BookingStatus.DITOLAK -> "Tolak booking?" to
                    "Booking ${booking.id} akan ditolak dan status tidak bisa diubah lagi."
            BookingStatus.DIBATALKAN -> "Batalkan booking?" to
                    "Booking ${booking.id} akan dibatalkan dan status tidak bisa diubah lagi."
            else -> null
        }

        if (konfirmasi == null) {
            ubahStatus(booking, status)
        } else {
            AlertDialog.Builder(this)
                .setTitle(konfirmasi.first)
                .setMessage(konfirmasi.second)
                .setPositiveButton("Ya") { _, _ -> ubahStatus(booking, status) }
                .setNegativeButton("Batal") { _, _ -> tampilkan() } // kembalikan pilihan dropdown
                .show()
        }
    }

    private fun ubahStatus(booking: BookingRecord, status: BookingStatus) {
        if (!updatingIds.add(booking.id)) return

        binding.progress.isVisible = true
        BookingRepository.updateStatus(
            bookingId = booking.id,
            status = status,
            onSuccess = {
                updatingIds.remove(booking.id)
                binding.progress.isVisible = false
                toast(
                    if (status == BookingStatus.SELESAI) "Servis selesai, points customer ditambahkan"
                    else "Progress booking diperbarui"
                )
            },
            onError = { error ->
                updatingIds.remove(booking.id)
                binding.progress.isVisible = false
                toast(error.message ?: "Gagal memperbarui status", panjang = true)
                tampilkan()
            }
        )
    }

    private fun toast(message: String, panjang: Boolean = false) =
        Toast.makeText(this, message, if (panjang) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}