package id.ac.stmi.mobileproject

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/* ============================== MODEL ============================== */

enum class StatusVoucher(val label: String) {
    TERSEDIA("Tersedia"), DIPAKAI("Terpakai"), KEDALUWARSA("Kedaluwarsa")
}

/**
 * Voucher milik customer, lahir dari klaim promo.
 * Yang disimpan hanya TERSEDIA / DIPAKAI. KEDALUWARSA dihitung dari tanggal berakhir.
 */
data class VoucherSaya(
    val id: String,
    val promoId: String,
    val judul: String,
    val label: String,        // "25%", "Gratis", "Rp 50.000"
    val kode: String,
    val dipakai: Boolean,
    val tglKlaim: Long,
    val berakhir: Long
) {
    fun status(now: Long = System.currentTimeMillis()) = when {
        dipakai -> StatusVoucher.DIPAKAI
        berakhir < now -> StatusVoucher.KEDALUWARSA
        else -> StatusVoucher.TERSEDIA
    }
}

/* ============================== STORE ============================== */

/**
 * Sama seperti PromoStore: disimpan lokal (SharedPreferences, JSON).
 * Nanti bisa dipindah ke Firestore tanpa mengubah pemanggil.
 */
object VoucherStore {
    private const val PREF = "voucher_store"
    private const val KEY = "vouchers"

    enum class Hasil { SUKSES, SUDAH_DIKLAIM, BERAKHIR, GAGAL }

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun VoucherSaya.toJson() = JSONObject().apply {
        put("id", id); put("promoId", promoId); put("judul", judul); put("label", label)
        put("kode", kode); put("dipakai", dipakai); put("tglKlaim", tglKlaim); put("berakhir", berakhir)
    }

    private fun JSONObject.toVoucher() = VoucherSaya(
        getString("id"), getString("promoId"), getString("judul"), optString("label"),
        getString("kode"), optBoolean("dipakai", false), getLong("tglKlaim"), getLong("berakhir")
    )

    private fun baca(ctx: Context): List<VoucherSaya> = try {
        val raw = prefs(ctx).getString(KEY, null)
        if (raw == null) emptyList()
        else JSONArray(raw).let { a -> (0 until a.length()).map { a.getJSONObject(it).toVoucher() } }
    } catch (e: Exception) { emptyList() }

    private fun simpan(ctx: Context, data: List<VoucherSaya>) {
        val arr = JSONArray().also { a -> data.forEach { a.put(it.toJson()) } }
        prefs(ctx).edit().putString(KEY, arr.toString()).apply()
    }

    /** Panggil di onStart, .remove() di onStop. Langsung mengirim data saat ini. */
    fun listen(ctx: Context, onChange: (List<VoucherSaya>) -> Unit): PromoListener {
        val p = prefs(ctx)
        val l = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY) onChange(baca(ctx).sortedByDescending { it.tglKlaim })
        }
        p.registerOnSharedPreferenceChangeListener(l)
        onChange(baca(ctx).sortedByDescending { it.tglKlaim })
        return PromoListener { p.unregisterOnSharedPreferenceChangeListener(l) }
    }

    fun sudahDiklaim(ctx: Context, promoId: String) =
        baca(ctx).any { it.promoId == promoId }

    fun cekDiklaim(ctx: Context, promoId: String, onResult: (Boolean) -> Unit) {
        onResult(sudahDiklaim(ctx, promoId))
    }

    /** Dipanggil dari tombol Klaim di halaman detail promo. */
    fun klaim(ctx: Context, p: PromoItem, onDone: (Hasil) -> Unit) {
        if (p.berakhir < System.currentTimeMillis()) { onDone(Hasil.BERAKHIR); return }
        val data = baca(ctx)
        if (data.any { it.promoId == p.id }) { onDone(Hasil.SUDAH_DIKLAIM); return }
        val baru = VoucherSaya(
            id = UUID.randomUUID().toString(),
            promoId = p.id, judul = p.judul, label = p.labelDiskon, kode = p.kode,
            dipakai = false, tglKlaim = System.currentTimeMillis(), berakhir = p.berakhir
        )
        simpan(ctx, listOf(baru) + data)
        onDone(Hasil.SUKSES)
    }

    /** Panggil saat booking dikonfirmasi. */
    fun tandaiDipakai(ctx: Context, id: String) = ubah(ctx, id, true)

    /** Panggil bila booking dibatalkan. */
    fun kembalikan(ctx: Context, id: String) = ubah(ctx, id, false)

    private fun ubah(ctx: Context, id: String, dipakai: Boolean) =
        simpan(ctx, baca(ctx).map { if (it.id == id) it.copy(dipakai = dipakai) else it })

    /** Angka untuk kartu "Voucher saya" di beranda Promo. */
    fun jumlahTersedia(list: List<VoucherSaya>) =
        list.count { it.status() == StatusVoucher.TERSEDIA }
}

