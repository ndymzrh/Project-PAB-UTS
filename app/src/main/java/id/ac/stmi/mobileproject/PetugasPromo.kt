package id.ac.stmi.mobileproject

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

enum class KategoriPromo(val label: String) {
    SERVIS("Servis"), SPAREPART("Sparepart"), POIN("Poin")
}

/** Satu promo. Field disamakan dengan data promo di sisi customer. */
data class PromoItem(
    val id: String,
    val judul: String,
    val kategori: KategoriPromo,
    val kode: String,
    val jenisDiskon: String,      // "Persen" atau "Nominal"
    val nilai: Long,
    val mulai: Long,
    val berakhir: Long,
    val berlaku: String,
    val syarat: String,           // satu syarat per baris
    val gambar: String = "bg_promo1" // nama drawable
) {
    val labelDiskon: String
        get() = when {
            jenisDiskon == "Persen" && nilai >= 100 -> "Gratis"
            jenisDiskon == "Persen" -> "$nilai%"
            else -> "Rp " + "%,d".format(nilai).replace(',', '.')
        }
}

fun Long.toTanggal(): String =
    SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).format(Date(this))

fun gambarDefault(k: KategoriPromo) = when (k) {
    KategoriPromo.SERVIS -> "bg_promo1"
    KategoriPromo.POIN -> "bg_promo2"
    KategoriPromo.SPAREPART -> "bg_promo3"
}

/** Penanda listener agar pemanggil cukup memanggil .remove() di onStop. */
class PromoListener(private val stop: () -> Unit) {
    fun remove() = stop()
}

/**
 * Promo disimpan lokal di SharedPreferences (JSON). Petugas menulis, customer membaca
 * di HP yang sama; listener ikut berubah saat data berubah.
 * CATATAN: tidak tersinkron antar HP. Untuk itu pindahkan isi object ini ke Firestore
 * (versi Firestore ada di file *.firestore.bak); pemanggilnya tidak perlu diubah.
 */
object PromoStore {
    private const val PREF = "promo_store"
    private const val KEY = "promos"
    private const val KEY_SEED = "seeded"

    enum class Hasil { SUKSES, KODE_ADA, GAGAL }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun PromoItem.toJson() = JSONObject().apply {
        put("id", id); put("judul", judul); put("kategori", kategori.name)
        put("kode", kode); put("jenis", jenisDiskon); put("nilai", nilai)
        put("mulai", mulai); put("berakhir", berakhir); put("berlaku", berlaku)
        put("syarat", syarat); put("gambar", gambar)
    }

    private fun JSONObject.toPromo() = PromoItem(
        getString("id"), getString("judul"),
        KategoriPromo.valueOf(getString("kategori")), getString("kode"),
        getString("jenis"), getLong("nilai"), getLong("mulai"), getLong("berakhir"),
        optString("berlaku"), optString("syarat"), optString("gambar", "bg_promo1")
    )

    private fun baca(ctx: Context): List<PromoItem> = try {
        val raw = prefs(ctx).getString(KEY, null)
        if (raw == null) emptyList()
        else JSONArray(raw).let { a -> (0 until a.length()).map { a.getJSONObject(it).toPromo() } }
    } catch (e: Exception) { emptyList() }

    private fun simpan(ctx: Context, data: List<PromoItem>) {
        val arr = JSONArray().also { a -> data.forEach { a.put(it.toJson()) } }
        prefs(ctx).edit().putString(KEY, arr.toString()).apply()
    }

