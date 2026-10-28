package id.ac.stmi.mobileproject

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import id.ac.stmi.mobileproject.databinding.ActivityHomeCustomerBinding
import java.text.NumberFormat
import java.util.Locale

class HomeActivityCustomer : AppCompatActivity() {

    private lateinit var binding: ActivityHomeCustomerBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private var currentVehicle: Vehicle? = null
    private var userReg: ListenerRegistration? = null
    private var vehiclesReg: ListenerRegistration? = null
    private var bookingsReg: ListenerRegistration? = null
    private var promoListener: PromoListener? = null
    private var activeBookingId: String? = null
    private val idFormat = NumberFormat.getIntegerInstance(Locale("id", "ID"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityHomeCustomerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ikon status bar gelap karena latar top bar putih
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = true

        // Padding atas = tinggi status bar, area itu ikut berwarna putih
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.topBarContainer.root.setPadding(0, bars.top, 0, 0)
            insets
        }

        val notifBadge = setupTopBar(
            showNotificationDot = true,
            onLogout = {
                userReg?.remove()
                vehiclesReg?.remove()
                bookingsReg?.remove()
                userReg = null
                vehiclesReg = null
                bookingsReg = null
                confirmLogout()
            }
        )
        showActiveBooking(null)

        // Isi promo contoh sekali saja bila belum ada
        PromoStore.seedSekali(this)

        val uid = auth.currentUser?.uid
        if (uid == null) {
            goToLogin()
            return
        }

        setupClicks()
    }

    // Listener dipasang tiap layar tampil & dilepas saat tersembunyi,
    // supaya data ikut segar setelah kembali dari halaman lain.
    override fun onStart() {
        super.onStart()

        // Promo dipasang sebelum cek uid supaya tetap tampil
        promoListener = PromoStore.listen(
            this,
            onChange = { tampilPromo(it) },
            onError = { toast("Gagal memuat promo") }
        )

        val uid = auth.currentUser?.uid ?: return
        userReg = listenUser(uid)
        vehiclesReg = listenVehicles(uid)
        bookingsReg = listenBookings(uid)
    }

    override fun onResume() {
        super.onResume()
        // Tab Beranda tetap terpilih setelah kembali dari tab lain
        binding.bottomNav.menu.findItem(R.id.nav_home)?.isChecked = true
    }

    override fun onStop() {
        super.onStop()
        promoListener?.remove()
        promoListener = null
        userReg?.remove()
        vehiclesReg?.remove()
        bookingsReg?.remove()
        userReg = null
        vehiclesReg = null
        bookingsReg = null
    }

