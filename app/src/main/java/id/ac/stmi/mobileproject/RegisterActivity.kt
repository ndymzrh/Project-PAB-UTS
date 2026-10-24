package id.ac.stmi.mobileproject

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.jvm.java

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val etNama = findViewById<TextInputEditText>(R.id.etNama)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etNoHp = findViewById<TextInputEditText>(R.id.etNoHp)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val etKonfirmasi = findViewById<TextInputEditText>(R.id.etKonfirmasi)
        val btnDaftar = findViewById<MaterialButton>(R.id.btnDaftar)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val tvKembaliLogin = findViewById<TextView>(R.id.tvKembaliLogin)

        // Tombol kembali ke halaman login
        tvKembaliLogin.setOnClickListener {
            finish()
        }

        btnDaftar.setOnClickListener {
            val nama = etNama.text.toString().trim()
            val email = etEmail.text.toString().trim().lowercase()
            val noHp = etNoHp.text.toString().trim().replace(Regex("[\\s-]"), "")
            // Password sengaja TIDAK di-trim agar sama persis dengan yang diketik pengguna
            val password = etPassword.text.toString()
            val konfirmasi = etKonfirmasi.text.toString()

            // 1. Validasi input kosong
            if (nama.isEmpty() || email.isEmpty() || noHp.isEmpty() ||
                password.isEmpty() || konfirmasi.isEmpty()
            ) {
                toast("Harap isi semua kolom")
                return@setOnClickListener
            }

            // 2. Validasi format email
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                toast("Format email tidak valid")
                return@setOnClickListener
            }

            // 3. Validasi nomor HP Indonesia (08xx, 628xx, atau +628xx)
            if (!noHp.matches(Regex("^(\\+62|62|0)8[0-9]{8,12}$"))) {
                toast("Nomor HP tidak valid")
                return@setOnClickListener
            }

            // 4. Validasi password
            if (password.length < 6) {
                toast("Password minimal 6 karakter")
                return@setOnClickListener
            }
            if (password != konfirmasi) {
                toast("Password dan konfirmasi tidak cocok")
                return@setOnClickListener
            }

            // Tampilkan loading dan matikan tombol agar tidak diklik dua kali
            progressBar.visibility = View.VISIBLE
            btnDaftar.isEnabled = false

            val resetLoading = {
                progressBar.visibility = View.GONE
                btnDaftar.isEnabled = true
            }

            // 5. Proses pembuatan akun
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (!task.isSuccessful) {
                        resetLoading()
                        Toast.makeText(this, pesanError(task.exception), Toast.LENGTH_LONG).show()
                        return@addOnCompleteListener
                    }

                    val user = auth.currentUser
                    if (user == null) {
                        resetLoading()
                        Toast.makeText(this, "Terjadi kesalahan, coba lagi", Toast.LENGTH_LONG).show()
                        return@addOnCompleteListener
                    }

                    // Role di-hardcode "pengguna" karena ini khusus form customer
                    val userMap = hashMapOf(
                        "nama" to nama,
                        "email" to email,
                        "no_hp" to noHp,
                        "role" to "pengguna",
                        "points" to 0
                    )

                    db.collection("users").document(user.uid)
                        .set(userMap)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Registrasi berhasil!", Toast.LENGTH_SHORT).show()
                            // Akun otomatis login setelah dibuat, langsung ke beranda
                            startActivity(Intent(this, HomeActivityCustomer::class.java))
                            finishAffinity()
                        }
                        .addOnFailureListener { e ->
                            // Hapus akun Auth supaya pengguna bisa daftar ulang dengan email yang sama
                            user.delete().addOnCompleteListener {
                                resetLoading()
                                Toast.makeText(
                                    this,
                                    "Gagal menyimpan data: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                }
        }
    }

    private fun pesanError(e: Exception?): String = when (e) {
        is FirebaseAuthUserCollisionException -> "Email sudah terdaftar. Silakan masuk."
        is FirebaseAuthWeakPasswordException -> "Password terlalu lemah"
        is FirebaseAuthInvalidCredentialsException -> "Format email tidak valid"
        is FirebaseNetworkException -> "Tidak ada koneksi internet"
        else -> "Registrasi gagal: ${e?.message}"
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}