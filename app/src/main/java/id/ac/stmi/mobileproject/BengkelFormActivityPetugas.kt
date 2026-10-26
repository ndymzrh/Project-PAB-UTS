package id.ac.stmi.mobileproject

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import id.ac.stmi.mobileproject.databinding.ActivityBengkelFormPetugasBinding
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.views.CustomZoomButtonsController
import java.util.Locale
import org.osmdroid.util.GeoPoint as OsmPoint

/**
 * Tambah / edit bengkel (petugas).
 * Lokasi dipilih dengan menggeser peta sampai pin di tengah tepat di lokasi bengkel,
 * atau dengan tombol "Gunakan lokasi saya". Tersimpan sebagai GeoPoint di field "lokasi".
 * Setelah pin berhenti digeser, alamat lengkap dibaca otomatis (AlamatHelper) dan mengisi
 * kolom alamat, kecuali petugas sudah mengetik alamat sendiri.
 *
 * Mode tambah: tanpa extra. Mode edit: putExtra(EXTRA_ID, idDokumen).
 */
class BengkelFormActivityPetugas : AppCompatActivity() {

    companion object {
        const val EXTRA_ID = "bengkel_id"

        /** Jeda setelah pin berhenti digeser sebelum alamat dicari (menjaga batas Nominatim). */
        private const val JEDA_CARI_MS = 900L

        /** Pin yang bergeser kurang dari ini dianggap tidak berpindah (tidak cari ulang). */
        private const val JARAK_MIN_CARI_M = 15f
    }

    private lateinit var binding: ActivityBengkelFormPetugasBinding
    private val db by lazy { FirebaseFirestore.getInstance() }
    private var idBengkel: String? = null

    /** True jika petugas sudah menentukan lokasi (geser peta / lokasi saya / data lama). */
    private var lokasiDiatur = false

    // ---- Alamat otomatis ----
    private val handler = Handler(Looper.getMainLooper())
    private var jadwalCari: Runnable? = null
    private var idPermintaan = 0
    private var sedangIsiOtomatis = false
    private var alamatManual = false // true jika petugas mengetik alamat sendiri
    private var titikCariTerakhir: Pair<Double, Double>? = null

    private val izinLokasi = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { hasil ->
        if (hasil.values.any { it }) gunakanLokasiSaya()
        else toast("Izin lokasi ditolak. Geser peta secara manual.")
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OsmSetup.init(this) // harus sebelum MapView di-inflate
        enableEdgeToEdge()
        binding = ActivityBengkelFormPetugasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val headerTop = binding.header.paddingTop
        val panelBottom = binding.panelSimpan.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.header.updatePadding(top = headerTop + bars.top)
            binding.panelSimpan.updatePadding(bottom = panelBottom + bars.bottom)
            insets
        }

        binding.btnBack.setOnClickListener { finish() }

        // ---- Peta ----
        binding.map.setTileSource(TileSourceFactory.MAPNIK)
        binding.map.setMultiTouchControls(true)
        binding.map.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        binding.map.controller.setZoom(11.0)
        binding.map.controller.setCenter(OsmPoint(-6.2088, 106.8456)) // awal: Jakarta

