package id.ac.stmi.mobileproject

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class PetugasPromoTambahActivity : AppCompatActivity() {

    private lateinit var btnSimpan: MaterialButton

    private var mulai: Long? = null
    private var berakhir: Long? = null

    private fun til(id: Int) = findViewById<TextInputLayout>(id)
    private fun et(id: Int) = findViewById<TextInputEditText>(id)
    private fun dd(id: Int) = findViewById<AutoCompleteTextView>(id)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_promo)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        btnSimpan = findViewById(R.id.btnSimpan)
        btnSimpan.setOnClickListener { simpan() }

        setupDropdown()
        setupTanggal()
    }

    private fun setupDropdown() {
        dd(R.id.etKategori).setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1,
            KategoriPromo.values().map { it.label }))
        dd(R.id.etJenis).setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1,
            listOf("Persen", "Nominal")))
    }

    private fun setupTanggal() {
        et(R.id.etMulai).setOnClickListener {
            pilihTanggal { mulai = it; et(R.id.etMulai).setText(it.toTanggal()) }
        }
        et(R.id.etBerakhir).setOnClickListener {
            pilihTanggal { berakhir = it; et(R.id.etBerakhir).setText(it.toTanggal()) }
        }
    }

    private fun pilihTanggal(onPick: (Long) -> Unit) {
        val picker = MaterialDatePicker.Builder.datePicker().build()
        picker.addOnPositiveButtonClickListener { onPick(it) }
        picker.show(supportFragmentManager, "tanggal")
    }

    private fun simpan() {
        val judul = et(R.id.etJudul).text.toString().trim()
        val kode = et(R.id.etKode).text.toString().trim().uppercase()
        val kategori = KategoriPromo.values().firstOrNull { it.label == dd(R.id.etKategori).text.toString() }
        val jenis = dd(R.id.etJenis).text.toString()
        val nilai = et(R.id.etNilai).text.toString().toLongOrNull()
        val berlaku = et(R.id.etBerlaku).text.toString().trim()
        val syarat = et(R.id.etSyarat).text.toString().trim()

        listOf(R.id.tilJudul, R.id.tilKategori, R.id.tilKode, R.id.tilJenis, R.id.tilNilai,
            R.id.tilMulai, R.id.tilBerakhir, R.id.tilBerlaku, R.id.tilSyarat)
            .forEach { til(it).error = null }

        var ok = true
        fun salah(id: Int, pesan: String) { til(id).error = pesan; ok = false }

        if (judul.isEmpty()) salah(R.id.tilJudul, "Judul wajib diisi")
        if (kategori == null) salah(R.id.tilKategori, "Pilih kategori")
        if (kode.isEmpty()) salah(R.id.tilKode, "Kode wajib diisi")
        else if (kode.contains('/')) salah(R.id.tilKode, "Kode tidak boleh memuat '/'")
        if (jenis.isEmpty()) salah(R.id.tilJenis, "Pilih jenis diskon")
        if (nilai == null || nilai <= 0) salah(R.id.tilNilai, "Isi nilai diskon")
        else if (jenis == "Persen" && nilai > 100) salah(R.id.tilNilai, "Maksimal 100%")
        if (mulai == null) salah(R.id.tilMulai, "Pilih tanggal mulai")
        if (berakhir == null) salah(R.id.tilBerakhir, "Pilih tanggal berakhir")
        else if (mulai != null && berakhir!! <= mulai!!) salah(R.id.tilBerakhir, "Harus setelah tanggal mulai")
        if (berlaku.isEmpty()) salah(R.id.tilBerlaku, "Wajib diisi")
        if (syarat.isEmpty()) salah(R.id.tilSyarat, "Wajib diisi")
        if (!ok) return

        btnSimpan.isEnabled = false // cegah tap ganda
        PromoStore.tambah(
            this,
            PromoItem(kode, judul, kategori!!, kode, jenis, nilai!!, mulai!!, berakhir!!, berlaku, syarat,
                gambarDefault(kategori))
        ) { hasil, errorMsg ->
            btnSimpan.isEnabled = true
            when (hasil) {
                PromoStore.Hasil.SUKSES -> {
                    toast("Promo disimpan")
                    finish()
                }
                PromoStore.Hasil.KODE_ADA -> til(R.id.tilKode).error = "Kode sudah dipakai"
                PromoStore.Hasil.GAGAL -> {
                    val msg = if (!errorMsg.isNullOrEmpty()) "Gagal menyimpan: $errorMsg" else "Gagal menyimpan, periksa koneksi"
                    toast(msg)
                }
            }
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}