    // Hilangkan animasi saat activity ditutup (back) supaya perpindahan tab mulus
    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    /** Promo dari PromoStore (data yang sama dengan yang dibuat petugas) */
    private fun tampilPromo(semua: List<PromoItem>) {
        // Hanya promo yang sedang berlaku (sudah mulai & belum berakhir)
        val sekarang = System.currentTimeMillis()
        val aktif = semua.filter { it.mulai <= sekarang && it.berakhir >= sekarang }
        val wadah = binding.promoContainer
        wadah.removeAllViews()
        binding.tvPromoHeader.visibility = if (aktif.isEmpty()) View.GONE else View.VISIBLE

        fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
        fun teks(t: String, ukuran: Float) = TextView(this).apply {
            text = t
            textSize = ukuran
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        }

        aktif.forEach { p ->
            val gambar = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageResource(drawableId(p.gambar))
            }
            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#99000000"))
                setPadding(dp(14), dp(8), dp(14), dp(10))
                val label = when {
                    p.jenisDiskon.equals("Persen", ignoreCase = true) -> "${p.nilai}%"
                    p.nilai > 0L -> "Rp ${idFormat.format(p.nilai)}"
                    else -> "GRATIS"
                }
                addView(teks(label, 26f))
                addView(teks(p.judul, 15f))
                addView(teks("Kode: ${p.kode} • s/d ${p.berakhir.toTanggal()}", 12f))
            }
            val kartu = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(320), dp(170))
                    .apply { marginEnd = dp(12) }
                radius = dp(20).toFloat()
                cardElevation = dp(3).toFloat()
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    startActivity(
                        Intent(this@HomeActivityCustomer, PromoActivityCustomer::class.java)
                    )
                }
                addView(gambar, FrameLayout.LayoutParams(-1, -1))
                addView(info, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
            }
            wadah.addView(kartu)
        }
    }

    /** Nama + poin dari dokumen users/{uid} (realtime) */
    private fun listenUser(uid: String): ListenerRegistration =
        db.collection("users").document(uid)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    toast("Gagal memuat profil")
                    return@addSnapshotListener
                }
                // Field "nama" sesuai RegisterActivity (fallback ke "name")
                val name = snap?.getString("nama")
                    ?: snap?.getString("name")
                    ?: auth.currentUser?.displayName
                    ?: "Pelanggan"
                val points = snap?.getLong("points") ?: 0L

                binding.tvUserName.text = name
                binding.tvPoints.text = idFormat.format(points)
            }

    /** Kendaraan dari sub-koleksi users/{uid}/vehicles (realtime) */
    private fun listenVehicles(uid: String): ListenerRegistration =
        db.collection("users").document(uid).collection("vehicles")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    toast("Gagal memuat kendaraan")
                    return@addSnapshotListener
                }
                val list = snap?.documents.orEmpty().mapNotNull { doc ->
                    doc.toObject(Vehicle::class.java)?.copy(id = doc.id)
                }
                // Prioritas: mobil utama, kalau tidak ada ambil yang pertama
                currentVehicle = list.firstOrNull { it.primary } ?: list.firstOrNull()
                showVehicle(currentVehicle)
            }

    private fun showVehicle(v: Vehicle?) {
        if (v == null) {
            binding.tvVehicleName.text = "Belum ada kendaraan"
            binding.tvPlateNumber.text = "Ketuk untuk menambahkan mobil"
            binding.progressService.max = 100
            binding.progressService.progress = 0
            binding.tvServiceProgress.text = "-"
            return
        }

        binding.tvVehicleName.text = v.displayName
        binding.tvPlateNumber.text = v.plateNumber

        val interval = v.serviceIntervalKm.coerceAtLeast(1L)
        val used = (v.odometer - v.lastServiceOdometer).coerceIn(0L, interval)

        binding.progressService.max = interval.toInt()
        binding.progressService.progress = used.toInt()
        binding.tvServiceProgress.text =
            "${idFormat.format(used)}/${idFormat.format(interval)} km"
    }

    private fun listenBookings(uid: String): ListenerRegistration =
        BookingRepository.listenCustomerBookings(
            uid = uid,
            onResult = { bookings ->
                val active = bookings.firstOrNull {
                    it.status in setOf(
                        BookingStatus.MENUNGGU_KONFIRMASI,
                        BookingStatus.DIKONFIRMASI,
                        BookingStatus.SEDANG_DISERVIS
                    )
                }
                showActiveBooking(active)
            },
            onError = { toast("Gagal memuat booking") }
        )

    private fun showActiveBooking(booking: BookingRecord?) {
        val ada = booking != null
        binding.tvBookingHeader.visibility = if (ada) View.VISIBLE else View.GONE
        binding.cardActiveBooking.visibility = if (ada) View.VISIBLE else View.GONE
        activeBookingId = booking?.id
        if (booking != null) {
            binding.tvBookingTitle.text = booking.serviceNames.ifBlank { "Booking servis" }
            binding.tvBookingSchedule.text = "${booking.workshopName} - ${booking.scheduleText}"
            binding.tvBookingStatus.text = booking.status.label
        }
    }

    private fun setupClicks() {
        binding.cardVehicle.setOnClickListener {
            startActivity(Intent(this, MyVehiclesActivityCustomer::class.java))
        }

        binding.menuBooking.setOnClickListener {
            startActivity(Intent(this, BookingListActivityCustomer::class.java))
        }
        binding.menuLayanan.setOnClickListener {
            startActivity(Intent(this, LayananActivity::class.java))
        }
        binding.menuBengkel.setOnClickListener {
            startActivity(Intent(this, BengkelListActivityCustomer::class.java))
        }
        binding.menuRiwayat.setOnClickListener {
            startActivity(Intent(this, RiwayatBookingActivityCustomer::class.java))
        }
        binding.cardActiveBooking.setOnClickListener {
            activeBookingId?.let { bookingId ->
                startActivity(
                    Intent(this, BookingSuccessActivity::class.java)
                        .putExtra(BookingSuccessActivity.EXTRA_BOOKING_ID, bookingId)
                )
            }
        }
        binding.fabBooking.setOnClickListener {
            startActivity(Intent(this, LayananActivity::class.java))
        }

        binding.bottomNav.selectedItemId = R.id.nav_home
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_promo -> { openTab(PromoActivityCustomer::class.java); false }
                R.id.nav_bengkel -> { openTab(BengkelActivityCustomer::class.java); false }
                R.id.nav_account -> { openTab(AccountActivity::class.java); false }
                else -> false
            }
        }
        // Tap tab yang sudah aktif: tidak melakukan apa-apa
        binding.bottomNav.setOnItemReselectedListener { }
    }

    /** Pindah tab tanpa animasi & tanpa menumpuk activity */
    private fun openTab(target: Class<*>) {
        startActivity(
            Intent(this, target).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        )
    }

    private fun goToLogin() {
        // Ganti LoginActivity dengan activity login Anda
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}