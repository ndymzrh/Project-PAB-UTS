package id.ac.stmi.mobileproject

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
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
import id.ac.stmi.mobileproject.databinding.ActivityBengkelPetugasBinding
import kotlin.jvm.java

/**
 * Kelola bengkel (petugas): daftar, tambah, edit, aktif/nonaktif.
 * Koleksi "bengkel" yang sama dibaca oleh BengkelActivityCustomer.
 */
class BengkelActivityPetugas : AppCompatActivity() {

    private lateinit var binding: ActivityBengkelPetugasBinding
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val adapter = BengkelPetugasAdapter(
        onEdit = { b ->
            startActivity(
                Intent(this, BengkelFormActivityPetugas::class.java)
                    .putExtra(BengkelFormActivityPetugas.EXTRA_ID, b.id)
            )
        },
        onToggleAktif = { b, aktif -> ubahStatus(b, aktif) }
    )
    private var listener: ListenerRegistration? = null
    private var semua: List<Bengkel> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityBengkelPetugasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val headerTop = binding.header.paddingTop
        val listBottom = binding.rvBengkel.paddingBottom
        val fabBottom = (binding.fabTambah.layoutParams as FrameLayout.LayoutParams).bottomMargin
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.header.updatePadding(top = headerTop + bars.top)
            binding.rvBengkel.updatePadding(bottom = listBottom + bars.bottom)
            binding.fabTambah.updateLayoutParams<FrameLayout.LayoutParams> {
                bottomMargin = fabBottom + bars.bottom
            }
            insets
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.rvBengkel.layoutManager = LinearLayoutManager(this)
        binding.rvBengkel.adapter = adapter
        binding.etCari.doAfterTextChanged { tampilkan() }
        binding.fabTambah.setOnClickListener {
            startActivity(Intent(this, BengkelFormActivityPetugas::class.java))
        }

        muatBengkel()
    }

    private fun muatBengkel() {
        binding.progress.visibility = View.VISIBLE
        listener = db.collection(BengkelActivityCustomer.KOLEKSI_BENGKEL)
            .addSnapshotListener { snap, error ->
                binding.progress.visibility = View.GONE
                if (error != null) {
                    toast("Gagal memuat: ${error.message}", panjang = true)
                    return@addSnapshotListener
                }
                semua = snap?.documents?.map { d ->
                    val gp = d.getGeoPoint("lokasi")
                    Bengkel(
                        id = d.id,
                        nama = d.getString("nama") ?: "-",
                        alamat = d.getString("alamat") ?: "",
                        telepon = d.getString("telepon") ?: "",
                        jamOperasional = d.getString("jamOperasional") ?: "",
                        lat = gp?.latitude ?: 0.0,
                        lng = gp?.longitude ?: 0.0,
                        aktif = d.getBoolean("aktif") ?: true
                    )
                }?.sortedBy { it.nama.lowercase() } ?: emptyList()
                tampilkan()
            }
    }

    private fun tampilkan() {
        val kata = binding.etCari.text?.toString()?.trim()?.lowercase().orEmpty()
        val hasil = if (kata.isEmpty()) semua else semua.filter {
            it.nama.lowercase().contains(kata) || it.alamat.lowercase().contains(kata)
        }
        adapter.submit(hasil)
        binding.tvTotal.text = "${hasil.size} bengkel"
        binding.tvKosong.visibility = if (hasil.isEmpty()) View.VISIBLE else View.GONE
        binding.rvBengkel.visibility = if (hasil.isEmpty()) View.GONE else View.VISIBLE
    }

    /** Bengkel tidak dihapus, hanya dinonaktifkan agar riwayat booking tetap utuh. */
    private fun ubahStatus(b: Bengkel, aktif: Boolean) {
        db.collection(BengkelActivityCustomer.KOLEKSI_BENGKEL).document(b.id)
            .update(mapOf("aktif" to aktif, "updatedAt" to FieldValue.serverTimestamp()))
            .addOnSuccessListener {
                toast(if (aktif) "Bengkel diaktifkan" else "Bengkel dinonaktifkan")
            }
            .addOnFailureListener {
                toast("Gagal: ${it.message}", panjang = true)
                tampilkan() // kembalikan posisi saklar sesuai data
            }
    }

    private fun toast(msg: String, panjang: Boolean = false) =
        Toast.makeText(this, msg, if (panjang) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        listener?.remove()
        super.onDestroy()
    }
}