package id.ac.stmi.mobileproject

import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import id.ac.stmi.mobileproject.databinding.ActivityBengkelListCustomerBinding

/**
 * Daftar bengkel untuk pelanggan (tampilan list), pasangan dari peta (BengkelActivityCustomer).
 * Data sama: koleksi "bengkel", hanya yang aktif. Bengkel terdekat di urutan pertama.
 *
 * Mode pilih (untuk booking): buka dengan putExtra(BengkelActivityCustomer.EXTRA_MODE_PILIH, true).
 * Hasil: RESULT_ID & RESULT_NAMA dari BengkelActivityCustomer, sama seperti halaman peta.
 */
class BengkelListActivityCustomer : AppCompatActivity() {

    private lateinit var binding: ActivityBengkelListCustomerBinding
    private lateinit var adapter: BengkelListAdapter
    private val db by lazy { FirebaseFirestore.getInstance() }
    private var listener: ListenerRegistration? = null

    private var modePilih = false
    private var semua: List<Bengkel> = emptyList()
    private var lokasiSaya: Location? = null
    private var locationListener: LocationListener? = null

    private val izinLokasi = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { hasil ->
        if (hasil.values.any { it }) {
            cariLokasiSaya()
            mulaiPantauLokasi()
        } else {
            toast("Izin lokasi ditolak, jarak ke bengkel tidak bisa ditampilkan")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityBengkelListCustomerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Header putih -> ikon status bar gelap
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = true

        val atasAwal = binding.header.paddingTop
        val bawahAwal = binding.rvBengkel.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.header.updatePadding(top = atasAwal + bars.top)
            binding.rvBengkel.updatePadding(bottom = bawahAwal + bars.bottom)
            insets
        }

        modePilih = intent.getBooleanExtra(BengkelActivityCustomer.EXTRA_MODE_PILIH, false)
        binding.tvJudul.text = if (modePilih) "Pilih Bengkel" else "Lokasi Bengkel"

        adapter = BengkelListAdapter(
            modePilih = modePilih,
            onNavigasi = { bukaPeta(this, it) },
            onPilih = { b ->
                setResult(
                    RESULT_OK,
                    Intent()
                        .putExtra(BengkelActivityCustomer.RESULT_ID, b.id)
                        .putExtra(BengkelActivityCustomer.RESULT_NAMA, b.nama)
                )
                finish()
            }
        )
        binding.rvBengkel.layoutManager = LinearLayoutManager(this)
        binding.rvBengkel.adapter = adapter

        binding.btnBack.setOnClickListener { finish() }
        binding.btnPeta.setOnClickListener { keTampilanPeta() }
        binding.etCari.doAfterTextChanged { tampilkan() }

        if (LokasiHelper.punyaIzin(this)) cariLokasiSaya()
        else izinLokasi.launch(LokasiHelper.IZIN)

        muatBengkel()
    }

    /** Pindah ke peta; FORWARD_RESULT supaya hasil mode pilih tetap sampai ke pemanggil. */
    private fun keTampilanPeta() {
        startActivity(
            Intent(this, BengkelActivityCustomer::class.java)
                .putExtra(BengkelActivityCustomer.EXTRA_MODE_PILIH, modePilih)
                .addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
        )
        finish()
    }

    /** Semua bengkel secara realtime; yang ditampilkan hanya yang aktif. */
    private fun muatBengkel() {
        binding.progress.visibility = View.VISIBLE
        listener = db.collection(BengkelActivityCustomer.KOLEKSI_BENGKEL)
            .addSnapshotListener { snap, error ->
                binding.progress.visibility = View.GONE
                if (error != null) {
                    toast("Gagal memuat: ${error.message}", panjang = true)
                    return@addSnapshotListener
                }
                semua = snap?.documents?.mapNotNull { it.toBengkel() }?.filter { it.aktif }
                    ?: emptyList()
                tampilkan()
            }
    }

    private fun tampilkan() {
        val kata = binding.etCari.text?.toString()?.trim()?.lowercase().orEmpty()
        var hasil = if (kata.isEmpty()) semua else semua.filter {
            it.nama.lowercase().contains(kata) || it.alamat.lowercase().contains(kata)
        }
        // Jika lokasi pengguna diketahui, bengkel terdekat di urutan pertama
        lokasiSaya?.let { l ->
            hasil = hasil.sortedBy { Jarak.meter(l.latitude, l.longitude, it.lat, it.lng) }
        }
        adapter.submit(hasil, lokasiSaya)
        binding.tvKosong.visibility = if (hasil.isEmpty()) View.VISIBLE else View.GONE
        binding.rvBengkel.visibility = if (hasil.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun cariLokasiSaya() {
        LokasiHelper.ambil(
            this,
            onHasil = { loc ->
                lokasiSaya = loc
                tampilkan()
            },
            onGagal = { /* jarak tidak ditampilkan; daftar tetap muncul */ }
        )
    }

    private fun mulaiPantauLokasi() {
        if (locationListener != null) return
        locationListener = LokasiHelper.pantau(this) { loc ->
            lokasiSaya = loc
            tampilkan()
        }
    }

    private fun hentikanPantauLokasi() {
        LokasiHelper.stopPantau(this, locationListener)
        locationListener = null
    }

    override fun onResume() {
        super.onResume()
        if (LokasiHelper.punyaIzin(this)) {
            mulaiPantauLokasi()
        }
    }

    override fun onPause() {
        hentikanPantauLokasi()
        super.onPause()
    }

    override fun onDestroy() {
        hentikanPantauLokasi()
        listener?.remove()
        super.onDestroy()
    }

    private fun toast(msg: String, panjang: Boolean = false) =
        Toast.makeText(this, msg, if (panjang) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}