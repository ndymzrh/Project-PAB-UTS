package id.ac.stmi.mobileproject

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import id.ac.stmi.mobileproject.databinding.ActivityChangePasswordBinding

/**
 * Halaman Ubah Kata Sandi.
 * Layout : res/layout/activity_change_password.xml (ViewBinding -> ActivityChangePasswordBinding)
 *
 * Alur Firebase:
 *   1. reauthenticate()  -> verifikasi kata sandi LAMA (wajib oleh Firebase)
 *   2. updatePassword()  -> kata sandi di Firebase Auth diganti
 *   3. (opsional) catat waktu ganti ke Firestore: users/{uid}.pw_updated_at
 *
 * Kata sandi TIDAK disimpan di Firestore. Firebase Auth menyimpannya dalam bentuk hash.
 */
class ChangePasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChangePasswordBinding

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityChangePasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Jarak agar konten tidak tertutup status bar / navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        if (auth.currentUser == null) {
            toast("Silakan login terlebih dahulu")
            finish()
            return
        }

        setupClicks()
        setupMatchIndicator()
    }

    private fun setupClicks() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnCancel.setOnClickListener { finish() }
        binding.btnHelp.setOnClickListener { toast("Butuh bantuan? Hubungi CS 24/7") }
        binding.btnSave.setOnClickListener { onSaveClicked() }

        // Ikon mata: tampilkan / sembunyikan kata sandi
        binding.ivToggleOld.setOnClickListener { togglePassword(binding.etOldPassword, binding.ivToggleOld) }
        binding.ivToggleNew.setOnClickListener { togglePassword(binding.etNewPassword, binding.ivToggleNew) }
        binding.ivToggleConfirm.setOnClickListener { togglePassword(binding.etConfirmPassword, binding.ivToggleConfirm) }
    }

    // ------------------------------------------------------------------
    // Tampilkan / sembunyikan password
    // ------------------------------------------------------------------
    private fun togglePassword(editText: EditText, icon: ImageView) {
        val isHidden = editText.transformationMethod is PasswordTransformationMethod
        editText.transformationMethod =
            if (isHidden) HideReturnsTransformationMethod.getInstance()
            else PasswordTransformationMethod.getInstance()
        icon.setImageResource(if (isHidden) R.drawable.ic_ep_eye else R.drawable.ic_ep_eye_off)
        editText.setSelection(editText.text.length) // kursor tetap di akhir teks
    }

    // ------------------------------------------------------------------
    // Chip "Cocok" muncul saat konfirmasi sama dengan sandi baru
    // ------------------------------------------------------------------
    private fun setupMatchIndicator() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val confirm = binding.etConfirmPassword.text.toString()
                val isMatch = confirm.isNotEmpty() &&
                        confirm == binding.etNewPassword.text.toString()
                binding.tvMatch.visibility = if (isMatch) View.VISIBLE else View.GONE
            }
        }
        binding.etNewPassword.addTextChangedListener(watcher)
        binding.etConfirmPassword.addTextChangedListener(watcher)
    }

    // ------------------------------------------------------------------
    // Simpan
    // ------------------------------------------------------------------
    private fun onSaveClicked() {
        val old = binding.etOldPassword.text.toString()
        val new = binding.etNewPassword.text.toString()
        val confirm = binding.etConfirmPassword.text.toString()

        // --- Validasi ---
        if (old.isEmpty()) {
            binding.etOldPassword.error = "Kata sandi lama wajib diisi"
            binding.etOldPassword.requestFocus()
            return
        }
        if (new.length < 6) {
            binding.etNewPassword.error = "Minimal 6 karakter"
            binding.etNewPassword.requestFocus()
            return
        }
        if (new == old) {
            binding.etNewPassword.error = "Kata sandi baru tidak boleh sama dengan yang lama"
            binding.etNewPassword.requestFocus()
            return
        }
        if (confirm != new) {
            binding.etConfirmPassword.error = "Konfirmasi tidak sama"
            binding.etConfirmPassword.requestFocus()
            return
        }

        val user = auth.currentUser
        val email = user?.email
        if (user == null || email == null) {
            toast("Sesi login tidak valid, silakan login ulang")
            return
        }

        setLoading(true)

        // 1) Login ulang dengan sandi lama
        user.reauthenticate(EmailAuthProvider.getCredential(email, old))
            .addOnSuccessListener {
                // 2) Ganti sandi
                user.updatePassword(new)
                    .addOnSuccessListener {
                        // 3) Catat waktu ganti (tidak menyimpan sandi apa pun)
                        db.collection("users").document(user.uid)
                            .update("pw_updated_at", FieldValue.serverTimestamp())

                        setLoading(false)
                        toast("Kata sandi berhasil diubah")
                        finish()
                    }
                    .addOnFailureListener {
                        setLoading(false)
                        toast("Gagal mengubah kata sandi: ${it.message}")
                    }
            }
            .addOnFailureListener { e ->
                setLoading(false)
                when (e) {
                    is FirebaseAuthInvalidCredentialsException -> {
                        binding.etOldPassword.error = "Kata sandi lama salah"
                        binding.etOldPassword.requestFocus()
                    }
                    is FirebaseTooManyRequestsException ->
                        toast("Terlalu banyak percobaan. Coba lagi beberapa saat.")
                    is FirebaseNetworkException ->
                        toast("Tidak ada koneksi internet")
                    else -> toast("Verifikasi gagal: ${e.message}")
                }
            }
    }

    /** Matikan tombol saat proses berjalan supaya tidak terklik dua kali. */
    private fun setLoading(loading: Boolean) {
        binding.btnSave.isEnabled = !loading
        binding.btnSave.alpha = if (loading) 0.6f else 1f
        binding.btnSave.text = if (loading) "Menyimpan..." else "Simpan Kata Sandi Baru"
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}