        // Peta berada di dalam ScrollView: cegah ScrollView mencuri gestur geser peta
        binding.map.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.parent.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_MOVE -> lokasiDiatur = true
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.parent.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
        binding.map.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                tampilkanKoordinat()
                jadwalkanCariAlamat()
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean = false
        })
        tampilkanKoordinat()

        // Petugas mengetik sendiri -> alamat tidak ditimpa otomatis lagi
        binding.etAlamat.doAfterTextChanged {
            if (!sedangIsiOtomatis) alamatManual = true
        }
        binding.btnAmbilAlamat.setOnClickListener { cariAlamat(paksa = true) }

        binding.btnLokasiSaya.setOnClickListener { gunakanLokasiSaya() }
        binding.btnSimpan.setOnClickListener { simpan() }

        // ---- Mode tambah / edit ----
        idBengkel = intent.getStringExtra(EXTRA_ID)
        val id = idBengkel
        if (id == null) {
            binding.tvJudul.text = "Tambah Bengkel"
            binding.swAktif.isChecked = true
            // Titik awal peta: lokasi petugas jika izin sudah ada (belum dianggap final)
            if (LokasiHelper.punyaIzin(this)) {
                LokasiHelper.ambil(this,
                    onHasil = { pindahKe(it.latitude, it.longitude) },
                    onGagal = {})
            }
        } else {
            binding.tvJudul.text = "Edit Bengkel"
            muatBengkel(id)
        }
    }

    private fun muatBengkel(id: String) {
        db.collection(BengkelActivityCustomer.KOLEKSI_BENGKEL).document(id).get()
            .addOnSuccessListener { d ->
                if (!d.exists()) {
                    toast("Data bengkel tidak ditemukan")
                    finish()
                    return@addOnSuccessListener
                }
                val alamatLama = d.getString("alamat") ?: ""
                binding.etNama.setText(d.getString("nama") ?: "")
                isiAlamatOtomatis(alamatLama)
                // Alamat yang sudah tersimpan dilindungi; menimpa hanya lewat tombol
                alamatManual = alamatLama.isNotBlank()

                binding.etTelepon.setText(d.getString("telepon") ?: "")
                binding.etJam.setText(d.getString("jamOperasional") ?: "")
                binding.swAktif.isChecked = d.getBoolean("aktif") ?: true
                d.getGeoPoint("lokasi")?.let {
                    titikCariTerakhir = it.latitude to it.longitude
                    lokasiDiatur = true
                    pindahKe(it.latitude, it.longitude)
                }
            }
            .addOnFailureListener { toast("Gagal memuat: ${it.message}", panjang = true) }
    }

    private fun pindahKe(lat: Double, lng: Double) {
        binding.map.controller.setZoom(17.0)
        binding.map.controller.animateTo(OsmPoint(lat, lng))
    }

    private fun gunakanLokasiSaya() {
        if (!LokasiHelper.punyaIzin(this)) {
            izinLokasi.launch(LokasiHelper.IZIN)
            return
        }
        LokasiHelper.ambil(this,
            onHasil = {
                lokasiDiatur = true // set sebelum peta bergerak agar alamat ikut dicari
                pindahKe(it.latitude, it.longitude)
            },
            onGagal = { toast("Lokasi belum tersedia. Pastikan GPS aktif lalu coba lagi.") })
    }

    private fun tampilkanKoordinat() {
        val c = binding.map.mapCenter
        binding.tvKoordinat.text =
            String.format(Locale.US, "Lat %.6f, Lng %.6f", c.latitude, c.longitude)
    }

    // ------------------------------------------------------------------
    //  Alamat otomatis dari pin
    // ------------------------------------------------------------------

    /** Dipanggil setiap peta bergerak; pencarian baru jalan setelah pin diam sebentar. */
    private fun jadwalkanCariAlamat() {
        if (!lokasiDiatur) return // abaikan gerakan awal peta yang bukan dari petugas
        jadwalCari?.let { handler.removeCallbacks(it) }
        val r = Runnable { cariAlamat(paksa = false) }
        jadwalCari = r
        handler.postDelayed(r, JEDA_CARI_MS)
    }

    /**
     * @param paksa true = dari tombol "Isi alamat dari pin": abaikan jarak minimal dan
     *              timpa kolom alamat walau sebelumnya diketik manual.
     */
    private fun cariAlamat(paksa: Boolean) {
        val c = binding.map.mapCenter
        val lat = c.latitude
        val lng = c.longitude

        if (!paksa) {
            val terakhir = titikCariTerakhir
            if (terakhir != null &&
                Jarak.meter(terakhir.first, terakhir.second, lat, lng) < JARAK_MIN_CARI_M
            ) return
        }

        val id = ++idPermintaan
        binding.tvAlamatPin.text = "Mencari alamat…"

        AlamatHelper.cari(this, lat, lng,
            onHasil = { alamat ->
                // Abaikan hasil lama jika pin sudah digeser lagi atau layar ditutup
                if (isFinishing || isDestroyed || id != idPermintaan) return@cari
                titikCariTerakhir = lat to lng

                val isiKolom = paksa || !alamatManual
                if (isiKolom) {
                    isiAlamatOtomatis(alamat)
                    alamatManual = false
                    binding.tvAlamatPin.text = "Alamat dari pin: $alamat"
                } else {
                    binding.tvAlamatPin.text = "Alamat dari pin: $alamat\n" +
                            "(Kolom alamat diisi manual. Tekan \"Isi alamat dari pin\" untuk memakainya.)"
                }
            },
            onGagal = {
                if (isFinishing || isDestroyed || id != idPermintaan) return@cari
                binding.tvAlamatPin.text =
                    "Alamat tidak bisa dibaca (cek internet). Isi alamat secara manual."
            })
    }

    private fun isiAlamatOtomatis(teks: String) {
        sedangIsiOtomatis = true
        binding.etAlamat.setText(teks)
        sedangIsiOtomatis = false
    }

    // ------------------------------------------------------------------
    //  Simpan
    // ------------------------------------------------------------------

    private fun simpan() {
        binding.tilNama.error = null
        binding.tilAlamat.error = null

        val nama = binding.etNama.text?.toString()?.trim().orEmpty()
        val alamat = binding.etAlamat.text?.toString()?.trim().orEmpty()
        if (nama.isEmpty()) {
            binding.tilNama.error = "Nama bengkel wajib diisi"
            return
        }
        if (alamat.isEmpty()) {
            binding.tilAlamat.error = "Alamat wajib diisi"
            return
        }
        if (!lokasiDiatur) {
            toast("Tentukan lokasi dulu: geser peta sampai pin tepat di bengkel, atau tekan Gunakan lokasi saya", panjang = true)
            return
        }

        val c = binding.map.mapCenter
        val data = hashMapOf<String, Any>(
            "nama" to nama,
            "alamat" to alamat,
            "telepon" to binding.etTelepon.text?.toString()?.trim().orEmpty(),
            "jamOperasional" to binding.etJam.text?.toString()?.trim().orEmpty(),
            "lokasi" to GeoPoint(c.latitude, c.longitude),
            "aktif" to binding.swAktif.isChecked
        )

        binding.btnSimpan.isEnabled = false
        val koleksi = db.collection(BengkelActivityCustomer.KOLEKSI_BENGKEL)
        val id = idBengkel
        val tugas = if (id == null) {
            data["createdAt"] = FieldValue.serverTimestamp()
            koleksi.add(data)
        } else {
            data["updatedAt"] = FieldValue.serverTimestamp()
            koleksi.document(id).update(data)
        }
        tugas
            .addOnSuccessListener {
                toast(if (id == null) "Bengkel ditambahkan" else "Bengkel diperbarui")
                finish()
            }
            .addOnFailureListener {
                binding.btnSimpan.isEnabled = true
                toast("Gagal menyimpan: ${it.message}", panjang = true)
            }
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
        jadwalCari?.let { handler.removeCallbacks(it) }
        idPermintaan++ // batalkan hasil pencarian alamat yang masih berjalan
        binding.map.onDetach()
        super.onDestroy()
    }

    private fun toast(msg: String, panjang: Boolean = false) =
        Toast.makeText(this, msg, if (panjang) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}