package id.ac.stmi.mobileproject

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import id.ac.stmi.mobileproject.databinding.ActivityPelangganDetailBinding
import id.ac.stmi.mobileproject.databinding.ItemBarisDetailBinding
import java.text.SimpleDateFormat
import java.util.Locale

class PelangganDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_UID = "uid"

        // ===== SESUAIKAN DENGAN STRUKTUR FIRESTORE ANDA =====
        // Nama subkoleksi di dalam users/{uid} (cek di Firebase Console, sesuaikan jika beda)
        const val SUBKOLEKSI_MOBIL = "vehicles"
        const val SUBKOLEKSI_BOOKING = "bookings"
    }

    private lateinit var binding: ActivityPelangganDetailBinding
    private val db by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityPelangganDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val headerTop = binding.header.paddingTop
        val scrollBottom = binding.scroll.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.header.updatePadding(top = headerTop + bars.top)
            binding.scroll.updatePadding(bottom = scrollBottom + bars.bottom)
            insets
        }

        binding.btnBack.setOnClickListener { finish() }

        val uid = intent.getStringExtra(EXTRA_UID)
        if (uid == null) {
            Toast.makeText(this, "Data pengguna tidak ditemukan", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        muatUser(uid)
        muatMobil(uid)
        muatBooking(uid)
    }

    private fun muatUser(uid: String) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val nama = doc.getString("nama") ?: "-"
                binding.tvNama.text = nama
                binding.tvInisial.text = nama.firstOrNull()?.uppercase() ?: "?"
                binding.tvEmail.text = doc.getString("email") ?: "-"
                binding.tvNoHp.text = doc.getString("no_hp") ?: "-"
                binding.tvRole.text = (doc.getString("role") ?: "-").replaceFirstChar { it.uppercase() }
                val points = (doc.get("points") as? Number)?.toLong() ?: 0L
                binding.tvPoints.text = points.toString()
            }
            .addOnFailureListener { gagal(it) }
    }

    private fun muatMobil(uid: String) {
        // Mobil disimpan sebagai subkoleksi: users/{uid}/<SUBKOLEKSI_MOBIL>
        db.collection("users").document(uid).collection(SUBKOLEKSI_MOBIL).get()
            .addOnSuccessListener { snap ->
                binding.containerMobil.removeAllViews()
                binding.tvJumlahMobil.text = snap.size().toString()
                binding.tvKosongMobil.visibility = if (snap.isEmpty) View.VISIBLE else View.GONE
                // Mobil utama (primary = true) ditaruh paling atas
                val urut = snap.documents.sortedByDescending { it.getBoolean("primary") == true }
                urut.forEach { doc ->
                    val judul = listOfNotNull(pick(doc, "brand"), pick(doc, "model"))
                        .joinToString(" ").ifEmpty { "Mobil" } +
                            if (doc.getBoolean("primary") == true) "  (Utama)" else ""
                    val baris1 = listOfNotNull(
                        pick(doc, "plateNumber"),
                        pick(doc, "year"),
                        pick(doc, "color")
                    ).joinToString(" • ")
                    val baris2 = listOfNotNull(
                        pick(doc, "transmission"),
                        pick(doc, "fuelType"),
                        pick(doc, "odometer")?.let { "$it km" }
                    ).joinToString(" • ")
                    val baris3 = listOfNotNull(
                        pick(doc, "lastService")?.let { "Servis terakhir: $it" },
                        pick(doc, "serviceInterval")?.let { "Interval: $it" }
                    ).joinToString(" • ")
                    val sub = listOf(baris1, baris2, baris3)
                        .filter { it.isNotEmpty() }
                        .joinToString("\n")
                        .ifEmpty { "-" }
                    tambahBaris(binding.containerMobil, judul, sub)
                }
            }
            .addOnFailureListener { gagal(it) }
    }

    private fun muatBooking(uid: String) {
        db.collection("users").document(uid).collection(SUBKOLEKSI_BOOKING).get()
            .addOnSuccessListener { snap ->
                binding.containerBooking.removeAllViews()
                binding.tvJumlahBooking.text = snap.size().toString()
                binding.tvKosongBooking.visibility = if (snap.isEmpty) View.VISIBLE else View.GONE

                // Urutkan di aplikasi (terbaru dulu) agar tidak butuh index Firestore
                val urut = snap.documents.sortedByDescending { doc ->
                    (doc.get("tanggal") as? Timestamp)?.seconds
                        ?: (doc.get("created_at") as? Timestamp)?.seconds
                        ?: 0L
                }
                urut.forEach { doc ->
                    val judul = pick(doc, "layanan", "jenis_servis", "servis", "nama_layanan") ?: "Booking"
                    val sub = listOfNotNull(
                        pick(doc, "tanggal", "tgl_booking", "created_at"),
                        pick(doc, "jam"),
                        pick(doc, "status")?.let { "Status: $it" }
                    ).joinToString(" • ").ifEmpty { "-" }
                    tambahBaris(binding.containerBooking, judul, sub)
                }
            }
            .addOnFailureListener { gagal(it) }
    }

    /** Ambil field pertama yang ada isinya dari daftar nama field. */
    private fun pick(doc: DocumentSnapshot, vararg keys: String): String? {
        for (k in keys) {
            val v = doc.get(k) ?: continue
            val teks = if (v is Timestamp) {
                SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(v.toDate())
            } else v.toString()
            if (teks.isNotBlank()) return teks
        }
        return null
    }

    private fun tambahBaris(container: LinearLayout, judul: String, sub: String) {
        val row = ItemBarisDetailBinding.inflate(layoutInflater, container, false)
        row.tvJudul.text = judul
        row.tvSub.text = sub
        container.addView(row.root)
    }

    private fun gagal(e: Exception) {
        Toast.makeText(this, "Gagal memuat: ${e.message}", Toast.LENGTH_LONG).show()
    }
}