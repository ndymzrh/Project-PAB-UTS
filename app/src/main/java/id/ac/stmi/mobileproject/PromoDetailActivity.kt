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
    private var promoSaatIni: PromoItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_promo_detail)

        btnKlaim = findViewById(R.id.btnKlaim)
        aturTombol("Memeriksa...", aktif = false)

        setupBottomNav()

        val promoId = intent.getStringExtra(EXTRA_PROMO_ID)

        if (promoId.isNullOrBlank()) {
            toast("ID promo tidak ditemukan")
            finish()
            return
        }

        PromoStore.get(this, promoId) { promo ->
            if (isFinishing || isDestroyed) return@get

            if (promo == null) {
                toast("Promo tidak ditemukan")
                finish()
            } else {
                promoSaatIni = promo
                tampilkan(promo)
            }
        }
    }

    /** Teks tombol klaim: promo poin menampilkan jumlah poin yang ditukar. */
    private fun teksKlaim(promo: PromoItem): String =
        if (promo.bisaDitukarPoin) "Tukar ${promo.labelPoin}" else "Klaim"

    private fun tampilkan(promo: PromoItem) {
        findViewById<ImageView>(R.id.imgBanner)
            .setImageResource(drawableId(promo.gambar))

        findViewById<TextView>(R.id.tvPromoJudul).text =
            promo.judul

        val infoPoin = if (promo.bisaDitukarPoin) " · Tukar ${promo.labelPoin}" else ""
        findViewById<TextView>(R.id.tvPromoMeta).text =
            "Berlaku s/d ${promo.berakhir.toTanggal()} · ${promo.kategori.label}$infoPoin"

        findViewById<TextView>(R.id.tvKodePromo).text =
            promo.kode

        findViewById<TextView>(R.id.tvSyarat).text =
            promo.syarat.lines()
                .filter { it.isNotBlank() }
                .mapIndexed { index, syarat ->
                    "${index + 1}. $syarat"
                }
                .joinToString("\n")

        periksaStatusKlaim(promo)

        btnKlaim.setOnClickListener {
            if (promo.berakhir < System.currentTimeMillis()) {
                aturTombol("Berakhir", aktif = false)
                toast("Promo sudah berakhir")
                return@setOnClickListener
            }

            // Toast info: berapa poin yang harus ditukar
            if (promo.bisaDitukarPoin) {
                toast("Promo ini butuh ${promo.labelPoin} untuk ditukar")
            }

            val pesan = if (promo.bisaDitukarPoin) {
                "\"${promo.judul}\" akan masuk ke Voucher Saya.\n\n" +
                        "Poin Anda akan dikurangi ${promo.labelPoin}."
            } else {
                "\"${promo.judul}\" akan masuk ke Voucher Saya."
            }

            MaterialAlertDialogBuilder(this)
                .setTitle(if (promo.bisaDitukarPoin) "Tukar poin dengan voucher?" else "Klaim promo ini?")
                .setMessage(pesan)
                .setNegativeButton("Batal", null)
                .setPositiveButton(if (promo.bisaDitukarPoin) "Tukar" else "Klaim") { _, _ ->
                    prosesKlaim(promo)
                }
                .show()
        }
    }

    private fun periksaStatusKlaim(promo: PromoItem) {
        if (promo.berakhir < System.currentTimeMillis()) {
            aturTombol("Berakhir", aktif = false)
            return
        }

        aturTombol("Memeriksa...", aktif = false)

        VoucherStore.cekDiklaim(this, promo.id) { sudah ->
            if (isFinishing || isDestroyed) return@cekDiklaim

            if (sudah) {
                aturTombol("Sudah diklaim", aktif = false)
            } else {
                aturTombol(teksKlaim(promo), aktif = true)
            }
        }
    }

    private fun prosesKlaim(promo: PromoItem) {
        aturTombol("Memproses...", aktif = false)

        VoucherStore.klaim(this, promo) { hasil ->
            if (isFinishing || isDestroyed) return@klaim

            when (hasil) {
                VoucherStore.Hasil.SUKSES -> {
                    aturTombol("Sudah diklaim", aktif = false)
                    toast(
                        if (promo.bisaDitukarPoin)
                            "Berhasil! ${promo.labelPoin} dipakai. Cek Voucher Saya."
                        else
                            "Promo berhasil diklaim! Cek Voucher Saya."
                    )
                }

                VoucherStore.Hasil.SUDAH_DIKLAIM -> {
                    aturTombol("Sudah diklaim", aktif = false)
                    toast("Promo ini sudah pernah diklaim")
                }

                VoucherStore.Hasil.BERAKHIR -> {
                    aturTombol("Berakhir", aktif = false)
                    toast("Promo sudah berakhir")
                }

                VoucherStore.Hasil.POIN_KURANG -> {
                    aturTombol(teksKlaim(promo), aktif = true)
                    toast("Poin Anda tidak mencukupi. Dibutuhkan ${promo.labelPoin}.")
                }

                VoucherStore.Hasil.GAGAL -> {
                    aturTombol(teksKlaim(promo), aktif = true)
                    toast("Gagal klaim promo. Periksa koneksi atau izin Firestore.")
                }
            }
        }
    }

    private fun aturTombol(
        teks: String,
        aktif: Boolean
    ) {
        btnKlaim.text = teks
        btnKlaim.isEnabled = aktif

        btnKlaim.backgroundTintList =
            ColorStateList.valueOf(
                if (aktif) {
                    ContextCompat.getColor(
                        this,
                        R.color.bk_accent
                    )
                } else {
                    Color.parseColor("#C5CBD3")
                }
            )

        btnKlaim.setTextColor(
            if (aktif) Color.WHITE
            else Color.parseColor("#6B7280")
        )
    }

    private fun setupBottomNav() {
        val bottomNav =
            findViewById<BottomNavigationView>(R.id.bottomNav)

        bottomNav.selectedItemId = R.id.nav_promo

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(
                        Intent(
                            this,
                            HomeActivityCustomer::class.java
                        ).addFlags(
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                        )
                    )
                    overridePendingTransition(0, 0)
                    false
                }

                R.id.nav_promo -> {
                    finish()
                    false
                }

                R.id.nav_bengkel -> {
                    toast("Peta bengkel")
                    false
                }

                R.id.nav_account -> {
                    toast("Akun")
                    false
                }

                else -> false
            }
        }

        bottomNav.setOnItemReselectedListener { item ->
            if (item.itemId == R.id.nav_promo) {
                finish()
            }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(
            this,
            msg,
            Toast.LENGTH_SHORT
        ).show()
    }

    companion object {
        const val EXTRA_PROMO_ID = "promoId"
    }
}