    /** Panggil di onStart, lalu .remove() di onStop. Langsung mengirim data saat ini. */
    fun listen(ctx: Context, onChange: (List<PromoItem>) -> Unit, onError: () -> Unit = {}): PromoListener {
        val p = prefs(ctx)
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY) onChange(baca(ctx).sortedByDescending { it.mulai })
        }
        p.registerOnSharedPreferenceChangeListener(l)
        onChange(baca(ctx).sortedByDescending { it.mulai })
        return PromoListener { p.unregisterOnSharedPreferenceChangeListener(l) }
    }

    fun get(ctx: Context, id: String, onResult: (PromoItem?) -> Unit) =
        onResult(baca(ctx).firstOrNull { it.id == id })

    fun tambah(ctx: Context, p: PromoItem, onDone: (Hasil) -> Unit) {
        val data = baca(ctx)
        if (data.any { it.kode.equals(p.kode, ignoreCase = true) }) { onDone(Hasil.KODE_ADA); return }
        simpan(ctx, listOf(p) + data)
        onDone(Hasil.SUKSES)
    }

    fun hapus(ctx: Context, id: String, onDone: (Boolean) -> Unit) {
        simpan(ctx, baca(ctx).filterNot { it.id == id })
        onDone(true)
    }

    /** Isi 4 promo contoh SEKALI SAJA (ditandai flag). Dipanggil dari sisi petugas. */
    fun seedSekali(ctx: Context) {
        val p = prefs(ctx)
        if (p.getBoolean(KEY_SEED, false)) return
        val ada = baca(ctx)
        val baru = dataAwal().filter { s -> ada.none { it.kode.equals(s.kode, ignoreCase = true) } }
        simpan(ctx, baru + ada)
        p.edit().putBoolean(KEY_SEED, true).apply()
    }

    private fun tgl(y: Int, m: Int, d: Int) =
        Calendar.getInstance().apply { clear(); set(y, m, d, 23, 59) }.timeInMillis

    private fun dataAwal(): List<PromoItem> {
        val mulai = tgl(2026, Calendar.OCTOBER, 1)
        val syarat = "Berlaku di seluruh cabang Annova."
        return listOf(
            PromoItem("SERVISHEMAT25", "Diskon 25% Servis Berkala", KategoriPromo.SERVIS, "SERVISHEMAT25",
                "Persen", 25, mulai, tgl(2026, Calendar.DECEMBER, 31), "Servis berkala",
                "Berlaku untuk kendaraan jenis mobil penumpang.\nMinimal transaksi servis Rp 200.000.\nTidak dapat digabung dengan promo lain.\n$syarat",
                gambar = "bg_promo1"),
            PromoItem("POINSERVIS50", "Voucher Servis Poin Loyalitas", KategoriPromo.POIN, "POINSERVIS50",
                "Nominal", 50000, mulai, tgl(2026, Calendar.DECEMBER, 31), "Poin loyalitas",
                "Voucher senilai Rp 50.000 untuk transaksi servis.\nDitukar dengan poin loyalitas yang tersedia.\nTidak dapat diuangkan.\n$syarat",
                gambar = "bg_promo2"),
            PromoItem("REMTUNE10", "Paket Kampas Rem & Tune-up", KategoriPromo.SPAREPART, "REMTUNE10",
                "Persen", 10, mulai, tgl(2027, Calendar.FEBRUARY, 28), "Kampas rem, tune-up",
                "Diskon 10% untuk paket kampas rem dan tune-up.\nBerlaku untuk kendaraan jenis mobil penumpang.\nTidak dapat digabung dengan promo lain.\n$syarat",
                gambar = "bg_promo3"),
            PromoItem("CEKACGRATIS", "Gratis Cek AC Mobil", KategoriPromo.SERVIS, "CEKACGRATIS",
                "Persen", 100, mulai, tgl(2027, Calendar.JANUARY, 15), "Cek AC",
                "Pengecekan AC gratis tanpa minimal transaksi.\nSatu kali pemakaian per kendaraan.\nTidak termasuk biaya perbaikan atau penggantian part.\n$syarat",
                gambar = "bg_promo4")
        )
    }
}

/** Ambil id drawable dari nama; jatuh ke bg_promo1 bila tidak ada. */
fun Context.drawableId(nama: String): Int {
    val id = resources.getIdentifier(nama, "drawable", packageName)
    return if (id != 0) id else R.drawable.bg_promo1
}
class PetugasPromoAdapter(private val onClick: (PromoItem) -> Unit) :
    ListAdapter<PromoItem, PetugasPromoAdapter.VH>(Diff) {

    class VH(
        v: View,
        val img: ImageView,
        val label: TextView,
        val judul: TextView,
        val berlaku: TextView,
        val detail: TextView
    ) : RecyclerView.ViewHolder(v)

    // Kartu dibuat lewat kode, jadi tidak perlu file layout item
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        fun dp(n: Int) = (n * ctx.resources.displayMetrics.density).toInt()
        fun warna(id: Int) = ContextCompat.getColor(ctx, id)

        val img = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        val label = TextView(ctx).apply {
            setBackgroundResource(R.drawable.bg_status_chip)
            backgroundTintList = ColorStateList.valueOf(warna(R.color.bk_accent))
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
        }
        val thumb = FrameLayout(ctx).apply {
            addView(img, FrameLayout.LayoutParams(-1, -1))
            addView(label, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START)
                .apply { setMargins(dp(10), 0, 0, dp(8)) })
        }
        val judul = TextView(ctx).apply {
            minLines = 2; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
            setTextColor(warna(R.color.bk_text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(null, Typeface.BOLD)
        }
        val berlaku = TextView(ctx).apply {
            setTextColor(warna(R.color.bk_text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        val detail = TextView(ctx).apply {
            text = "Lihat Detail"
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_status_chip)
            backgroundTintList = ColorStateList.valueOf(warna(R.color.bk_primary))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
        }
        val isi = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(judul)
            addView(berlaku, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) })
            addView(detail, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(10) })
        }
        val kolom = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(thumb, LinearLayout.LayoutParams(-1, dp(90)))
            addView(isi)
        }
        val kartu = MaterialCardView(ctx).apply {
            layoutParams = RecyclerView.LayoutParams(-1, -2).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) }
            radius = dp(20).toFloat()
            cardElevation = dp(3).toFloat()
            strokeWidth = dp(1)
            strokeColor = warna(R.color.bk_stroke_soft)
            setCardBackgroundColor(Color.WHITE)
            isClickable = true
            isFocusable = true
            addView(kolom)
        }
        return VH(kartu, img, label, judul, berlaku, detail)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val p = getItem(position)
        val ctx = h.itemView.context
        h.img.setImageResource(ctx.drawableId(p.gambar))
        h.label.text = p.labelDiskon
        h.judul.text = p.judul
        h.berlaku.text = "s/d ${p.berakhir.toTanggal()}"
        h.itemView.setOnClickListener { onClick(p) }
        h.detail.setOnClickListener { onClick(p) }
    }

    private object Diff : DiffUtil.ItemCallback<PromoItem>() {
        override fun areItemsTheSame(a: PromoItem, b: PromoItem) = a.id == b.id
        override fun areContentsTheSame(a: PromoItem, b: PromoItem) = a == b
    }
}
class PetugasPromoActivity : AppCompatActivity() {

