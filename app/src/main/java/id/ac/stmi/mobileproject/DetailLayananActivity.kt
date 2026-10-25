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
import com.google.firebase.auth.FirebaseAuth
import id.ac.stmi.mobileproject.databinding.ActivityDetailLayananBinding
import id.ac.stmi.mobileproject.databinding.ItemSvcStepBinding

/**
 * Halaman Detail Layanan.
 * Layout : res/layout/activity_detail_layanan.xml (ViewBinding -> ActivityDetailLayananBinding)
 * Input  : Intent extra EXTRA_LAYANAN_ID (id layanan yang diklik di halaman Layanan)
 * Data   : dokumen Firestore koleksi "layanan" dengan id tersebut (realtime)
 * Output : kalau tombol "Booking Layanan Ini" ditekan, kirim id layanan kembali ke
 *          LayananActivity lewat setResult(RESULT_OK, ...).
 */
class DetailLayananActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailLayananBinding
    private var tersimpan = false // status ikon bookmark

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityDetailLayananBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupInsets()

        // Ambil layanan dari Firestore berdasarkan id yang dikirim lewat Intent
        val id = intent.getStringExtra(EXTRA_LAYANAN_ID).orEmpty()
        if (id.isEmpty()) {
            toast("Layanan tidak ditemukan")
            finish()
            return
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnBook.isEnabled = false // aktif setelah data layanan tiba

        LayananCatalog.listenSatu(
            this, id,
            onError = { toast("Gagal memuat layanan: ${it.message}") }
        ) { layanan ->
            if (layanan == null) {
                toast("Layanan tidak ditemukan")
                finish()
            } else {
                tampilkan(layanan)
                setupKlik(layanan)
                binding.btnBook.isEnabled = true
            }
        }

        // Mobil utama user
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            listenKendaraanUtama(uid) { v ->
                if (v == null) {
                    binding.tvVehicleName.setText(R.string.svc_belum_ada_mobil)
                    binding.rowPlate.visibility = View.GONE
                } else {
                    binding.tvVehicleName.text = v.displayName
                    binding.tvPlate.text = v.plateNumber
                    binding.rowPlate.visibility = View.VISIBLE
                }
            }
        }
    }

    /**
     * Layar digambar sampai belakang status bar & navigation bar.
     * Header diberi padding atas setinggi status bar, bar bawah diberi padding
     * bawah setinggi navigation bar, supaya isinya tidak tertutup.
     */
    private fun setupInsets() {
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = true // ikon status bar gelap (latar putih)

        // Simpan padding awal supaya tidak menumpuk saat listener dipanggil berulang
        val headerTop = binding.headerBar.paddingTop
        val barBottom = binding.bottomBar.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.headerBar.setPadding(
                binding.headerBar.paddingLeft, headerTop + bars.top,
                binding.headerBar.paddingRight, binding.headerBar.paddingBottom
            )
            binding.bottomBar.setPadding(
                binding.bottomBar.paddingLeft, binding.bottomBar.paddingTop,
                binding.bottomBar.paddingRight, barBottom + bars.bottom
            )
            insets
        }
    }

    /** Mengisi seluruh tampilan dengan data layanan. */
    private fun tampilkan(l: LayananItem) {
        binding.ivHero.setImageResource(l.ikon)
        binding.tvHeroCaption.text = "Detail Layanan - ${l.judul}"
        binding.tvTitle.text = l.judul
        binding.tvDesc.text = l.deskripsiDetail
        binding.tvChipGaransi.text = l.garansi
        binding.tvChipPart.text = l.sparepart
        binding.tvChipEstimasi.text = "Estimasi ${l.durasi}"

        binding.tvStepCount.text = "${l.langkah.size} tahapan pengerjaan"
        binding.tvPackage.text = l.paket

        // Satu baris per tahapan (item_svc_step.xml)
        binding.layoutSteps.removeAllViews()
        l.langkah.forEachIndexed { i, langkah ->
            val row = ItemSvcStepBinding.inflate(layoutInflater, binding.layoutSteps, false)
            row.divider.visibility = if (i == 0) View.GONE else View.VISIBLE // tahap 1 tanpa garis
            row.tvNumber.text = (i + 1).toString()
            row.tvStepTitle.text = langkah.judul
            row.tvStepDesc.text = langkah.deskripsi
            binding.layoutSteps.addView(row.root)
        }

        binding.tvTotal.text = l.hargaTampil
    }

    private fun setupKlik(l: LayananItem) {
        binding.btnShare.setOnClickListener {
            val kirim = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "${l.judul} - ${l.hargaTampil} di Annova AutoCare")
            }
            startActivity(Intent.createChooser(kirim, getString(R.string.svc_bagikan)))
        }

        binding.btnBookmark.setOnClickListener {
            tersimpan = !tersimpan
            binding.btnBookmark.setImageResource(
                if (tersimpan) R.drawable.ic_svc_bookmark_filled else R.drawable.ic_svc_bookmark
            )
            toast(if (tersimpan) "Layanan disimpan" else "Simpanan dihapus")
        }

        binding.btnChangeCar.setOnClickListener {
            startActivity(Intent(this, MyVehiclesActivityCustomer::class.java))
        }

        binding.btnBook.setOnClickListener {
            // Kirim id layanan kembali ke LayananActivity, lalu tutup halaman ini
            setResult(RESULT_OK, Intent().putExtra(EXTRA_LAYANAN_ID, l.id))
            finish()
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}