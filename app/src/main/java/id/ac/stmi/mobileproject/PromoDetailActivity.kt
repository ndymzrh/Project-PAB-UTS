package id.ac.stmi.mobileproject

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton

class PromoDetailActivity : AppCompatActivity() {

    private data class Promo(
        val title: String,
        val validUntil: String,
        val kategori: String,
        val code: String,
        val image: Int,
        val terms: List<String>
    )

    // Urutan sama dengan kartu di halaman daftar (Promo 1..4).
    // Syarat promo 2-4 hanya contoh, silakan diganti.
    private val promos = listOf(
        Promo(
            "Diskon 25% Servis Berkala", "s/d 31 Des 2026", "Servis berkala",
            "SERVISHEMAT25", R.drawable.bg_promo1,
            listOf(
                "Berlaku untuk kendaraan jenis mobil penumpang.",
                "Minimal transaksi servis Rp 200.000.",
                "Tidak dapat digabung dengan promo lain.",
                "Berlaku di seluruh cabang Annova."
            )
        ),
        Promo(
            "Voucher Servis Poin Loyalitas", "s/d 31 Des 2026", "Poin loyalitas",
            "POINSERVIS50", R.drawable.bg_promo2,
            listOf(
                "Voucher senilai Rp 50.000 untuk transaksi servis.",
                "Ditukar dengan poin loyalitas yang tersedia.",
                "Tidak dapat diuangkan.",
                "Berlaku di seluruh cabang Annova."
            )
        ),
        Promo(
            "Paket Kampas Rem & Tune-up", "s/d 28 Feb 2027", "Sparepart",
            "REMTUNE10", R.drawable.bg_promo3,
            listOf(
                "Diskon 10% untuk paket kampas rem dan tune-up.",
                "Berlaku untuk kendaraan jenis mobil penumpang.",
                "Tidak dapat digabung dengan promo lain.",
                "Berlaku di seluruh cabang Annova."
            )
        ),
        Promo(
            "Gratis Cek AC Mobil", "s/d 15 Jan 2027", "Servis",
            "CEKACGRATIS", R.drawable.bg_promo4,
            listOf(
                "Pengecekan AC gratis tanpa minimal transaksi.",
                "Satu kali pemakaian per kendaraan.",
                "Tidak termasuk biaya perbaikan atau penggantian part.",
                "Berlaku di seluruh cabang Annova."
            )
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_promo_detail)

        val promo = promos.getOrNull(intent.getIntExtra(EXTRA_PROMO_INDEX, -1))
        if (promo == null) {
            toast("Promo tidak ditemukan")
            finish()
            return
        }

        findViewById<ImageView>(R.id.imgBanner).setImageResource(promo.image)
        findViewById<TextView>(R.id.tvPromoJudul).text = promo.title
        findViewById<TextView>(R.id.tvPromoMeta).text =
            "Berlaku ${promo.validUntil} · ${promo.kategori}"
        findViewById<TextView>(R.id.tvKodePromo).text = promo.code
        findViewById<TextView>(R.id.tvSyarat).text = promo.terms
            .mapIndexed { i, t -> "${i + 1}. $t" }
            .joinToString("\n")

        findViewById<MaterialButton>(R.id.btnSalin).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Kode promo", promo.code))
            toast("Kode ${promo.code} disalin")
        }

        setupBottomNav()
    }

    private fun setupBottomNav() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        bottomNav.selectedItemId = R.id.nav_promo // sebelum listener
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(
                        Intent(this, HomeActivityCustomer::class.java)
                            .addFlags(
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                            )
                    )
                    overridePendingTransition(0, 0)
                    false
                }
                R.id.nav_bengkel -> { toast("Peta bengkel"); false }
                R.id.nav_account -> { toast("Akun"); false }
                else -> false
            }
        }
        // Tap Promo lagi = kembali ke daftar promo
        bottomNav.setOnItemReselectedListener { finish() }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_PROMO_INDEX = "promoIndex"
    }
}