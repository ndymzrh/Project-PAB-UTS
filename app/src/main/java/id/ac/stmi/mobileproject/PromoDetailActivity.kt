package id.ac.stmi.mobileproject

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PromoDetailActivity : AppCompatActivity() {

    private lateinit var btnKlaim: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_promo_detail)

        // Tombol bawaan layout (id btnSalin, teks "Klaim"). Nonaktif dulu sampai status dicek.
        btnKlaim = findViewById(R.id.btnKlaim)
        aturTombol("Klaim", aktif = false)

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

        // Promo berakhir / sudah diklaim = tombol nonaktif
        if (promo.berakhir < System.currentTimeMillis()) {
            aturTombol("Berakhir", aktif = false)
        } else {
            VoucherStore.cekDiklaim(this, promo.id) { sudah ->
                if (sudah) aturTombol("Sudah diklaim", aktif = false)
                else aturTombol("Klaim", aktif = true)
            }
        }

        btnKlaim.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Klaim promo ini?")
                .setMessage("\"${promo.judul}\" akan masuk ke Voucher saya.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Klaim") { _, _ -> klaim(promo) }
                .show()
        }
    }

    private fun klaim(promo: PromoItem) {
        aturTombol("Memproses...", aktif = false) // cegah tap ganda
        VoucherStore.klaim(this, promo) { hasil ->
            when (hasil) {
                VoucherStore.Hasil.SUKSES -> {
                    toast("Promo masuk ke Voucher saya")
                    finish() // promo sudah hilang dari daftar Home
                }
                VoucherStore.Hasil.SUDAH_DIKLAIM -> {
                    toast("Promo sudah diklaim")
                    finish()
                }
                VoucherStore.Hasil.BERAKHIR -> {
                    toast("Promo sudah berakhir")
                    aturTombol("Berakhir", aktif = false)
                }
                VoucherStore.Hasil.GAGAL -> {
                    toast("Gagal mengklaim, periksa koneksi")
                    aturTombol("Klaim", aktif = true)
                }
            }
        }
    }

    /** Oranye saat aktif, abu-abu saat nonaktif. */
    private fun aturTombol(teks: String, aktif: Boolean) {
        btnKlaim.text = teks
        btnKlaim.isEnabled = aktif
        btnKlaim.backgroundTintList = ColorStateList.valueOf(
            if (aktif) ContextCompat.getColor(this, R.color.bk_accent) else Color.parseColor("#C5CBD3")
        )
        btnKlaim.setTextColor(if (aktif) Color.WHITE else Color.parseColor("#6B7280"))
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