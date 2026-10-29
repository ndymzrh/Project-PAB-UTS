package id.ac.stmi.mobileproject

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import id.ac.stmi.mobileproject.databinding.ActivityHomePetugasBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HomeActivityPetugas : AppCompatActivity() {

    private lateinit var binding: ActivityHomePetugasBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private var bookingListener: ListenerRegistration? = null
    private val bookingAdapter by lazy {
        BookingPetugasAdapter(allowStatusUpdate = false) { _, _ -> }
    }
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityHomePetugasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applySystemBarInsets(binding.main)
        binding.rvBooking.layoutManager = LinearLayoutManager(this)
        binding.rvBooking.adapter = bookingAdapter

        val uid = auth.currentUser?.uid
        if (uid == null) {
            startActivity(
                Intent(this, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finish()
            return
        }

        setupTopBar(showNotificationDot = false)
        setupClicks()
        loadStaff(uid)
    }

    override fun onStart() {
        super.onStart()
        listenBookings()
    }

    override fun onStop() {
        bookingListener?.remove()
        bookingListener = null
        super.onStop()
    }

    // Pastikan item yang benar terpilih setiap kali activity kembali aktif (misal setelah tekan tombol Kembali)
    override fun onResume() {
        super.onResume()
        if (binding.bottomNav.selectedItemId != R.id.nav_home) {
            binding.bottomNav.selectedItemId = R.id.nav_home
        }
    }

    private fun listenBookings() {
        bookingListener?.remove()
        bookingListener = BookingRepository.listenAllBookings(
            onResult = { bookings -> renderBookings(bookings) },
            onError = { toast("Gagal memuat booking") }
        )
    }

    private fun renderBookings(bookings: List<BookingRecord>) {
        val today = dateFormat.format(System.currentTimeMillis())
        val now = Calendar.getInstance()
        val activeStatuses = setOf(
            BookingStatus.MENUNGGU_KONFIRMASI,
            BookingStatus.DIKONFIRMASI,
            BookingStatus.SEDANG_DISERVIS
        )
        val todayBookings = bookings.count { it.bookingDate == today }
        val waiting = bookings.count { it.status == BookingStatus.MENUNGGU_KONFIRMASI }
        val running = bookings.count { it.status == BookingStatus.SEDANG_DISERVIS }
        val completedToday = bookings.count {
            it.status == BookingStatus.SELESAI &&
                    (it.completedAt?.let { timestamp -> dateFormat.format(timestamp.toDate()) == today }
                        ?: (it.bookingDate == today))
        }
        val monthlyRevenue = bookings
            .filter { it.status == BookingStatus.SELESAI }
            .filter { booking ->
                val completedAt = booking.completedAt
                if (completedAt != null) {
                    val completed = Calendar.getInstance().apply { time = completedAt.toDate() }
                    completed.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                            completed.get(Calendar.MONTH) == now.get(Calendar.MONTH)
                } else {
                    booking.bookingDate.startsWith(today.substring(0, 7))
                }
            }
            .sumOf { it.totalPrice }

        binding.tvStatBookingHariIni.text = todayBookings.toString()
        binding.tvStatMenunggu.text = waiting.toString()
        binding.tvStatBerjalan.text = running.toString()
        binding.tvStatSelesai.text = completedToday.toString()
        binding.tvPendapatan.text = "${monthlyRevenue.keRupiah()} bulan ini"

        val preview = bookings.filter { it.status in activeStatuses }.take(3)
        bookingAdapter.submitList(preview)
        binding.tvNoBooking.isVisible = preview.isEmpty()
        binding.rvBooking.isVisible = preview.isNotEmpty()
    }

    private fun loadStaff(uid: String) {
        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val isPemilik = doc.getString("role") == "pemilik"
            binding.tvStaffName.text = doc.getString("nama") ?: "Petugas"
            binding.tvStaffRole.text = if (isPemilik) "Pemilik" else "Admin"
            binding.cardLaporan.visibility = if (isPemilik) View.VISIBLE else View.GONE
        }
    }

    private fun setupClicks() {
        // Menu cepat
        binding.menuBooking.setOnClickListener { startActivity(Intent(this, BookingPetugasActivity::class.java)) }
        binding.menuServis.setOnClickListener { toast("Kelola servis") }
        binding.menuTeknisi.setOnClickListener { toast("Kelola teknisi") }
        binding.menuPelanggan.setOnClickListener {
            startActivity(Intent(this, PetugasPelangganActivity::class.java))
        }

        // Kartu Laporan Pendapatan diarahkan ke LaporanPendapatanActivity
        binding.cardLaporan.setOnClickListener {
            startActivity(Intent(this, LaporanPendapatanActivity::class.java))
        }

        // "Lihat semua" di samping judul Menu Cepat -> halaman semua menu
        binding.btnSemuaMenu.setOnClickListener {
            startActivity(Intent(this, SemuaMenuActivityPetugas::class.java))
        }
        // "Lihat semua" di samping judul Booking Masuk
        binding.btnLihatSemua.setOnClickListener { startActivity(Intent(this, BookingPetugasActivity::class.java)) }

        binding.bottomNav.selectedItemId = R.id.nav_home
        binding.bottomNav.setOnItemSelectedListener { item ->
            // Cegah pemicu berulang jika tab yang sama sedang aktif
            if (item.itemId == binding.bottomNav.selectedItemId) {
                return@setOnItemSelectedListener true
            }

            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_booking -> {
                    val intent = Intent(this, BookingPetugasActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    }
                    startActivity(intent)
                    overridePendingTransition(0, 0)
                    false
                }
                R.id.nav_servis -> {
                    toast("Kelola servis")
                    // Kembalikan seleksi ke tab Home agar tidak salah nyala
                    binding.bottomNav.post { binding.bottomNav.selectedItemId = R.id.nav_home }
                    false
                }
                R.id.nav_account -> {
                    val intent = Intent(this, AccountPetugasActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    }
                    startActivity(intent)
                    overridePendingTransition(0, 0)
                    true
                }
                else -> false
            }
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}