package id.ac.stmi.mobileproject

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
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
import com.google.firebase.auth.FirebaseAuthInvalidUserException

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    private lateinit var tilEmail: TextInputLayout
    private lateinit var etEmail: TextInputEditText
    private lateinit var btnRecover: MaterialButton
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_forgot_password)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        auth = FirebaseAuth.getInstance()

        tilEmail = findViewById(R.id.tilEmail)
        etEmail = findViewById(R.id.etEmail)
        btnRecover = findViewById(R.id.btnRecover)
        progressBar = findViewById(R.id.progressBar)

        // Isi otomatis jika email sudah diketik di halaman login
        intent.getStringExtra(EXTRA_EMAIL)?.let { etEmail.setText(it) }

        findViewById<LinearLayout>(R.id.layoutKembali).setOnClickListener { finish() }
        btnRecover.setOnClickListener { kirimTautan() }
    }

    private fun kirimTautan() {
        tilEmail.error = null
        val email = etEmail.text.toString().trim().lowercase()

        if (email.isEmpty()) {
            tilEmail.error = "Email wajib diisi"
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tilEmail.error = "Format email tidak valid"
            return
        }

        setLoading(true)
        // Firebase hanya mengirim email jika akunnya memang ada.
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener(this) { task ->
                setLoading(false)
                val e = task.exception
                when {
                    task.isSuccessful -> {
                        toast("Jika email terdaftar, tautan pemulihan sudah dikirim ke $email. Cek kotak masuk atau folder spam.")
                        finish()   // kembali ke halaman Login
                    }
                    e is FirebaseAuthInvalidUserException ->
                        tilEmail.error = "Email belum terdaftar. Silakan daftar terlebih dahulu."
                    e is FirebaseTooManyRequestsException ->
                        toast("Terlalu banyak percobaan. Coba lagi beberapa saat.")
                    e is FirebaseNetworkException ->
                        toast("Tidak ada koneksi internet")
                    else -> toast("Gagal: ${e?.message}")
                }
            }
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnRecover.isEnabled = !loading
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    companion object {
        const val EXTRA_EMAIL = "extra_email"
    }
}