/* ============================== ADAPTER ============================== */

class VoucherAdapter(
    private val onSalin: (VoucherSaya) -> Unit,
    private val onPakai: (VoucherSaya) -> Unit
) : ListAdapter<VoucherSaya, VoucherAdapter.VH>(Diff) {

    class VH(
        v: View,
        val judul: TextView,
        val label: TextView,
        val kode: TextView,
        val berlaku: TextView,
        val status: TextView,
        val salin: TextView,
        val pakai: TextView
    ) : RecyclerView.ViewHolder(v)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        fun dp(n: Int) = (n * ctx.resources.displayMetrics.density).toInt()
        fun warna(id: Int) = ContextCompat.getColor(ctx, id)

        fun tombol(teks: String, bg: Int, fg: Int) = TextView(ctx).apply {
            text = teks
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_status_chip)
            backgroundTintList = ColorStateList.valueOf(bg)
            setTextColor(fg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(null, Typeface.BOLD)
        }

        val judul = TextView(ctx).apply {
            setTextColor(warna(R.color.bk_text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(null, Typeface.BOLD)
        }
        val label = TextView(ctx).apply {
            setBackgroundResource(R.drawable.bg_status_chip)
            backgroundTintList = ColorStateList.valueOf(warna(R.color.bk_accent))
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
        }
        val atas = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(judul, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
            addView(label)
        }

        val kode = TextView(ctx).apply {
            setTextColor(warna(R.color.bk_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(null, Typeface.BOLD)
        }
        val salin = tombol("Salin", Color.WHITE, warna(R.color.bk_accent)).apply {
            setPadding(dp(14), dp(6), dp(14), dp(6))
        }
        val barisKode = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundResource(R.drawable.bg_status_chip)
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F2F5FA"))
            addView(kode, LinearLayout.LayoutParams(0, -2, 1f))
            addView(salin)
        }

        val berlaku = TextView(ctx).apply {
            setTextColor(warna(R.color.bk_text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        val status = TextView(ctx).apply {
            setBackgroundResource(R.drawable.bg_status_chip)
            setPadding(dp(10), dp(3), dp(10), dp(3))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(null, Typeface.BOLD)
        }
        val barisInfo = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(berlaku, LinearLayout.LayoutParams(0, -2, 1f))
            addView(status)
        }

        val pakai = tombol("Pakai di Booking", warna(R.color.bk_primary), Color.WHITE)

        val isi = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(atas)
            addView(barisKode, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            addView(barisInfo, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            addView(pakai, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(12) })
        }
        val kartu = MaterialCardView(ctx).apply {
            layoutParams = RecyclerView.LayoutParams(-1, -2).apply {
                // Margin horizontal 0 karena XML sudah punya padding 16dp kiri/kanan
                setMargins(0, dp(6), 0, dp(6))
            }
            radius = dp(20).toFloat()
            cardElevation = dp(3).toFloat()
            strokeWidth = dp(1)
            strokeColor = warna(R.color.bk_stroke_soft)
            setCardBackgroundColor(Color.WHITE)
            addView(isi)
        }
        return VH(kartu, judul, label, kode, berlaku, status, salin, pakai)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val v = getItem(position)
        val st = v.status()
        h.judul.text = v.judul
        h.label.text = v.label
        h.kode.text = v.kode
        h.berlaku.text = "Berlaku s/d ${v.berakhir.toTanggal()}"
        h.status.text = st.label
        h.status.backgroundTintList = ColorStateList.valueOf(
            Color.parseColor(if (st == StatusVoucher.TERSEDIA) "#2E7D32" else "#9AA3AF")
        )
        h.salin.setOnClickListener { onSalin(v) }

        // Tombol pakai hanya aktif untuk voucher yang masih tersedia
        val aktif = st == StatusVoucher.TERSEDIA
        h.pakai.visibility = if (aktif) View.VISIBLE else View.GONE
        h.salin.visibility = if (aktif) View.VISIBLE else View.GONE
        h.pakai.setOnClickListener { onPakai(v) }
    }

    private object Diff : DiffUtil.ItemCallback<VoucherSaya>() {
        override fun areItemsTheSame(a: VoucherSaya, b: VoucherSaya) = a.id == b.id
        override fun areContentsTheSame(a: VoucherSaya, b: VoucherSaya) = a == b
    }
}

/* ============================== ACTIVITY ============================== */

class VoucherSayaActivity : AppCompatActivity() {

    private var filter = StatusVoucher.TERSEDIA
    private var semua: List<VoucherSaya> = emptyList()
    private var listener: PromoListener? = null
    private lateinit var adapter: VoucherAdapter
    private lateinit var tvKosong: LinearLayout
    private lateinit var tvPesanKosong: TextView
    private lateinit var tvTotalVoucher: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ganti nama layout jika file XML-mu berbeda
        setContentView(R.layout.activity_voucher_saya)

        adapter = VoucherAdapter(
            onSalin = { v -> salin(v.kode); toast("Kode ${v.kode} disalin") },
            onPakai = { v ->
                // TODO: ganti dengan intent ke halaman Booking milikmu, mis.
                // startActivity(Intent(this, BookingActivity::class.java)
                //     .putExtra("kodeVoucher", v.kode).putExtra("voucherId", v.id))
                // Sementara: kode disalin supaya bisa ditempel di Booking.
                salin(v.kode)
                toast("Kode disalin, tempel di halaman Booking")
            }
        )

        // Toolbar
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }

        tvTotalVoucher = findViewById(R.id.tvTotalVoucher)

        // Daftar voucher
        findViewById<RecyclerView>(R.id.rvVouchers).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@VoucherSayaActivity.adapter
        }

        // Tampilan kosong (TextView di dalamnya tidak punya id, diambil lewat urutan child:
        // child 0 = ImageView, child 1 = TextView)
        tvKosong = findViewById(R.id.tvKosong)
        tvPesanKosong = tvKosong.getChildAt(1) as TextView

        // Tab filter: urutan tab = urutan enum (Tersedia, Terpakai, Kedaluwarsa)
        val tabFilter = findViewById<TabLayout>(R.id.tabFilter)
        tabFilter.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                filter = StatusVoucher.values().getOrElse(tab.position) { StatusVoucher.TERSEDIA }
                render()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    override fun onStart() {
        super.onStart()
        listener = VoucherStore.listen(this) { semua = it; render() }
    }

    override fun onStop() {
        super.onStop()
        listener?.remove()
        listener = null
    }

    private fun render() {
        val list = semua.filter { it.status() == filter }
        adapter.submitList(list)

        tvTotalVoucher.text = "${VoucherStore.jumlahTersedia(semua)} voucher"

        tvPesanKosong.text = when (filter) {
            StatusVoucher.TERSEDIA -> "Belum ada voucher tersedia.\nKlaim promo di halaman Promo."
            StatusVoucher.DIPAKAI -> "Belum ada voucher yang terpakai."
            StatusVoucher.KEDALUWARSA -> "Tidak ada voucher kedaluwarsa."
        }
        tvKosong.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun salin(teks: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Kode voucher", teks))
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}