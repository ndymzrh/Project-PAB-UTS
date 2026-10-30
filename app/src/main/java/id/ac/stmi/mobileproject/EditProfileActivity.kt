package id.ac.stmi.mobileproject

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import id.ac.stmi.mobileproject.databinding.ActivityEditProfileBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Halaman Edit Profil.
 * Layout : res/layout/activity_edit_profile.xml (ViewBinding -> ActivityEditProfileBinding)
 * Data   : Firestore users/{uid}
 *
 * Field di Firestore:
 *   LAMA  : nama, email, no_hp
 *   BARU  : tgl_lahir (yyyy-MM-dd), jenis_kelamin ("Laki-laki"/"Perempuan"), alamat, kota
 *
 * Email hanya ditampilkan (tidak bisa diubah).
 * Catatan: kata sandi TIDAK disimpan di Firestore. Kata sandi dikelola Firebase Auth
 * (otomatis ter-hash & aman), jadi diganti lewat user.updatePassword().
 */
class EditProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditProfileBinding

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var uid: String

    // State form
    private var gender: String = ""          // "" = belum dipilih
    private var birthIso: String = ""        // format simpan: "1988-08-14"

    // Format tanggal. Pakai UTC karena MaterialDatePicker mengembalikan milidetik UTC.
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
    private val displayFormat = SimpleDateFormat("d MMMM yyyy", Locale("id", "ID"))
        .apply { timeZone = TimeZone.getTimeZone("UTC") }

    companion object {
        private const val MALE = "Laki-laki"
        private const val FEMALE = "Perempuan"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Beri jarak agar konten tidak tertutup status bar / navigation bar (edge-to-edge).
        // Tidak memakai applySystemBarInsets() dari TopBarHelper karena helper itu
        // mencari view dari include_top_bar yang tidak ada di halaman ini.
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // Belum login -> tidak boleh edit profil
        val currentUid = auth.currentUser?.uid
        if (currentUid == null) {
            toast("Silakan login terlebih dahulu")
            finish()
            return
        }
        uid = currentUid

        setupClicks()
        loadProfile()
    }

    // ------------------------------------------------------------------
    // 1. AMBIL DATA DARI FIRESTORE -> ISI FORM
    // ------------------------------------------------------------------
    /** Pakai get() satu kali (bukan listener) supaya ketikan user tidak tertimpa. */
    private fun loadProfile() {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { snap ->
                val nama = snap.getString("nama") ?: auth.currentUser?.displayName ?: ""
                val email = snap.getString("email") ?: auth.currentUser?.email ?: ""
                val noHp = snap.getString("no_hp") ?: ""

                binding.etName.setText(nama)
                binding.etEmail.setText(email)
                binding.etPhone.setText(stripCountryCode(noHp))
                binding.etAddress.setText(snap.getString("alamat") ?: "")
                binding.etCity.setText(snap.getString("kota") ?: "")

                birthIso = snap.getString("tgl_lahir") ?: ""
                showBirthDate()

                setGender(snap.getString("jenis_kelamin") ?: "")

                // Header
                binding.tvHeaderName.text = nama.ifBlank { "Pelanggan" }
                binding.tvAvatar.text = initialsOf(nama)
                val idPelanggan = snap.getString("id_pelanggan") ?: uid.take(8).uppercase()
                binding.tvCustomerId.text = "ID Pelanggan: $idPelanggan"

                // Chip "Terverifikasi" hanya muncul kalau email sudah diverifikasi Firebase
                binding.tvEmailVerified.visibility =
                    if (auth.currentUser?.isEmailVerified == true) View.VISIBLE else View.GONE
            }
            .addOnFailureListener { toast("Gagal memuat profil: ${it.message}") }
    }

    // ------------------------------------------------------------------
    // 2. KLIK TOMBOL
    // ------------------------------------------------------------------
    private fun setupClicks() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnCancel.setOnClickListener { finish() }
        binding.btnSave.setOnClickListener { onSaveClicked() }

        binding.tvBirthDate.setOnClickListener { showDatePicker() }
        binding.btnMale.setOnClickListener { setGender(MALE) }
        binding.btnFemale.setOnClickListener { setGender(FEMALE) }

        binding.btnChangePassword.setOnClickListener {
            startActivity(Intent(this, ChangePasswordActivity::class.java))
        }
    }

    // ------------------------------------------------------------------
    // 3. TANGGAL LAHIR
    // ------------------------------------------------------------------
    private fun showDatePicker() {
        val builder = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Pilih tanggal lahir")

        // Kalau sudah ada tanggal tersimpan, buka picker di tanggal itu
        if (birthIso.isNotEmpty()) {
            isoFormat.parse(birthIso)?.let { builder.setSelection(it.time) }
        }

        val picker = builder.build()
        picker.addOnPositiveButtonClickListener { millis ->
            birthIso = isoFormat.format(Date(millis))
            showBirthDate()
        }
        picker.show(supportFragmentManager, "birthDatePicker")
    }

    private fun showBirthDate() {
        binding.tvBirthDate.text =
            if (birthIso.isEmpty()) ""
            else isoFormat.parse(birthIso)?.let { displayFormat.format(it) } ?: ""
    }

    // ------------------------------------------------------------------
    // 4. JENIS KELAMIN (dua tombol, hanya satu yang aktif)
    // ------------------------------------------------------------------
    private fun setGender(value: String) {
        gender = value
        styleGenderButton(binding.btnMale, value == MALE)
        styleGenderButton(binding.btnFemale, value == FEMALE)
    }

    private fun styleGenderButton(button: android.widget.TextView, selected: Boolean) {
        button.setBackgroundResource(
            if (selected) R.drawable.bg_gender_on else R.drawable.bg_gender_off
        )
        button.setTextColor(
            if (selected) ContextCompat.getColor(this, android.R.color.white)
            else ContextCompat.getColor(this, R.color.bk_text_secondary)
        )
    }

    // ------------------------------------------------------------------
    // 5. SIMPAN PERUBAHAN
    // ------------------------------------------------------------------
    private fun onSaveClicked() {
        val nama = binding.etName.text.toString().trim()
        val phoneDigits = binding.etPhone.text.toString().filter { it.isDigit() }.trimStart('0')

        // --- Validasi ---
        if (nama.isEmpty()) {
            binding.etName.error = "Nama wajib diisi"
            binding.etName.requestFocus()
            return
        }
        if (phoneDigits.length !in 9..13) {
            binding.etPhone.error = "Nomor WhatsApp tidak valid"
            binding.etPhone.requestFocus()
            return
        }

        setLoading(true)
        saveToFirestore(nama, phoneDigits)
    }

    /**
     * Tulis ke users/{uid}. SetOptions.merge() = hanya field yang kita kirim yang berubah,
     * field lain (points, dll.) tidak terhapus. Field baru otomatis dibuat kalau belum ada.
     */
    private fun saveToFirestore(nama: String, phoneDigits: String) {
        val data = hashMapOf<String, Any>(
            "nama" to nama,
            "no_hp" to "+62$phoneDigits",
            "tgl_lahir" to birthIso,
            "jenis_kelamin" to gender,
            "alamat" to binding.etAddress.text.toString().trim(),
            "kota" to binding.etCity.text.toString().trim(),
            "updated_at" to FieldValue.serverTimestamp()
        )

        db.collection("users").document(uid)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                setLoading(false)
                toast("Profil berhasil diperbarui")
                finish() // kembali ke halaman Akun; listener di sana otomatis menampilkan data baru
            }
            .addOnFailureListener {
                setLoading(false)
                toast("Gagal menyimpan: ${it.message}")
            }
    }

    // ------------------------------------------------------------------
    // 6. HELPER
    // ------------------------------------------------------------------
    /** Tombol Simpan dimatikan saat proses berjalan supaya tidak terklik dua kali. */
    private fun setLoading(loading: Boolean) {
        binding.btnSave.isEnabled = !loading
        binding.btnSave.alpha = if (loading) 0.6f else 1f
        binding.btnSave.text = if (loading) "Menyimpan..." else "Simpan Perubahan"
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
            .ifEmpty { "?" }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}