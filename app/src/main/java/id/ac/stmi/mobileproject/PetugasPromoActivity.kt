package id.ac.stmi.mobileproject

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import id.ac.stmi.mobileproject.databinding.ActivityPromoPetugasBinding

class PetugasPromoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPromoPetugasBinding
    private lateinit var adapter: PetugasPromoAdapter
    private lateinit var rvPromo: RecyclerView
    private lateinit var tvKosong: TextView

    private var filter: KategoriPromo? = null // null = Semua (chip Semua terpilih di awal)
    private var semuaPromo: List<PromoItem> = emptyList()
    private var promoListener: PromoListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPromoPetugasBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnBack.setOnClickListener { finish() }

        val atasAwal = binding.header.paddingTop
        val bawahAwal = binding.main.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.header.updatePadding(top = atasAwal + bars.top)
            binding.main.updatePadding(bottom = bawahAwal + bars.bottom)
            insets
        }

        setupDaftar()
        setupFilter()

        findViewById<MaterialButton>(R.id.fabTambah).setOnClickListener {
            startActivity(Intent(this, PetugasPromoTambahActivity::class.java))
        }

        PromoStore.seedSekali(this)
    }

    override fun onStart() {
        super.onStart()
        promoListener = PromoStore.listen(
            this,
            onChange = { semuaPromo = it; render() },
            onError = { toast("Gagal memuat promo") }
        )
    }

    override fun onStop() {
        super.onStop()
        promoListener?.remove()
        promoListener = null
    }

    private fun setupDaftar() {
        adapter = PetugasPromoAdapter { promo ->
            startActivity(
                Intent(this, PetugasPromoDetailActivity::class.java)
                    .putExtra(PetugasPromoDetailActivity.EXTRA_PROMO_ID, promo.id)
            )
        }
        rvPromo = findViewById(R.id.rvPromo)
        rvPromo.adapter = adapter
        tvKosong = findViewById(R.id.tvKosong)
    }

    private fun setupFilter() {
        val chipGroup = findViewById<ChipGroup>(R.id.chipGroupKategori)

        for (i in 0 until chipGroup.childCount) {
            (chipGroup.getChildAt(i) as Chip).isCheckedIconVisible = false
        }

        chipGroup.check(R.id.chipSemua)
        chipGroup.setOnCheckedStateChangeListener { group, ids ->
            filter = when (ids.firstOrNull()) {
                R.id.chipServis -> KategoriPromo.SERVIS
                R.id.chipSparepart -> KategoriPromo.SPAREPART
                R.id.chipPoin -> KategoriPromo.POIN
                else -> null // chipSemua
            }
            warnaiChip(group)
            render()
        }
        warnaiChip(chipGroup)
    }

    /** Chip terpilih = biru tua, lainnya putih. */
    private fun warnaiChip(group: ChipGroup) {
        val navy = ContextCompat.getColor(this, R.color.bk_primary)
        for (i in 0 until group.childCount) {
            val c = group.getChildAt(i) as Chip
            c.chipBackgroundColor = ColorStateList.valueOf(if (c.isChecked) navy else Color.WHITE)
            c.setTextColor(if (c.isChecked) Color.WHITE else navy)
        }
    }

    private fun render() {
        val now = System.currentTimeMillis()
        val aktif = semuaPromo.filter { it.berakhir >= now }
        val list = filter?.let { f -> aktif.filter { it.kategori == f } } ?: aktif

        // Total di header mengikuti kategori yang dipilih (Semua = seluruh promo aktif)
        binding.tvTotal.text = "${list.size} promo aktif"
        adapter.submitList(list)
        tvKosong.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}