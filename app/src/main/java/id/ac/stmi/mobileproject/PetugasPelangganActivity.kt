package id.ac.stmi.mobileproject

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import id.ac.stmi.mobileproject.databinding.ActivityPetugasPelangganBinding

class PetugasPelangganActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPetugasPelangganBinding
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val adapter = PelangganAdapter { p ->
        startActivity(
            Intent(this, PelangganDetailActivity::class.java)
                .putExtra(PelangganDetailActivity.EXTRA_UID, p.uid)
        )
    }
    private var listener: ListenerRegistration? = null
    private var semuaPelanggan: List<Pelanggan> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPetugasPelangganBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inset sistem (status bar / navigation bar) ditangani sendiri,
        // karena layout ini tidak memakai include_top_bar.
        val headerTop = binding.header.paddingTop
        val listBottom = binding.rvPelanggan.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.header.updatePadding(top = headerTop + bars.top)
            binding.rvPelanggan.updatePadding(bottom = listBottom + bars.bottom)
            insets
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.rvPelanggan.layoutManager = LinearLayoutManager(this)
        binding.rvPelanggan.adapter = adapter

        binding.etCari.doAfterTextChanged { tampilkan() }

        muatPelanggan()
    }

    /** Ambil semua user dengan role "pengguna" (pelanggan) secara realtime. */
    private fun muatPelanggan() {
        binding.progress.visibility = View.VISIBLE
        listener = db.collection("users")
            .whereEqualTo("role", "pengguna")
            .addSnapshotListener { snapshot, error ->
                binding.progress.visibility = View.GONE
                if (error != null) {
                    Toast.makeText(this, "Gagal memuat: ${error.message}", Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }
                semuaPelanggan = snapshot?.documents?.map { doc ->
                    Pelanggan(
                        uid = doc.id,
                        nama = doc.getString("nama") ?: "-",
                        email = doc.getString("email") ?: "-",
                        noHp = doc.getString("no_hp") ?: "-"
                    )
                }?.sortedBy { it.nama.lowercase() } ?: emptyList()
                tampilkan()
            }
    }

    private fun tampilkan() {
        val kata = binding.etCari.text?.toString()?.trim()?.lowercase().orEmpty()
        val hasil = if (kata.isEmpty()) semuaPelanggan else semuaPelanggan.filter {
            it.nama.lowercase().contains(kata) ||
                    it.email.lowercase().contains(kata) ||
                    it.noHp.contains(kata)
        }
        adapter.submit(hasil)
        binding.tvTotal.text = "${hasil.size} pelanggan"
        binding.tvKosong.visibility = if (hasil.isEmpty()) View.VISIBLE else View.GONE
        binding.rvPelanggan.visibility = if (hasil.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onDestroy() {
        listener?.remove()
        super.onDestroy()
    }
}