    private var filter: KategoriPromo? = KategoriPromo.SERVIS // chip Servis terpilih di awal
    private lateinit var adapter: PetugasPromoAdapter
    private lateinit var rvPromo: RecyclerView
    private lateinit var tvKosong: TextView
    private var semuaPromo: List<PromoItem> = emptyList()
    private var promoListener: PromoListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_promo_petugas)

        adapter = PetugasPromoAdapter { promo ->
            startActivity(
                Intent(this, PetugasPromoDetailActivity::class.java)
                    .putExtra(PetugasPromoDetailActivity.EXTRA_PROMO_ID, promo.id)
            )
        }
        siapkanDaftar()
        PromoStore.seedSekali(this) // isi 4 promo contoh, hanya sekali

        val chipGroup = findViewById<ChipGroup>(R.id.chipGroupKategori)
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
        findViewById<MaterialButton>(R.id.fabTambah).setOnClickListener {
            startActivity(Intent(this, PetugasPromoTambahActivity::class.java))
        }

        setupBottomNav()
    }

    // Otomatis ikut saat promo ditambah / dihapus
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

    private fun siapkanDaftar() {
        rvPromo = findViewById(R.id.rvPromo)
        rvPromo.adapter = adapter
        tvKosong = findViewById(R.id.tvKosong)
    }

    // Chip terpilih = biru tua, lainnya putih (warna di XML bersifat tetap)
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

    private fun setupBottomNav() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        bottomNav.selectedItemId = R.id.nav_promo // sebelum listener
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(
                        Intent(this, HomeActivityPetugas::class.java)
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
        // Tap Promo lagi = kembali ke atas daftar
        bottomNav.setOnItemReselectedListener { findViewById<NestedScrollView>(R.id.scrollPromo).smoothScrollTo(0, 0) }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
class PetugasPromoDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail_promo_petugas)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        setupBottomNav()

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
                .setPositiveButton("Hapus") { _, _ ->
                    PromoStore.hapus(this, promo.id) { ok ->
                        toast(if (ok) "Promo dihapus" else "Gagal menghapus promo")
                        if (ok) finish()
                    }
                }
                .show()
        }
    }

    private fun setupBottomNav() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)

        bottomNav.selectedItemId = R.id.nav_promo // sebelum listener
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(
                        Intent(this, HomeActivityPetugas::class.java)
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

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_PROMO_ID = "promoId"
    }
}
class PetugasPromoTambahActivity : AppCompatActivity() {

    private var mulai: Long? = null
    private var berakhir: Long? = null

    private fun til(id: Int) = findViewById<TextInputLayout>(id)
    private fun et(id: Int) = findViewById<TextInputEditText>(id)
    private fun dd(id: Int) = findViewById<AutoCompleteTextView>(id)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_promo)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        dd(R.id.etKategori).setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1,
            KategoriPromo.values().map { it.label }))
        dd(R.id.etJenis).setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1,
            listOf("Persen", "Nominal")))
        et(R.id.etMulai).setOnClickListener {
            pilihTanggal { mulai = it; et(R.id.etMulai).setText(it.toTanggal()) }
        }
        et(R.id.etBerakhir).setOnClickListener {
            pilihTanggal { berakhir = it; et(R.id.etBerakhir).setText(it.toTanggal()) }
        }
        findViewById<MaterialButton>(R.id.btnSimpan).setOnClickListener { simpan() }
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

        val btn = findViewById<MaterialButton>(R.id.btnSimpan)
        btn.isEnabled = false
        PromoStore.tambah(
            this,
            PromoItem(kode, judul, kategori!!, kode, jenis, nilai!!, mulai!!, berakhir!!, berlaku, syarat,
                gambarDefault(kategori))
        ) { hasil ->
            btn.isEnabled = true
            when (hasil) {
                PromoStore.Hasil.SUKSES -> {
                    Toast.makeText(this, "Promo disimpan", Toast.LENGTH_SHORT).show()
                    finish()
                }
                PromoStore.Hasil.KODE_ADA -> til(R.id.tilKode).error = "Kode sudah dipakai"
                PromoStore.Hasil.GAGAL ->
                    Toast.makeText(this, "Gagal menyimpan, periksa koneksi", Toast.LENGTH_SHORT).show()
            }
        }
    }
}