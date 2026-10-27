package id.ac.stmi.mobileproject

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.GridLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import id.ac.stmi.mobileproject.databinding.ActivitySemuaMenuPetugasBinding

class SemuaMenuActivityPetugas : AppCompatActivity() {

    companion object {
        const val MENU_BOOKING = "booking"
        const val MENU_SERVIS = "servis"
        const val MENU_TEKNISI = "teknisi"
        const val MENU_PELANGGAN = "pelanggan"
        const val MENU_LAYANAN = "layanan"
        const val MENU_PROMO = "promo"
        const val MENU_LAPORAN = "laporan"
        const val MENU_STAFF = "staff"
        const val MENU_BENGKEL = "bengkel"

    }

    private lateinit var binding: ActivitySemuaMenuPetugasBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val adapter = MenuGridAdapter { menu -> bukaMenu(menu) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySemuaMenuPetugasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inset sistem ditangani sendiri (layout ini tidak memakai include_top_bar)
        val headerTop = binding.header.paddingTop
        val listBottom = binding.rvMenu.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.header.updatePadding(top = headerTop + bars.top)
            binding.rvMenu.updatePadding(bottom = listBottom + bars.bottom)
            insets
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.rvMenu.layoutManager = GridLayoutManager(this, 3)
        binding.rvMenu.adapter = adapter

        // Tampilkan menu dasar dulu, menu khusus pemilik menyusul setelah role terbaca
        adapter.submit(daftarMenu(isPemilik = false))
        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                if (doc.getString("role") == "pemilik") {
                    adapter.submit(daftarMenu(isPemilik = true))
                }
            }
        }
    }

    private fun daftarMenu(isPemilik: Boolean): List<MenuPetugas> {
        val menu = mutableListOf(
            MenuPetugas(MENU_BOOKING, "Booking", R.drawable.ic_calendar),
            MenuPetugas(MENU_SERVIS, "Servis", R.drawable.ic_build),
            MenuPetugas(MENU_TEKNISI, "Teknisi", R.drawable.ic_tune),
            MenuPetugas(MENU_PELANGGAN, "Pelanggan", R.drawable.ic_car),
            MenuPetugas(MENU_LAYANAN, "Layanan", R.drawable.ic_menu_layanan),
            MenuPetugas(MENU_PROMO, "Promo", R.drawable.ic_gift)
        )
        if (isPemilik) {
            menu += MenuPetugas(MENU_LAPORAN, "Laporan", R.drawable.ic_receipt)
            menu += MenuPetugas(MENU_STAFF, "Staff", R.drawable.ic_staff)
            menu += MenuPetugas(MENU_BENGKEL, "Bengkel", R.drawable.ic_location)
        }
        return menu
    }

    private fun bukaMenu(menu: MenuPetugas) {
        when (menu.key) {
            MENU_PELANGGAN ->
                startActivity(Intent(this, PetugasPelangganActivity::class.java))
            MENU_PROMO ->
                startActivity(Intent(this, PetugasPromoActivity::class.java))
            MENU_BOOKING -> startActivity(Intent(this, BookingPetugasActivity::class.java))
            MENU_SERVIS -> toast("Kelola servis")
            MENU_TEKNISI -> toast("Kelola teknisi")
            MENU_LAYANAN -> {
                startActivity(Intent(this, LayananActivityPetugas::class.java))
            }
            MENU_LAPORAN -> toast("Laporan pendapatan")
            MENU_STAFF -> toast("Kelola Staff")
            MENU_BENGKEL -> {
                startActivity(Intent(this, BengkelActivityPetugas::class.java))
            }
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}