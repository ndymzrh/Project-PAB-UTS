package id.ac.stmi.mobileproject

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var rgRole: RadioGroup
    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var progressBar: ProgressBar

    companion object {
        // Nilai field "role" di Firestore: users/{uid}.role
        private const val ROLE_PELANGGAN = "pengguna"   // sama dengan yang disimpan RegisterActivity
        private const val ROLE_PETUGAS = "petugas"      // dibuat manual untuk akun mekanik/admin
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // 1. Inisialisasi View berdasarkan ID dari activity_login.xml
        rgRole = findViewById(R.id.rgRole)
        tilEmail = findViewById(R.id.tilEmail)
        tilPassword = findViewById(R.id.tilPassword)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        progressBar = findViewById(R.id.progressBar)
        val layoutDaftar = findViewById<LinearLayout>(R.id.layoutDaftar)
        val tvDaftar = findViewById<TextView>(R.id.tvDaftar)
        val tvLupaPassword = findViewById<TextView>(R.id.tvLupaPassword)

        // 2. Sembunyikan "Daftar" saat role Petugas dipilih
        rgRole.setOnCheckedChangeListener { _, checkedId ->
            layoutDaftar.visibility =
                if (checkedId == R.id.rbPetugas) View.GONE else View.VISIBLE
        }

        // 3. Hapus pesan error saat pengguna mulai mengetik ulang
        etEmail.setOnFocusChangeListener { _, _ -> tilEmail.error = null }
        etPassword.setOnFocusChangeListener { _, _ -> tilPassword.error = null }

        // Tombol "Daftar Sekarang" -> halaman register
        tvDaftar.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Tombol "Masuk"
        btnLogin.setOnClickListener { prosesLogin() }

        // "Lupa Kata sandi?"
        tvLupaPassword.setOnClickListener { kirimResetPassword() }

        // 4. Kalau sebelumnya sudah login, langsung arahkan sesuai role
        auth.currentUser?.let { cekRoleDanMasuk(it.uid, roleDipilih = null) }
    }

    // ---------------------------------------------------------------
    // LOGIN
    // ---------------------------------------------------------------
    private fun prosesLogin() {
        tilEmail.error = null
        tilPassword.error = null

        val email = etEmail.text.toString().trim().lowercase()
        val password = etPassword.text.toString()   // jangan di-trim

        if (email.isEmpty()) {
            tilEmail.error = "Email wajib diisi"
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.error = "Format email tidak valid"
            return
        }
        if (password.isEmpty()) {
            tilPassword.error = "Password wajib diisi"
            return
        }

        val roleDipilih =
            if (rgRole.checkedRadioButtonId == R.id.rbPetugas) ROLE_PETUGAS else ROLE_PELANGGAN

        setLoading(true)

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (!task.isSuccessful) {
                    setLoading(false)
                    toast(pesanError(task.exception))
                    return@addOnCompleteListener
                }

                val uid = task.result?.user?.uid
                if (uid == null) {
                    setLoading(false)
                    toast("Terjadi kesalahan, coba lagi")
                    return@addOnCompleteListener
                }

                cekRoleDanMasuk(uid, roleDipilih)
            }
    }

    /**
     * Ambil role dari Firestore (users/{uid}.role), cocokkan dengan pilihan di layar,
     * lalu buka halaman sesuai role.
     * roleDipilih = null artinya login otomatis (tanpa memilih role di layar).
     */
    private fun cekRoleDanMasuk(uid: String, roleDipilih: String?) {
        setLoading(true)

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val role = doc.getString("role")?.trim()?.lowercase()

                when {
                    !doc.exists() || role.isNullOrEmpty() -> {
                        auth.signOut()
                        setLoading(false)
                        toast("Data akun tidak ditemukan. Hubungi admin.")
                    }

                    roleDipilih != null && role != roleDipilih -> {
                        auth.signOut()
                        setLoading(false)
                        val seharusnya = if (role == ROLE_PETUGAS) "Petugas" else "Pelanggan"
                        toast("Akun ini terdaftar sebagai $seharusnya. Pilih role yang sesuai.")
                    }

                    else -> bukaHalaman(role)
                }
            }
            .addOnFailureListener { e ->
                // Jangan signOut di sini: bisa jadi hanya masalah koneksi
                setLoading(false)
                toast("Gagal memuat data akun: ${e.message}")
            }
    }

    private fun bukaHalaman(role: String) {
        when (role) {
            ROLE_PELANGGAN -> {
                startActivity(Intent(this, HomeActivityCustomer::class.java))
                finishAffinity()
            }

            ROLE_PETUGAS -> {
                // TODO: ganti dengan halaman petugas Anda, contoh:
                // startActivity(Intent(this, HomeActivityPetugas::class.java))
                // finishAffinity()
                auth.signOut()
                setLoading(false)
                toast("Halaman petugas belum tersedia")
            }

            else -> {
                auth.signOut()
                setLoading(false)
                toast("Role akun tidak dikenali")
            }
        }
    }

    // ---------------------------------------------------------------
    // LUPA PASSWORD
    // ---------------------------------------------------------------
    private fun kirimResetPassword() {
        tilEmail.error = null
        val email = etEmail.text.toString().trim().lowercase()

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.error = "Isi email yang valid dulu untuk reset password"
            etEmail.requestFocus()
            return
        }

        setLoading(true)
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener(this) { task ->
                setLoading(false)
                if (task.isSuccessful) {
                    // Pesan sengaja netral supaya tidak membocorkan email mana yang terdaftar
                    toast("Jika email terdaftar, tautan reset password sudah dikirim")
                } else {
                    toast(pesanError(task.exception))
                }
            }
    }

    // ---------------------------------------------------------------
    // UTIL
    // ---------------------------------------------------------------
    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnLogin.isEnabled = !loading
    }

    private fun pesanError(e: Exception?): String = when (e) {
        is FirebaseAuthInvalidUserException,
        is FirebaseAuthInvalidCredentialsException -> "Email atau password salah"
        is FirebaseTooManyRequestsException -> "Terlalu banyak percobaan. Coba lagi beberapa saat."
        is FirebaseNetworkException -> "Tidak ada koneksi internet"
        else -> "Gagal masuk: ${e?.message}"
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}