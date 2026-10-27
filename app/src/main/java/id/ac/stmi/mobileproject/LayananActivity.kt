package id.ac.stmi.mobileproject

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import id.ac.stmi.mobileproject.databinding.ActivityLayananBinding
import id.ac.stmi.mobileproject.databinding.ItemSvcServiceBinding

/**
 * Halaman Layanan: daftar layanan servis, cari, filter kategori, dan pilih layanan.
 * Layout : res/layout/activity_layanan.xml  (ViewBinding -> ActivityLayananBinding)
 * Kartu  : res/layout/item_svc_service.xml  (ViewBinding -> ItemSvcServiceBinding)
 * Data   : Firestore koleksi "layanan" (lewat LayananCatalog.kt), realtime, hanya-baca.
 *          Layanan diinput oleh petugas; customer hanya melihat yang berstatus aktif.
 */
class LayananActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLayananBinding
    private val auth by lazy { FirebaseAuth.getInstance() }

    // --- state halaman ---
    private var kategori: LayananKategori? = null      // null = chip "Semua"
    private var kataKunci = ""                          // isi kolom cari
    private val dipilih = linkedSetOf<String>()         // id layanan yang dipilih
    private val tombolBooking = mutableMapOf<String, MaterialButton>() // id -> tombol di kartunya
    private var sudahMuat = false                       // true setelah data Firestore pertama tiba

    // Menerima hasil dari halaman Detail: kalau user menekan "Booking Layanan Ini",
    // layanan tersebut langsung masuk ke daftar pilihan.
    private val launcherDetail =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { hasil ->
            val id = hasil.data?.getStringExtra(EXTRA_LAYANAN_ID)
            if (hasil.resultCode == RESULT_OK && id != null) {
                dipilih.add(id)
                tombolBooking[id]?.let { gayaTombol(it, true) }
                perbaruiBar()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLayananBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false

        val atasAwal = binding.header.paddingTop
        val bawahAwal = binding.main.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.header.updatePadding(top = atasAwal + bars.top)
            binding.main.updatePadding(bottom = bawahAwal + bars.bottom)
            insets
        }

        // Belum login -> ke halaman login
        val uid = auth.currentUser?.uid
        if (uid == null) {
            startActivity(
                Intent(this, LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            finish()
            return
        }

        setupKendaraan(uid)
        setupCari()
        setupChip()
        setupKlik()

        // Sembunyikan "Layanan tidak ditemukan" sampai data Firestore selesai dimuat
        binding.tvKosong.visibility = View.GONE
        perbaruiBar()
        setupLayanan()

        binding.btnBack.setOnClickListener { finish() }
    }

    // ------------------------------------------------------------------
    // 0. DATA LAYANAN DARI FIRESTORE (realtime)
    // ------------------------------------------------------------------
    private fun setupLayanan() {
        LayananCatalog.listenSemua(
            this,
            onError = { toast("Gagal memuat layanan: ${it.message}") }
        ) {
            sudahMuat = true
            binding.chipSemua.text = "${getString(R.string.svc_semua)}  ${it.size}"
            perbaruiChipKategori(it)
            tampilkanDaftar()
            perbaruiBar() // harga/nama layanan terpilih ikut diperbarui
        }
    }

    /**
     * Chip kategori hanya tampil kalau ada layanan aktif di kategori itu.
     * Kalau kategori yang sedang dipilih jadi kosong, kembali ke "Semua".
     */
    private fun perbaruiChipKategori(list: List<LayananItem>) {
        val ada = list.map { it.kategori }.toSet()
        mapOf(
            binding.chipBerkala to LayananKategori.BERKALA,
            binding.chipOli to LayananKategori.OLI,
            binding.chipRem to LayananKategori.REM,
            binding.chipAc to LayananKategori.AC,
            binding.chipSpooring to LayananKategori.SPOORING,
            binding.chipLainnya to LayananKategori.LAINNYA
        ).forEach { (chip, k) ->
            chip.visibility = if (k in ada) View.VISIBLE else View.GONE
        }
        if (kategori != null && kategori !in ada) binding.chipSemua.isChecked = true
    }

    // ------------------------------------------------------------------
    // 1. KENDARAAN ("Servis untuk: ...")
    // ------------------------------------------------------------------
    private fun setupKendaraan(uid: String) {
        listenKendaraanUtama(uid) { v ->
            if (v == null) {
                binding.tvVehicleName.setText(R.string.svc_belum_ada_mobil)
                binding.tvPlate.visibility = View.GONE
            } else {
                binding.tvVehicleName.text = v.displayName
                binding.tvPlate.text = v.plateNumber
                binding.tvPlate.visibility = View.VISIBLE
            }
        }
    }

    // ------------------------------------------------------------------
    // 2. CARI
    // ------------------------------------------------------------------
    private fun setupCari() {
        // doAfterTextChanged dipanggil setiap isi kolom berubah
        binding.etSearch.doAfterTextChanged {
            kataKunci = it?.toString().orEmpty()
            tampilkanDaftar()
        }
    }

    // ------------------------------------------------------------------
    // 3. CHIP KATEGORI
    // ------------------------------------------------------------------
    private fun semuaChip() = listOf(
        binding.chipSemua, binding.chipBerkala, binding.chipOli,
        binding.chipRem, binding.chipAc, binding.chipSpooring, binding.chipLainnya
    )

    private fun setupChip() {
        binding.chipSemua.text = getString(R.string.svc_semua)
        warnaiChip(binding.chipSemua.id)

        binding.chipGroupKategori.setOnCheckedStateChangeListener { _, ids ->
            val id = ids.firstOrNull() ?: return@setOnCheckedStateChangeListener
            kategori = when (id) {
                R.id.chipBerkala -> LayananKategori.BERKALA
                R.id.chipOli -> LayananKategori.OLI
                R.id.chipRem -> LayananKategori.REM
                R.id.chipAc -> LayananKategori.AC
                R.id.chipSpooring -> LayananKategori.SPOORING
                R.id.chipLainnya -> LayananKategori.LAINNYA
                else -> null
            }
            warnaiChip(id)
            tampilkanDaftar()
        }
    }

    /** Chip terpilih = biru tua + teks putih; lainnya putih + teks biru tua. */
    private fun warnaiChip(idTerpilih: Int) {
        val primary = ContextCompat.getColor(this, R.color.bk_primary)
        semuaChip().forEach { chip ->
            val aktif = chip.id == idTerpilih
            chip.chipBackgroundColor = ColorStateList.valueOf(if (aktif) primary else Color.WHITE)
            chip.setTextColor(if (aktif) Color.WHITE else primary)
        }
    }

    // ------------------------------------------------------------------
    // 4. DAFTAR LAYANAN
    // ------------------------------------------------------------------
    private fun tampilkanDaftar() {
        val hasil = LayananCatalog.cari(kategori, kataKunci)

        binding.listServices.removeAllViews()
        tombolBooking.clear()
        hasil.forEach { binding.listServices.addView(buatKartu(it)) }

        // Beda pesan: petugas belum input layanan sama sekali vs hasil cari/filter kosong
        binding.tvKosong.setText(
            if (LayananCatalog.semua.isEmpty()) R.string.svc_belum_ada_layanan else R.string.svc_kosong
        )
        binding.tvKosong.visibility =
            if (sudahMuat && hasil.isEmpty()) View.VISIBLE else View.GONE
    }

    /** Membuat satu kartu dari item_svc_service.xml lalu mengisinya dengan data layanan. */
    private fun buatKartu(s: LayananItem): View {
        val row = ItemSvcServiceBinding.inflate(layoutInflater, binding.listServices, false)

        // Badge + durasi
        row.tvBadge.text = s.badge
        row.tvBadge2.text = s.badge2
        row.tvBadge2.visibility = if (s.badge2 != null) View.VISIBLE else View.GONE
        // Kalau ada 2 badge, durasi pindah ke baris sendiri supaya tidak sempit
        val duaBadge = s.badge2 != null
        row.tvDurationInline.text = s.durasi
        row.tvDurationBelow.text = s.durasi
        val adaDurasi = s.durasi.isNotBlank()
        row.tvDurationInline.visibility = if (adaDurasi && !duaBadge) View.VISIBLE else View.GONE
        row.tvDurationBelow.visibility = if (adaDurasi && duaBadge) View.VISIBLE else View.GONE
        // Badge kosong (petugas belum mengisi) -> sembunyikan
        row.tvBadge.visibility = if (s.badge.isBlank()) View.GONE else View.VISIBLE

        // Judul, deskripsi, ikon
        row.tvTitle.text = s.judul
        row.tvDesc.text = s.deskripsi
        row.ivThumb.setImageResource(s.ikon)

        // Cakupan servis (hanya kalau ada)
        if (s.cakupan.isNotEmpty()) {
            row.layoutCakupan.visibility = View.VISIBLE
            s.cakupan.forEach { teks ->
                val baris = layoutInflater.inflate(R.layout.item_svc_check, row.layoutCheckList, false) as TextView
                baris.text = teks
                row.layoutCheckList.addView(baris)
            }
        }

        // Harga: kalau ada harga coret, label "Estimasi Total" disembunyikan
        if (s.hargaCoret != null) {
            row.tvPriceOld.text = s.hargaCoret.keRupiah()
            row.tvPriceOld.paintFlags = row.tvPriceOld.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            row.tvPriceOld.visibility = View.VISIBLE
            row.tvPriceLabel.visibility = View.GONE
        }
        row.tvPrice.text = s.hargaTampil

        // Tombol Detail -> halaman DetailLayananActivity
        row.btnDetail.setOnClickListener {
            launcherDetail.launch(
                Intent(this, DetailLayananActivity::class.java)
                    .putExtra(EXTRA_LAYANAN_ID, s.id)
            )
        }

        // Tombol Booking -> pilih / batal pilih
        tombolBooking[s.id] = row.btnBooking
        gayaTombol(row.btnBooking, dipilih.contains(s.id))
        row.btnBooking.setOnClickListener {
            val sekarangDipilih = if (dipilih.contains(s.id)) {
                dipilih.remove(s.id); false
            } else {
                dipilih.add(s.id); true
            }
            gayaTombol(row.btnBooking, sekarangDipilih)
            perbaruiBar()
        }
        return row.root
    }

    /** Tombol oranye "+ Booking" saat belum dipilih, biru tua "✓ Dipilih" saat dipilih. */
    private fun gayaTombol(btn: MaterialButton, aktif: Boolean) {
        btn.setText(if (aktif) R.string.svc_dipilih else R.string.svc_booking)
        btn.setIconResource(if (aktif) R.drawable.ic_svc_check else R.drawable.ic_svc_add)
        val warna = if (aktif) R.color.bk_primary else R.color.bk_accent
        btn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, warna))
    }

    // ------------------------------------------------------------------
    // 5. BAR "N LAYANAN DIPILIH"
    // ------------------------------------------------------------------
    private fun perbaruiBar() {
        val item = dipilih.mapNotNull { LayananCatalog.find(it) }
        binding.barSelection.visibility = if (item.isEmpty()) View.GONE else View.VISIBLE
        binding.tvSelCount.text = "${item.size} Layanan Dipilih"
        binding.tvSelTotal.text = "Total: ${item.sumOf { it.harga }.keRupiah()}"
    }

    // ------------------------------------------------------------------
    // 6. KLIK TOMBOL
    // ------------------------------------------------------------------
    private fun setupKlik() {
        binding.btnChangeCar.setOnClickListener {
            startActivity(Intent(this, MyVehiclesActivityCustomer::class.java))
        }
        binding.btnFilter.setOnClickListener { toast("Filter lanjutan belum tersedia") }
        binding.btnNext.setOnClickListener {
            if (dipilih.isEmpty()) {
                toast("Pilih minimal satu layanan")
                return@setOnClickListener
            }
            startActivity(
                Intent(this, BookingActivityCustomer::class.java)
                    .putStringArrayListExtra(
                        BookingActivityCustomer.EXTRA_SERVICE_IDS,
                        ArrayList(dipilih)
                    )
            )
        }
    }



    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
