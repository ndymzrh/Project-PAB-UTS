package id.ac.stmi.mobileproject

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import id.ac.stmi.mobileproject.databinding.ActivityHomePetugasBinding
import kotlin.jvm.java

class HomeActivityPetugas : AppCompatActivity() {

    private lateinit var binding: ActivityHomePetugasBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityHomePetugasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applySystemBarInsets(binding.main)

        val uid = auth.currentUser?.uid
        if (uid == null) {
            startActivity(
                Intent(this, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
            return
        }

        setupTopBar(showNotificationDot = false)
        setupClicks()
        loadStaff(uid)
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
        binding.menuBooking.setOnClickListener { toast("Kelola booking") }
        binding.menuServis.setOnClickListener { toast("Kelola servis") }
        binding.menuTeknisi.setOnClickListener { toast("Kelola teknisi") }
        binding.menuPelanggan.setOnClickListener {
            startActivity(Intent(this, PetugasPelangganActivity::class.java))
        }
        binding.cardLaporan.setOnClickListener { toast("Laporan pendapatan") }

        // "Lihat semua" di samping judul Menu Cepat -> halaman semua menu
        binding.btnSemuaMenu.setOnClickListener {
            startActivity(Intent(this, SemuaMenuActivityPetugas::class.java))
        }
        // "Lihat semua" di samping judul Booking Masuk
        binding.btnLihatSemua.setOnClickListener { toast("Semua booking masuk") }

        binding.bottomNav.selectedItemId = R.id.nav_home
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_booking -> { toast("Booking"); false }
                R.id.nav_servis -> { toast("Servis"); false }
                R.id.nav_account -> { toast("Akun"); false }
                else -> false
            }
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}