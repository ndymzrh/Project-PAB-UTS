package id.ac.stmi.mobileproject

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import id.ac.stmi.mobileproject.databinding.ActivityHomeCustomerBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.NumberFormat
import java.util.Locale

class HomeActivityCustomer : AppCompatActivity() {

    private lateinit var binding: ActivityHomeCustomerBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private var currentVehicle: Vehicle? = null
    private var userReg: ListenerRegistration? = null
    private var vehiclesReg: ListenerRegistration? = null
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


        setContentView(binding.root)
        val notifBadge = setupTopBar(showNotificationDot = true)
        showActiveBooking(null, null, null)

        val uid = auth.currentUser?.uid
        if (uid == null) {
            goToLogin()
            return
        }

        setupClicks()
    }

    // Listener dipasang tiap layar tampil & dilepas saat tersembunyi,
    // supaya data ikut segar setelah kembali dari halaman Daftar Mobil.
    override fun onStart() {
        super.onStart()
        val uid = auth.currentUser?.uid ?: return
        userReg = listenUser(uid)
        vehiclesReg = listenVehicles(uid)
    }

    override fun onStop() {
        super.onStop()
        userReg?.remove()
        vehiclesReg?.remove()
        userReg = null
        vehiclesReg = null
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

    private fun showActiveBooking(title: String?, schedule: String?, status: String?) {
        val ada = title != null
        binding.tvBookingHeader.visibility = if (ada) View.VISIBLE else View.GONE
        binding.cardActiveBooking.visibility = if (ada) View.VISIBLE else View.GONE
        if (ada) {
            binding.tvBookingTitle.text = title
            binding.tvBookingSchedule.text = schedule
            binding.tvBookingStatus.text = status
        }
    }

    private fun setupClicks() {
        binding.cardVehicle.setOnClickListener {
            startActivity(Intent(this, MyVehiclesActivityCustomer::class.java))
        }

        binding.menuBooking.setOnClickListener { toast("Booking servis") }
        binding.menuLayanan.setOnClickListener { toast("Daftar layanan") }
        binding.menuBengkel.setOnClickListener { toast("Lokasi bengkel") }
        binding.menuRiwayat.setOnClickListener { toast("Riwayat servis") }
        binding.cardActiveBooking.setOnClickListener { toast("Detail booking") }
        binding.fabBooking.setOnClickListener { toast("Buat booking baru") }

        binding.bottomNav.selectedItemId = R.id.nav_home
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_promo -> { toast("Promo"); true }
                R.id.nav_bengkel -> { toast("Peta bengkel"); true }
                R.id.nav_account -> {
                    startActivity(Intent(this, AccountActivity::class.java))
                    false // false = tab Beranda tetap terpilih di halaman ini
                }
                else -> false
            }
        }
    }

    private fun goToLogin() {
        // Ganti LoginActivity dengan activity login Anda
        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}