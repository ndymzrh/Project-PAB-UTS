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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_promo_detail)

        setupBottomNav()

        // Data dari PromoStore, sama dengan yang diisi petugas
        PromoStore.get(this, intent.getStringExtra(EXTRA_PROMO_ID).orEmpty()) { promo ->
            if (promo == null) {
                toast("Promo tidak ditemukan")
                finish()
            } else {
                tampilkan(promo)
            }
        }
    }

    private fun tampilkan(promo: PromoItem) {
        findViewById<ImageView>(R.id.imgBanner).setImageResource(drawableId(promo.gambar))
        findViewById<TextView>(R.id.tvPromoJudul).text = promo.judul
        findViewById<TextView>(R.id.tvPromoMeta).text =
            "Berlaku s/d ${promo.berakhir.toTanggal()} · ${promo.kategori.label}"
        findViewById<TextView>(R.id.tvKodePromo).text = promo.kode
        findViewById<TextView>(R.id.tvSyarat).text = promo.syarat.lines()
            .filter { it.isNotBlank() }
            .mapIndexed { i, t -> "${i + 1}. $t" }
            .joinToString("\n")

        findViewById<MaterialButton>(R.id.btnSalin).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Kode promo", promo.kode))
            toast("Kode ${promo.kode} disalin")
        }
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
        const val EXTRA_PROMO_ID = "promoId"
    }
}