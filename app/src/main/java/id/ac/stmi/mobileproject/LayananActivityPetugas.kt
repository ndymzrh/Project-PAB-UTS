package id.ac.stmi.mobileproject

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import id.ac.stmi.mobileproject.databinding.ActivityLayananPetugasBinding
import id.ac.stmi.mobileproject.databinding.DialogFormLayananBinding

class LayananActivityPetugas : AppCompatActivity() {

    companion object {
        // ===== SESUAIKAN DENGAN STRUKTUR FIRESTORE ANDA =====
        // Koleksi tingkat atas tempat layanan disimpan.
        // Field: nama, deskripsi, harga (angka), durasi (menit, angka), aktif (boolean)
        const val KOLEKSI_LAYANAN = "layanan"
    }

    private lateinit var binding: ActivityLayananPetugasBinding
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val adapter = LayananAdapter(
        onEdit = { bukaForm(it) },
        onToggleAktif = { ubahStatusAktif(it) }
    )
    private var listener: ListenerRegistration? = null
    private var semuaLayanan: List<Layanan> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLayananPetugasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inset sistem ditangani sendiri (layout ini tidak memakai include_top_bar)
        val headerTop = binding.header.paddingTop
        val listBottom = binding.rvLayanan.paddingBottom
        val fabBottom = (binding.fabTambah.layoutParams as FrameLayout.LayoutParams).bottomMargin
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.header.updatePadding(top = headerTop + bars.top)
            binding.rvLayanan.updatePadding(bottom = listBottom + bars.bottom)
            binding.fabTambah.updateLayoutParams<FrameLayout.LayoutParams> {
                bottomMargin = fabBottom + bars.bottom
            }
            insets
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.rvLayanan.layoutManager = LinearLayoutManager(this)
        binding.rvLayanan.adapter = adapter
        binding.etCari.doAfterTextChanged { tampilkan() }
        binding.fabTambah.setOnClickListener { bukaForm(null) }

