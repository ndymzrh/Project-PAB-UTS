package id.ac.stmi.mobileproject

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import id.ac.stmi.mobileproject.databinding.ActivityAccountBinding
import id.ac.stmi.mobileproject.databinding.ItemMenuRowBinding
import java.text.NumberFormat
import java.util.Locale

/**
 * Halaman Akun.
 * Layout : res/layout/activity_account.xml (ViewBinding -> ActivityAccountBinding)
 * Data   : Firestore users/{uid} dan users/{uid}/vehicles
 */
class AccountActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountBinding

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    // Format angka gaya Indonesia: 39420 -> "39.420"
    private val idFormat = NumberFormat.getIntegerInstance(Locale("id", "ID"))

    private var currentVehicle: Vehicle? = null

    companion object {
        // Nomor WhatsApp customer care. Format internasional, TANPA "+" dan TANPA 0 di depan.
        private const val CS_WHATSAPP_NUMBER = "6285156210062"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAccountBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applySystemBarInsets(binding.main)

        // Belum login -> kembali ke halaman login
        val uid = auth.currentUser?.uid
        if (uid == null) {
            goToLogin()
            return
        }

        setupTopBar(showNotificationDot = true)
        setupMenus()
        setupClicks()
        setupBottomNav()

        listenUser(uid)
        listenVehicles(uid)
        showActiveService(null)
    }

    override fun onResume() {
        super.onResume()
        // Memastikan ikon tab Akun tetap terpilih saat kembali ke halaman ini
        binding.bottomNav.menu.findItem(R.id.nav_account)?.isChecked = true
    }

    // Hilangkan animasi bawaan saat activity ditutup agar perpindahan tab mulus
    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    // ------------------------------------------------------------------
    // 1. DATA PROFIL
    // ------------------------------------------------------------------
    private fun listenUser(uid: String) {
        db.collection("users").document(uid)
            .addSnapshotListener(this) { snap, error ->
                if (error != null) {
                    toast("Gagal memuat profil")
                    return@addSnapshotListener
                }
                val name = snap?.getString("nama")
                    ?: auth.currentUser?.displayName
                    ?: "Pelanggan"
                val phone = snap?.getString("no_hp") ?: "-"
                val email = snap?.getString("email") ?: auth.currentUser?.email ?: "-"
                val points = snap?.getLong("points") ?: 0L

                binding.tvName.text = name
                binding.tvAvatar.text = initialsOf(name)
                binding.tvPhone.text = phone
                binding.tvEmail.text = email
                binding.tvStatPoints.text = idFormat.format(points)
            }
    }

    private fun initialsOf(name: String): String =
        name.trim().split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "?" }

    // ------------------------------------------------------------------
    // 2. DATA KENDARAAN
    // ------------------------------------------------------------------
    private fun listenVehicles(uid: String) {
        db.collection("users").document(uid).collection("vehicles")
            .addSnapshotListener(this) { snap, error ->
                if (error != null) {
                    toast("Gagal memuat kendaraan")
                    return@addSnapshotListener
                }
                val list = snap?.documents.orEmpty().mapNotNull { doc ->
                    doc.toObject(Vehicle::class.java)?.copy(id = doc.id)
                }
                binding.tvStatCars.text = list.size.toString()

                currentVehicle = list.firstOrNull { it.primary } ?: list.firstOrNull()
                showVehicle(currentVehicle)
            }
    }

    private fun showVehicle(v: Vehicle?) {
        val ada = v != null
        binding.layoutVehicle.visibility = if (ada) View.VISIBLE else View.GONE
        binding.tvNoVehicle.visibility = if (ada) View.GONE else View.VISIBLE
        if (v == null) return

        binding.tvVehicleName.text = v.displayName
        binding.tvPlate.text = v.plateNumber
        binding.chipPrimary.visibility = if (v.primary) View.VISIBLE else View.GONE
        binding.tvOdometer.text = "${idFormat.format(v.odometer)} km"

        val nextKm = v.lastServiceOdometer + v.serviceIntervalKm
        binding.tvNextService.text = "${idFormat.format(nextKm)} km"
    }

    // ------------------------------------------------------------------
    // 3. SERVIS SEDANG BERJALAN
    // ------------------------------------------------------------------
    private fun showActiveService(detail: String?) {
        val ada = detail != null
        binding.cardActiveService.visibility = if (ada) View.VISIBLE else View.GONE
        binding.tvStatActive.text = if (ada) "1" else "0"
        if (ada) binding.tvActiveServiceDetail.text = detail
    }

    // ------------------------------------------------------------------
    // 4. BARIS MENU
    // ------------------------------------------------------------------
    private fun setupMenus() {
        bindMenu(binding.menuHistory, R.drawable.ic_receipt,
            R.string.menu_riwayat_title, R.string.menu_riwayat_sub) {
            startActivity(Intent(this, RiwayatBookingActivityCustomer::class.java))
        }
        bindMenu(binding.menuAddress, R.drawable.ic_location,
            R.string.menu_alamat_title, R.string.menu_alamat_sub) {
            toast("Alamat & lokasi tersimpan")
        }
        bindMenu(binding.menuPayment, R.drawable.ic_wallet,
            R.string.menu_bayar_title, R.string.menu_bayar_sub) {
            toast("Metode pembayaran")
        }
        bindMenu(binding.menuSettings, R.drawable.ic_shield,
            R.string.menu_setting_title, R.string.menu_setting_sub) {
            toast("Pengaturan & notifikasi")
        }

        bindMenu(binding.menuCare, R.drawable.ic_headset,
            R.string.menu_cs_title, R.string.menu_cs_sub) {
            openWhatsApp("Halo Annova AutoCare, saya ingin konsultasi teknis mengenai kendaraan saya.")
        }
        bindMenu(binding.menuFaq, R.drawable.ic_help_box,
            R.string.menu_faq_title, R.string.menu_faq_sub) {
            openWhatsApp("Halo Annova AutoCare, saya butuh bantuan terkait estimasi waktu pengerjaan & suku cadang.")
        }
        bindMenu(binding.menuPolicy, R.drawable.ic_verified,
            R.string.menu_garansi_title, R.string.menu_garansi_sub) {
            toast("Kebijakan garansi & privasi")
        }
    }

    private fun openWhatsApp(message: String) {
        val url = "https://wa.me/$CS_WHATSAPP_NUMBER?text=${Uri.encode(message)}"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            toast("WhatsApp atau browser tidak ditemukan")
        }
    }

    private fun bindMenu(
        row: ItemMenuRowBinding,
        iconRes: Int,
        titleRes: Int,
        subtitleRes: Int,
        onClick: () -> Unit
    ) {
        row.ivIcon.setImageResource(iconRes)
        row.tvTitle.setText(titleRes)
        row.tvSubtitle.setText(subtitleRes)
        row.root.setOnClickListener { onClick() }
    }

    // ------------------------------------------------------------------
    // 5. KLIK TOMBOL
    // ------------------------------------------------------------------
    private fun setupClicks() {
        binding.btnEditProfile.setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
        }
        binding.btnTrackService.setOnClickListener { toast("Pantau servis") }
        binding.btnAddCar.setOnClickListener {
            startActivity(Intent(this, AddVehicleActivityCustomer::class.java))
        }
        binding.btnManageCar.setOnClickListener {
            startActivity(Intent(this, MyVehiclesActivityCustomer::class.java))
        }
        binding.btnBookService.setOnClickListener {
            startActivity(Intent(this, LayananActivity::class.java))
        }
        binding.fabBooking.setOnClickListener {
            startActivity(Intent(this, LayananActivity::class.java))
        }
        binding.btnLogout.setOnClickListener { confirmLogout() }
    }

    // ------------------------------------------------------------------
    // 6. BOTTOM NAVIGATION (Dioptimalkan agar smooth)
    // ------------------------------------------------------------------
    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = R.id.nav_account

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_account -> true
                R.id.nav_home -> {
                    openTab(HomeActivityCustomer::class.java)
                    false
                }
                R.id.nav_promo -> {
                    openTab(PromoActivityCustomer::class.java)
                    false
                }
                R.id.nav_bengkel -> {
                    openTab(BengkelActivityCustomer::class.java)
                    false
                }
                else -> false
            }
        }
        // Mencegah reload ketika tab yang sedang aktif ditekan kembali
        binding.bottomNav.setOnItemReselectedListener { }
    }

    /** Fungsi pembantu untuk pindah tab secara instan tanpa animasi kedip */
    private fun openTab(target: Class<*>) {
        startActivity(
            Intent(this, target).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        )
        finish()
    }

    // ------------------------------------------------------------------
    // 7. LOGOUT
    // ------------------------------------------------------------------
    private fun confirmLogout() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.konfirmasi_keluar_title)
            .setMessage(R.string.konfirmasi_keluar_msg)
            .setNegativeButton(R.string.batal, null)
            .setPositiveButton(R.string.ya_keluar) { _, _ ->
                auth.signOut()
                goToLogin()
            }
            .show()
    }

    private fun goToLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}