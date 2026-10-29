package id.ac.stmi.mobileproject

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions

/**
 * Edit profil petugas/pemilik.
 * Menyimpan ke Firestore petugas/{uid} (merge) + displayName di Firebase Auth.
 * TIDAK pernah menulis field "role" (role hanya di users/{uid}).
 */
class EditProfilPetugasActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "EditProfilPetugas"
        private const val COL_PETUGAS = "petugas"
        private const val TIMEOUT_MS = 8000L
        private val DAFTAR_SHIFT = listOf("Shift Pagi", "Shift Siang", "Shift Malam")
        private val REGEX_HP = Regex("^(\\+62|62|0)8\\d{7,12}$")
    }

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val handler = Handler(Looper.getMainLooper())

    private var dimuat = false   // true jika data lama berhasil dibaca dari Firestore
    private var selesai = false  // mencegah callback ganda (sukses/gagal/timeout)

    private lateinit var tvAvatarEdit: TextView
    private lateinit var etEmail: TextInputEditText
    private lateinit var etIdAkun: TextInputEditText
    private lateinit var etNama: TextInputEditText
    private lateinit var etNip: TextInputEditText
    private lateinit var etTelepon: TextInputEditText
    private lateinit var etCabang: TextInputEditText
    private lateinit var actShift: AutoCompleteTextView
    private lateinit var tilNama: TextInputLayout
    private lateinit var tilTelepon: TextInputLayout
    private lateinit var progress: ProgressBar
    private lateinit var btnSimpan: MaterialButton

    private val timeoutRunnable = Runnable {
        if (selesai) return@Runnable
        selesai = true
        toast("Koneksi lambat, perubahan akan tersinkron otomatis")
        setResult(RESULT_OK)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profil_petugas)

        tvAvatarEdit = findViewById(R.id.tvAvatarEdit)
        etEmail = findViewById(R.id.etEmail)
        etIdAkun = findViewById(R.id.etIdAkun)
        etNama = findViewById(R.id.etNama)
        etNip = findViewById(R.id.etNip)
        etTelepon = findViewById(R.id.etTelepon)
        etCabang = findViewById(R.id.etCabang)
        actShift = findViewById(R.id.actShift)
        tilNama = findViewById(R.id.tilNama)
        tilTelepon = findViewById(R.id.tilTelepon)
        progress = findViewById(R.id.progress)
        btnSimpan = findViewById(R.id.btnSimpan)

        actShift.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, DAFTAR_SHIFT)
        )

        etNama.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                tilNama.error = null
                tvAvatarEdit.text = inisial(s?.toString().orEmpty())
            }
        })
        etTelepon.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { tilTelepon.error = null }
        })

        // Tombol kembali pada top bar
        findViewById<View>(R.id.btnBack)?.setOnClickListener { finish() }
        btnSimpan.setOnClickListener { simpan() }

        muatProfil()
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeoutRunnable)
        super.onDestroy()
    }

    // ------------------------------------------------------------ Muat data

    private fun muatProfil() {
        val user = auth.currentUser
        if (user == null) {
            finish()
            return
        }

        // Tampilkan informasi akun yang terkunci
        etEmail.setText(user.email.orEmpty())
        val uid = user.uid
        val uidTampil = if (uid.length > 8) "${uid.take(4)}…${uid.takeLast(4)}" else uid
        etIdAkun.setText(uidTampil)

        btnSimpan.isEnabled = false
        progress.visibility = View.VISIBLE

        db.collection(COL_PETUGAS).document(user.uid).get()
            .addOnSuccessListener { doc ->
                dimuat = true
                etNama.setText(doc.getString("nama") ?: user.displayName.orEmpty())
                etNip.setText(doc.getString("nip").orEmpty())
                etTelepon.setText(doc.getString("telepon").orEmpty())
                etCabang.setText(doc.getString("cabang").orEmpty())
                actShift.setText(doc.getString("shift").orEmpty(), false)
                selesaiMuat()
            }
            .addOnFailureListener {
                Log.e(TAG, "Gagal memuat profil", it)
                etNama.setText(user.displayName.orEmpty())
                toast("Data lama tidak terbaca, isi yang ingin diubah saja")
                selesaiMuat()
            }
    }

    private fun selesaiMuat() {
        progress.visibility = View.GONE
        btnSimpan.isEnabled = true
        tvAvatarEdit.text = inisial(etNama.text.toString())
    }

    // ------------------------------------------------------------ Simpan

    private fun simpan() {
        val user = auth.currentUser ?: return

        val nama = etNama.text.toString().trim()
        val telepon = etTelepon.text.toString().trim().replace(" ", "").replace("-", "")

        if (nama.length < 3) {
            tilNama.error = "Nama minimal 3 karakter"
            etNama.requestFocus()
            return
        }
        if (telepon.isNotEmpty() && !REGEX_HP.matches(telepon)) {
            tilTelepon.error = "Nomor HP tidak valid (contoh: 08123456789)"
            etTelepon.requestFocus()
            return
        }

        val data = hashMapOf<String, Any>(
            "nama" to nama,
            "email" to user.email.orEmpty(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        // Field opsional: jika data lama gagal dimuat, jangan timpa dengan kosong
        fun opsional(key: String, nilai: String) {
            if (dimuat || nilai.isNotEmpty()) data[key] = nilai
        }
        opsional("nip", etNip.text.toString().trim())
        opsional("telepon", telepon)
        opsional("cabang", etCabang.text.toString().trim())
        opsional("shift", actShift.text.toString().trim())

        setLoading(true)
        selesai = false
        handler.postDelayed(timeoutRunnable, TIMEOUT_MS)

        db.collection(COL_PETUGAS).document(user.uid)
            .set(data, SetOptions.merge())
            .addOnSuccessListener { berhasil(user, nama) }
            .addOnFailureListener { gagal(it) }
    }

    private fun berhasil(user: FirebaseUser, nama: String) {
        if (selesai) return
        selesai = true
        handler.removeCallbacks(timeoutRunnable)

        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(nama).build())
            .addOnFailureListener { Log.w(TAG, "displayName gagal diperbarui", it) }

        toast("Profil berhasil disimpan")
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

    private fun setLoading(loading: Boolean) {
        progress.visibility = if (loading) View.VISIBLE else View.GONE
        btnSimpan.isEnabled = !loading
        btnSimpan.text = if (loading) "Menyimpan..." else "Simpan Perubahan"
    }

    private fun inisial(nama: String): String {
        val kata = nama.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            kata.isEmpty() -> "P"
            kata.size == 1 -> kata[0].take(1).uppercase()
            else -> "${kata[0][0]}${kata[1][0]}".uppercase()
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}