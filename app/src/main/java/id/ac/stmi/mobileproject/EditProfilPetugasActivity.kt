package id.ac.stmi.mobileproject

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import id.ac.stmi.mobileproject.databinding.ActivityEditProfilPetugasBinding

/**
 * Edit profil petugas/pemilik — tampilan & alur disamakan dengan EditProfileActivity (customer).
 *
 * Data   : Firestore petugas/{uid} (merge): nama, email, telepon, nip, cabang
 * Cabang : dipilih dari koleksi "bengkel" (yang aktif)
 * Email  : hanya ditampilkan (tidak bisa diubah)
 * Nama   : juga disalin ke users/{uid}.nama (best-effort) agar beranda ikut berubah
 * TIDAK pernah menulis field "role" (role hanya di users/{uid}).
 */
class EditProfilPetugasActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "EditProfilPetugas"
        private const val COL_PETUGAS = "petugas"
        private const val COL_USERS = "users"
        private const val COL_BENGKEL = "bengkel"
        private const val TIMEOUT_MS = 8000L
    }

    private lateinit var binding: ActivityEditProfilPetugasBinding

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var uid: String

    private var dimuat = false          // true jika data lama berhasil dibaca dari Firestore
    private var selesai = false         // mencegah callback ganda (sukses/gagal/timeout)
    private var cabang: String = ""     // cabang terpilih
    private var daftarCabang: List<String> = emptyList()

    private val timeoutRunnable = Runnable {
        if (selesai) return@Runnable
        selesai = true
        setLoading(false)
        toast("Koneksi lambat, perubahan akan tersinkron otomatis")
        setResult(RESULT_OK)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityEditProfilPetugasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Jarak agar konten tidak tertutup status bar / navigation bar (edge-to-edge)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val currentUid = auth.currentUser?.uid
        if (currentUid == null) {
            toast("Silakan login terlebih dahulu")
            finish()
            return
        }
        uid = currentUid

        setupClicks()
        muatDaftarCabang()
        muatProfil()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeoutRunnable)
        super.onDestroy()
    }

    // ------------------------------------------------------------------ Klik

    private fun setupClicks() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnCancel.setOnClickListener { finish() }
        binding.btnSave.setOnClickListener { onSaveClicked() }
        binding.tvCabang.setOnClickListener { pilihCabang() }
        binding.btnChangePassword.setOnClickListener {
            startActivity(Intent(this, ChangePasswordActivity::class.java))
        }
    }

    // ------------------------------------------------------------------ Muat data

    private fun muatProfil() {
        val user = auth.currentUser ?: return

        binding.tvIdAkun.text = "ID Akun: ${uid.take(8).uppercase()}"
        binding.etEmail.setText(user.email.orEmpty())
        binding.tvEmailVerified.visibility = if (user.isEmailVerified) View.VISIBLE else View.GONE

        setLoading(true, tampilTeks = false)

        db.collection(COL_PETUGAS).document(uid).get()
            .addOnSuccessListener { doc ->
                dimuat = true
                val nama = doc.getString("nama") ?: user.displayName.orEmpty()
                binding.etName.setText(nama)
                binding.etPhone.setText(stripCountryCode(doc.getString("telepon").orEmpty()))
                binding.etNip.setText(doc.getString("nip").orEmpty())
                setCabang(doc.getString("cabang").orEmpty())
                tampilHeader(nama)
                setLoading(false)
            }
            .addOnFailureListener {
                Log.e(TAG, "Gagal memuat profil", it)
                val nama = user.displayName.orEmpty()
                binding.etName.setText(nama)
                tampilHeader(nama)
                toast("Data lama tidak terbaca, isi yang ingin diubah saja")
                setLoading(false)
            }
    }

    private fun tampilHeader(nama: String) {
        binding.tvHeaderName.text = nama.ifBlank { "Petugas" }
        binding.tvAvatar.text = initialsOf(nama)
    }

    // ------------------------------------------------------------------ Cabang

    /** Pilihan cabang diambil dari koleksi "bengkel" di Firestore (hanya yang aktif). */
    private fun muatDaftarCabang() {
        db.collection(COL_BENGKEL).get()
            .addOnSuccessListener { snap ->
                daftarCabang = snap.documents
                    .filter { it.getBoolean("aktif") != false }
                    .mapNotNull { it.getString("nama")?.trim()?.takeIf { n -> n.isNotEmpty() } }
                    .distinct()
                    .sorted()
                if (daftarCabang.isEmpty()) toast("Belum ada data cabang di database")
            }
            .addOnFailureListener {
                Log.e(TAG, "Gagal memuat daftar cabang", it)
                toast("Daftar cabang gagal dimuat")
            }
    }

    private fun pilihCabang() {
        if (daftarCabang.isEmpty()) {
            toast("Daftar cabang belum tersedia")
            return
        }
        val pilihan = daftarCabang.toTypedArray()
        val terpilih = pilihan.indexOf(cabang)
        MaterialAlertDialogBuilder(this)
            .setTitle("Pilih Cabang")
            .setSingleChoiceItems(pilihan, terpilih) { dialog, which ->
                setCabang(pilihan[which])
                dialog.dismiss()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun setCabang(nilai: String) {
        cabang = nilai
        binding.tvCabang.text = nilai
    }

    // ------------------------------------------------------------------ Simpan

    private fun onSaveClicked() {
        val nama = binding.etName.text.toString().trim()
        val phoneDigits = binding.etPhone.text.toString().filter { it.isDigit() }.trimStart('0')

        if (nama.length < 3) {
            binding.etName.error = "Nama minimal 3 karakter"
            binding.etName.requestFocus()
            return
        }
        if (phoneDigits.isNotEmpty() && phoneDigits.length !in 9..13) {
            binding.etPhone.error = "Nomor HP tidak valid"
            binding.etPhone.requestFocus()
            return
        }

        setLoading(true)
        simpanKeFirestore(nama, phoneDigits)
    }

    /** Tulis ke petugas/{uid} (merge). Field lain tidak terhapus. */
    private fun simpanKeFirestore(nama: String, phoneDigits: String) {
        val user = auth.currentUser ?: return
        val telepon = if (phoneDigits.isEmpty()) "" else "+62$phoneDigits"

        val data = hashMapOf<String, Any>(
            "nama" to nama,
            "email" to user.email.orEmpty(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        // Field opsional: jika data lama gagal dimuat, jangan timpa dengan kosong
        fun opsional(key: String, nilai: String) {
            if (dimuat || nilai.isNotEmpty()) data[key] = nilai
        }
        opsional("nip", binding.etNip.text.toString().trim())
        opsional("telepon", telepon)
        opsional("cabang", cabang.trim())

        selesai = false
        handler.postDelayed(timeoutRunnable, TIMEOUT_MS)

        db.collection(COL_PETUGAS).document(uid)
            .set(data, SetOptions.merge())
            .addOnSuccessListener { berhasil(nama) }
            .addOnFailureListener { gagal(it) }
    }

    private fun berhasil(nama: String) {
        if (selesai) return
        selesai = true
        handler.removeCallbacks(timeoutRunnable)

        val user = auth.currentUser
        user?.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(nama).build())
            ?.addOnFailureListener { Log.w(TAG, "displayName gagal diperbarui", it) }

        // Salin nama ke users/{uid} agar beranda ikut berubah (gagal pun tidak masalah)
        db.collection(COL_USERS).document(uid)
            .set(hashMapOf<String, Any>("nama" to nama), SetOptions.merge())
            .addOnFailureListener { Log.w(TAG, "users.nama tidak diperbarui", it) }

        setLoading(false)
        toast("Profil berhasil diperbarui")
        setResult(RESULT_OK)
        finish()
    }

    private fun gagal(e: Exception) {
        if (selesai) return
        selesai = true
        handler.removeCallbacks(timeoutRunnable)
        Log.e(TAG, "Gagal menyimpan profil", e)
        setLoading(false)

        val pesan = if (e is FirebaseFirestoreException &&
            e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        ) "Tidak punya izin menyimpan. Periksa Firestore rules." else "Gagal menyimpan profil, coba lagi"
        toast(pesan)
    }

    // ------------------------------------------------------------------ Helper

    /** Tombol Simpan dimatikan saat proses berjalan supaya tidak terklik dua kali. */
    private fun setLoading(loading: Boolean, tampilTeks: Boolean = true) {
        binding.progress.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSave.isEnabled = !loading
        binding.btnSave.alpha = if (loading) 0.6f else 1f
        if (tampilTeks) binding.btnSave.text = if (loading) "Menyimpan..." else "Simpan Perubahan"
    }

    /** "+628123456789" / "08123456789" / "628123456789" -> "8123456789" */
    private fun stripCountryCode(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            digits.startsWith("62") -> digits.removePrefix("62")
            digits.startsWith("0") -> digits.removePrefix("0")
            else -> digits
        }
    }

    private fun initialsOf(name: String): String =
        name.trim().split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "P" }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}