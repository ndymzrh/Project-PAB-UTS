package id.ac.stmi.mobileproject

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import id.ac.stmi.mobileproject.databinding.ActivityPromoDetailBinding

/**
 * Detail promo (customer).
 *
 * Tombol "Klaim" memanggil VoucherStore.klaim(ctx, promo) { hasil -> ... }.
 */
class PromoDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PROMO_ID = "promo_id"
    }

    private lateinit var binding: ActivityPromoDetailBinding

    private var promoId: String = ""
    private var promoListener: PromoListener? = null
    private var voucherClaimListener: PromoListener? = null
    private var promoSudahDiklaim: Set<String> = emptySet()
    private var promoSaatIni: PromoItem? = null

    /** True selama proses klaim berjalan, supaya tombol tidak bisa ditekan dua kali. */
    private var sedangKlaim = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPromoDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTopBar(showNotificationDot = false)

        promoId = intent.getStringExtra(EXTRA_PROMO_ID).orEmpty()
        if (promoId.isBlank()) {
            toast("Promo tidak ditemukan")
            finish()
            return
        }

        setupBack()
        setupBottomNav()
        binding.btnKlaim.setOnClickListener { klaim() }
    }

    override fun onStart() {
        super.onStart()

        promoListener = PromoStore.listen(
            this,
            onChange = { daftar ->
                promoSaatIni = daftar.firstOrNull { it.id == promoId }
                render()
            },
            onError = { toast("Gagal memuat promo") }
        )

        voucherClaimListener = VoucherStore.dengarkanPromoDiklaim(
            this,
            onChange = { ids ->
                promoSudahDiklaim = ids
                render()
            },
            onError = { toast("Gagal memuat status klaim promo") }
        )
    }

    override fun onStop() {
        super.onStop()
        promoListener?.remove()
        promoListener = null
        voucherClaimListener?.remove()
        voucherClaimListener = null
    }

    // Hilangkan animasi saat ditutup supaya perpindahan mulus (sama seperti halaman tab)
    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    // ------------------------------------------------------------------ Tombol kembali

    private fun setupBack() {
        binding.btnBackPromo.setOnClickListener { finish() }
    }

    // ------------------------------------------------------------------ Tampilan

    private fun render() {
        val p = promoSaatIni
        if (p == null) {
            binding.tvPromoJudul.text = "Promo tidak tersedia"
            binding.btnKlaim.isEnabled = false
            return
        }

        binding.imgBanner.setImageResource(drawableId(p.gambar))
        binding.tvPromoJudul.text = p.judul
        binding.tvPromoMeta.text = "${p.kategori.name} • s/d ${p.berakhir.toTanggal()}"
        binding.tvKodePromo.text = p.kode
        binding.tvSyarat.text = p.syarat

        val sudahDiklaim = p.id in promoSudahDiklaim
        val kedaluwarsa = p.berakhir < System.currentTimeMillis()

        binding.btnKlaim.isEnabled = !sudahDiklaim && !kedaluwarsa && !sedangKlaim
        binding.btnKlaim.text = when {
            sudahDiklaim -> "Diklaim"
            kedaluwarsa -> "Berakhir"
            sedangKlaim -> "Memproses..."
            p.bisaDitukarPoin -> "Tukar ${p.labelPoin}"
            else -> "Klaim"
        }
    }

    // ------------------------------------------------------------------ Klaim

    private fun klaim() {
        val p = promoSaatIni ?: return
        if (sedangKlaim) return

        sedangKlaim = true
        render()

        VoucherStore.klaim(this, p) { hasil ->
            sedangKlaim = false

            when (hasil) {
                VoucherStore.Hasil.SUKSES -> {
                    toast(
                        if (p.bisaDitukarPoin) "Poin berhasil ditukar, voucher ditambahkan"
                        else "Promo berhasil diklaim"
                    )
                    // Buka halaman Voucher Saya, lalu tutup detail promo supaya
                    // tombol kembali di Voucher Saya mengarah ke daftar Promo
                    startActivity(Intent(this, VoucherSayaActivity::class.java))
                    finish()
                }
                VoucherStore.Hasil.SUDAH_DIKLAIM -> toast("Promo ini sudah kamu klaim")
                VoucherStore.Hasil.BERAKHIR -> toast("Promo sudah berakhir")
                VoucherStore.Hasil.POIN_KURANG -> toast("Poin kamu belum cukup untuk menukar promo ini")
                VoucherStore.Hasil.GAGAL -> toast("Gagal mengklaim promo, coba lagi")
            }

            render()
        }
    }

    // ------------------------------------------------------------------ Bottom nav

    private fun openTab(target: Class<*>) {
        startActivity(
            Intent(this, target).addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        )
    }

    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = R.id.nav_promo

        binding.fabBooking.setOnClickListener {
            startActivity(Intent(this, LayananActivity::class.java))
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { openTab(HomeActivityCustomer::class.java); false }
                R.id.nav_promo -> { openTab(PromoActivityCustomer::class.java); false }
                R.id.nav_bengkel -> { openTab(BengkelActivityCustomer::class.java); false }
                R.id.nav_account -> { openTab(AccountActivity::class.java); false }
                else -> false
            }
        }
        binding.bottomNav.setOnItemReselectedListener { }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}