        muatLayanan()
    }

    /** Ambil semua layanan secara realtime. */
    private fun muatLayanan() {
        binding.progress.visibility = View.VISIBLE
        listener = db.collection(KOLEKSI_LAYANAN).addSnapshotListener { snap, error ->
            binding.progress.visibility = View.GONE
            if (error != null) {
                toast("Gagal memuat: ${error.message}", panjang = true)
                return@addSnapshotListener
            }
            semuaLayanan = snap?.documents?.map { d ->
                Layanan(
                    id = d.id,
                    nama = d.getString("nama") ?: "-",
                    deskripsi = d.getString("deskripsi") ?: "",
                    harga = (d.get("harga") as? Number)?.toLong() ?: 0L,
                    durasi = (d.get("durasi") as? Number)?.toLong() ?: 0L,
                    aktif = d.getBoolean("aktif") ?: true,
                    kategori = LayananKategori.values().firstOrNull { it.name == d.getString("kategori") }
                        ?: LayananKategori.LAINNYA
                )
            }?.sortedBy { it.nama.lowercase() } ?: emptyList()
            tampilkan()
        }
    }

    private fun tampilkan() {
        val kata = binding.etCari.text?.toString()?.trim()?.lowercase().orEmpty()
        val hasil = if (kata.isEmpty()) semuaLayanan else semuaLayanan.filter {
            it.nama.lowercase().contains(kata) || it.deskripsi.lowercase().contains(kata)
        }
        adapter.submit(hasil)
        binding.tvTotal.text = "${hasil.size} layanan"
        binding.tvKosong.visibility = if (hasil.isEmpty()) View.VISIBLE else View.GONE
        binding.rvLayanan.visibility = if (hasil.isEmpty()) View.GONE else View.VISIBLE
    }

    /** Form tambah (layanan == null) atau edit. */
    private fun bukaForm(layanan: Layanan?) {
        val form = DialogFormLayananBinding.inflate(layoutInflater)

        // Dropdown kategori (nilai tersimpan = nama enum, mis. "OLI")
        val daftarKategori = LayananKategori.values()
        var kategoriDipilih: LayananKategori = layanan?.kategori ?: daftarKategori.first()
        form.actKategori.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, daftarKategori.map { it.label })
        )
        form.actKategori.setText(kategoriDipilih.label, false)
        form.actKategori.setOnItemClickListener { _, _, pos, _ -> kategoriDipilih = daftarKategori[pos] }
        if (layanan != null) {
            form.etNama.setText(layanan.nama)
            form.etDeskripsi.setText(layanan.deskripsi)
            form.etHarga.setText(layanan.harga.toString())
            if (layanan.durasi > 0) form.etDurasi.setText(layanan.durasi.toString())
            form.swAktif.isChecked = layanan.aktif
        } else {
            form.swAktif.isChecked = true
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (layanan == null) "Tambah Layanan" else "Edit Layanan")
            .setView(form.root)
            .setPositiveButton("Simpan", null) // diatur di bawah agar dialog tidak menutup saat input salah
            .setNegativeButton("Batal", null)
            .create()

        dialog.setOnShowListener {
            val tombolSimpan = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            tombolSimpan.setOnClickListener {
                form.tilNama.error = null
                form.tilHarga.error = null

                val nama = form.etNama.text?.toString()?.trim().orEmpty()
                val harga = form.etHarga.text?.toString()?.trim()?.toLongOrNull()
                val durasiTeks = form.etDurasi.text?.toString()?.trim().orEmpty()
                val durasi = if (durasiTeks.isEmpty()) 0L else durasiTeks.toLongOrNull()

                if (nama.isEmpty()) {
                    form.tilNama.error = "Nama layanan wajib diisi"
                    return@setOnClickListener
                }
                if (harga == null || harga < 0) {
                    form.tilHarga.error = "Harga harus berupa angka"
                    return@setOnClickListener
                }
                if (durasi == null) {
                    toast("Durasi harus berupa angka (menit)")
                    return@setOnClickListener
                }

                val data = hashMapOf<String, Any>(
                    "nama" to nama,
                    "deskripsi" to form.etDeskripsi.text?.toString()?.trim().orEmpty(),
                    "harga" to harga,
                    "durasi" to durasi,
                    "aktif" to form.swAktif.isChecked,
                    "kategori" to kategoriDipilih.name
                )

                tombolSimpan.isEnabled = false
                simpan(
                    id = layanan?.id,
                    data = data,
                    onSukses = { dialog.dismiss() },
                    onGagal = { tombolSimpan.isEnabled = true }
                )
            }
        }
        dialog.show()
    }

    private fun simpan(
        id: String?,
        data: HashMap<String, Any>,
        onSukses: () -> Unit,
        onGagal: () -> Unit
    ) {
        if (id == null) {
            data["createdAt"] = FieldValue.serverTimestamp()
            db.collection(KOLEKSI_LAYANAN).add(data)
                .addOnSuccessListener { toast("Layanan ditambahkan"); onSukses() }
                .addOnFailureListener { gagal(it); onGagal() }
        } else {
            data["updatedAt"] = FieldValue.serverTimestamp()
            db.collection(KOLEKSI_LAYANAN).document(id).update(data)
                .addOnSuccessListener { toast("Layanan diperbarui"); onSukses() }
                .addOnFailureListener { gagal(it); onGagal() }
        }
    }

    /**
     * Layanan tidak pernah dihapus (rules Firestore melarang delete, dan booking lama
     * merujuk layanan ini). Yang dilakukan: nonaktifkan (disembunyikan dari customer) atau aktifkan lagi.
     */
    private fun ubahStatusAktif(layanan: Layanan) {
        if (layanan.aktif) {
            AlertDialog.Builder(this)
                .setTitle("Nonaktifkan layanan?")
                .setMessage(
                    "\"${layanan.nama}\" tidak akan tampil lagi di aplikasi customer. " +
                            "Riwayat booking tetap aman dan layanan bisa diaktifkan kembali kapan saja."
                )
                .setPositiveButton("Nonaktifkan") { _, _ -> setAktif(layanan, false) }
                .setNegativeButton("Batal", null)
                .show()
        } else {
            setAktif(layanan, true)
        }
    }

    private fun setAktif(layanan: Layanan, aktif: Boolean) {
        db.collection(KOLEKSI_LAYANAN).document(layanan.id)
            .update(mapOf("aktif" to aktif, "updatedAt" to FieldValue.serverTimestamp()))
            .addOnSuccessListener {
                toast(if (aktif) "Layanan diaktifkan" else "Layanan dinonaktifkan")
            }
            .addOnFailureListener { gagal(it) }
    }

    private fun gagal(e: Exception) = toast("Gagal: ${e.message}", panjang = true)

    private fun toast(msg: String, panjang: Boolean = false) =
        Toast.makeText(this, msg, if (panjang) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        listener?.remove()
        super.onDestroy()
    }
}