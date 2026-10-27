package id.ac.stmi.mobileproject

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PetugasPromoDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail_promo_petugas)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        // Data dari PromoStore, sama dengan yang dilihat customer
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

        findViewById<MaterialButton>(R.id.btnHapus).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Hapus promo ini?")
                .setMessage("\"${promo.judul}\" akan dihapus dari daftar dan tidak tampil lagi untuk customer. Tindakan ini tidak dapat dibatalkan.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Hapus") { _, _ -> hapus(promo) }
                .show()
        }
    }

    private fun hapus(promo: PromoItem) {
        PromoStore.hapus(this, promo.id) { ok ->
            toast(if (ok) "Promo dihapus" else "Gagal menghapus promo")
            if (ok) finish()
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_PROMO_ID = "promoId"
    }
}