package id.ac.stmi.mobileproject

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class PetugasPromoActivity : AppCompatActivity() {

    private lateinit var adapter: PetugasPromoAdapter
    private lateinit var rvPromo: RecyclerView
    private lateinit var tvKosong: TextView

    private var filter: KategoriPromo? = KategoriPromo.SERVIS // chip Servis terpilih di awal
    private var semuaPromo: List<PromoItem> = emptyList()
    private var promoListener: PromoListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_promo_petugas)

        setupDaftar()
        setupFilter()

        findViewById<MaterialButton>(R.id.fabTambah).setOnClickListener {
            startActivity(Intent(this, PetugasPromoTambahActivity::class.java))
        }

        // Isi 4 promo contoh bila koleksi masih kosong
        PromoStore.seedSekali(this)
    }

    // Ikut berubah otomatis saat promo ditambah / dihapus
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
        chipGroup.check(R.id.chipServis)
        chipGroup.setOnCheckedStateChangeListener { group, ids ->
            filter = when (ids.firstOrNull()) {
                R.id.chipServis -> KategoriPromo.SERVIS
                R.id.chipSparepart -> KategoriPromo.SPAREPART
                R.id.chipPoin -> KategoriPromo.POIN
                else -> null
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
        adapter.submitList(list)
        tvKosong.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}