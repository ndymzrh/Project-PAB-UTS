package id.ac.stmi.mobileproject

import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.view.View
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import id.ac.stmi.mobileproject.databinding.ActivityBengkelCustomerBinding
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.overlay.Marker
import kotlin.collections.filter
import kotlin.collections.forEachIndexed
import kotlin.collections.getOrNull
import kotlin.collections.isNotEmpty
import kotlin.collections.map
import kotlin.collections.sortedBy
import org.osmdroid.util.GeoPoint as OsmPoint

/**
 * Peta bengkel untuk pelanggan.
 *
 * Mode biasa  : menjelajah bengkel + navigasi.
 * Mode pilih  : buka dengan putExtra(EXTRA_MODE_PILIH, true) lewat startActivityForResult
 *               (ActivityResultContracts.StartActivityForResult). Hasil: RESULT_ID & RESULT_NAMA.
 *               Dipakai nanti oleh halaman booking untuk memilih bengkel.
 */
class BengkelActivityCustomer : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE_PILIH = "mode_pilih"
        const val RESULT_ID = "bengkel_id"
        const val RESULT_NAMA = "bengkel_nama"
        const val KOLEKSI_BENGKEL = "bengkel"
    }

    private lateinit var binding: ActivityBengkelCustomerBinding
    private lateinit var adapter: BengkelCardAdapter
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val snapHelper = PagerSnapHelper()
    private var listener: ListenerRegistration? = null

    private var semua: List<Bengkel> = emptyList()
    private var tampil: List<Bengkel> = emptyList()
    private var lokasiSaya: Location? = null
    private var sudahFitAwal = false

    private val izinLokasi = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { hasil ->
        if (hasil.values.any { it }) cariLokasiSaya(pusatkan = true)
        else toast("Izin lokasi ditolak, jarak ke bengkel tidak bisa ditampilkan")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OsmSetup.init(this) // harus sebelum MapView di-inflate
        enableEdgeToEdge()
        binding = ActivityBengkelCustomerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Peta terang -> ikon status bar gelap
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = true

        val atasAwal = binding.barAtas.paddingTop
        val bawahAwal = binding.panelBawah.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.barAtas.updatePadding(top = atasAwal + bars.top)
            binding.panelBawah.updatePadding(bottom = bawahAwal + bars.bottom)
            insets
        }

        siapkanPeta()
        siapkanKartu()

        binding.btnBack.setOnClickListener { finish() }
        binding.etCari.doAfterTextChanged { terapkanFilter(fit = true) }
        binding.fabLokasi.setOnClickListener {
            if (LokasiHelper.punyaIzin(this)) cariLokasiSaya(pusatkan = true)
            else izinLokasi.launch(LokasiHelper.IZIN)
        }

        if (LokasiHelper.punyaIzin(this)) cariLokasiSaya(pusatkan = false)
        else izinLokasi.launch(LokasiHelper.IZIN)

        muatBengkel()
    }

    private fun siapkanPeta() {
        binding.map.setTileSource(TileSourceFactory.MAPNIK)
        binding.map.setMultiTouchControls(true)
        binding.map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        binding.map.controller.setZoom(11.0)
        binding.map.controller.setCenter(OsmPoint(-6.2088, 106.8456)) // awal: Jakarta
    }

    private fun siapkanKartu() {
        val modePilih = intent.getBooleanExtra(EXTRA_MODE_PILIH, false)
        adapter = BengkelCardAdapter(
            modePilih = modePilih,
            onNavigasi = { bukaPeta(this, it) },
            onPilih = { b ->
                setResult(
                    RESULT_OK,
                    Intent().putExtra(RESULT_ID, b.id).putExtra(RESULT_NAMA, b.nama)
                )
                finish()
            }
        )

        // Padding kiri-kanan supaya kartu pertama dan terakhir tepat di tengah layar
        val layarW = resources.displayMetrics.widthPixels
        val pad = ((layarW - BengkelCardAdapter.lebarKartu(this)) / 2 -
                BengkelCardAdapter.marginKartu(this)).coerceAtLeast(0)
        binding.rvBengkel.setPadding(pad, 0, pad, 0)
        binding.rvBengkel.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvBengkel.adapter = adapter
        snapHelper.attachToRecyclerView(binding.rvBengkel)

        // Setelah kartu berhenti digeser, peta berpindah ke bengkel pada kartu itu
        binding.rvBengkel.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return
                val lm = rv.layoutManager ?: return
                val tengah = snapHelper.findSnapView(lm) ?: return
                fokus(rv.getChildAdapterPosition(tengah))
            }
        })
    }

    /** Semua bengkel secara realtime; yang ditampilkan hanya yang aktif. */
    private fun muatBengkel() {
        binding.progress.visibility = View.VISIBLE
        listener = db.collection(KOLEKSI_BENGKEL).addSnapshotListener { snap, error ->
            binding.progress.visibility = View.GONE
            if (error != null) {
                toast("Gagal memuat: ${error.message}", panjang = true)
                return@addSnapshotListener
            }
            semua = snap?.documents?.mapNotNull { it.toBengkel() }?.filter { it.aktif }
                ?: emptyList()
            terapkanFilter(fit = !sudahFitAwal)
        }
    }

    private fun terapkanFilter(fit: Boolean) {
        val kata = binding.etCari.text?.toString()?.trim()?.lowercase().orEmpty()
        var hasil = if (kata.isEmpty()) semua else semua.filter {
            it.nama.lowercase().contains(kata) || it.alamat.lowercase().contains(kata)
        }
        // Jika lokasi pengguna diketahui, bengkel terdekat di urutan pertama
        lokasiSaya?.let { l ->
            hasil = hasil.sortedBy { Jarak.meter(l.latitude, l.longitude, it.lat, it.lng) }
        }
        tampil = hasil

        adapter.submit(hasil, lokasiSaya)
        binding.tvKosong.visibility = if (hasil.isEmpty()) View.VISIBLE else View.GONE
        binding.rvBengkel.visibility = if (hasil.isEmpty()) View.GONE else View.VISIBLE
        gambarMarker()

        if (fit && hasil.isNotEmpty()) {
            fitPeta(hasil)
            sudahFitAwal = true
        }
    }

    private fun gambarMarker() {
        val peta = binding.map
        peta.overlays.clear()

        val pin = ContextCompat.getDrawable(this, R.drawable.ic_pin_custom)
        tampil.forEachIndexed { i, b ->
            peta.overlays.add(Marker(peta).apply {
                position = OsmPoint(b.lat, b.lng)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = pin
                title = b.nama
                setOnMarkerClickListener { _, _ ->
                    binding.rvBengkel.smoothScrollToPosition(i)
                    fokus(i)
                    true
                }
            })
        }

        lokasiSaya?.let { l ->
            peta.overlays.add(Marker(peta).apply {
                position = OsmPoint(l.latitude, l.longitude)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                icon = ContextCompat.getDrawable(this@BengkelActivityCustomer, R.drawable.ic_map_dot)
                setOnMarkerClickListener { _, _ -> true }
            })
        }
        peta.invalidate()
    }

    private fun fokus(index: Int) {
        val b = tampil.getOrNull(index) ?: return
        binding.map.controller.animateTo(OsmPoint(b.lat, b.lng))
    }

    private fun fitPeta(list: List<Bengkel>) {
        val titik = list.map { OsmPoint(it.lat, it.lng) }
        if (titik.size == 1) {
            binding.map.controller.setZoom(15.0)
            binding.map.controller.setCenter(titik[0])
            return
        }
        binding.map.post {
            binding.map.zoomToBoundingBox(BoundingBox.fromGeoPoints(titik), true, 120)
        }
    }

    private fun cariLokasiSaya(pusatkan: Boolean) {
        LokasiHelper.ambil(
            this,
            onHasil = { loc ->
                lokasiSaya = loc
                terapkanFilter(fit = false)
                if (pusatkan) {
                    binding.map.controller.setZoom(15.0)
                    binding.map.controller.animateTo(OsmPoint(loc.latitude, loc.longitude))
                }
            },
            onGagal = {
                if (pusatkan) toast("Lokasi belum tersedia. Pastikan GPS aktif lalu coba lagi.")
            }
        )
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
    }

    override fun onPause() {
        binding.map.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        listener?.remove()
        binding.map.onDetach()
        super.onDestroy()
    }

    private fun toast(msg: String, panjang: Boolean = false) =
        Toast.makeText(this, msg, if (panjang) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}