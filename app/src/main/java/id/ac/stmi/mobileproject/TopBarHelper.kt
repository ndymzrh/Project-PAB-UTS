package id.ac.stmi.mobileproject

import android.app.Activity
import android.graphics.Color
import android.widget.Toast
import android.content.Intent
import android.view.View
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.badge.ExperimentalBadgeUtils
@androidx.annotation.OptIn(ExperimentalBadgeUtils::class)
fun Activity.setupTopBar(
    showNotificationDot: Boolean = false,
    onSupport: () -> Unit = { toastTopBar("Bantuan") },
    // DIUBAH: dulu toast("Notifikasi"), sekarang membuka halaman Notifikasi
    onNotification: () -> Unit = {
        startActivity(Intent(this@setupTopBar, NotificationActivity::class.java))
    },
    onLogout: () -> Unit = { confirmLogout() }
): BadgeDrawable? {

    val toolbar = findViewById<MaterialToolbar>(R.id.topBar) ?: return null

    toolbar.setOnMenuItemClickListener { item ->
        when (item.itemId) {
            R.id.action_support -> { onSupport(); true }
            R.id.action_notification -> { onNotification(); true }
            R.id.action_logout -> { onLogout(); true }
            else -> false
        }
    }

    // Titik merah pada ikon notifikasi (menggantikan View bg_dot_red lama)
    val badge = BadgeDrawable.create(this).apply {
        backgroundColor = Color.parseColor("#D32F2F")
        // Awalnya disembunyikan; tampil/tidaknya ditentukan data Firestore di bawah.
        // (parameter showNotificationDot dipertahankan agar pemanggil lama tidak error)
        isVisible = false
    }
    toolbar.post {
        BadgeUtils.attachBadgeDrawable(badge, toolbar, R.id.action_notification)
    }

    // BARU: titik merah mengikuti data asli di Firestore.
    // Muncul kalau ada notifikasi dengan dibaca == false, hilang kalau semuanya sudah dibaca.
    // Listener otomatis berhenti saat halaman ditutup (dilepas ke Activity).
    FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
        // observeWhileStarted: pemantau dipasang lagi SETIAP halaman kembali tampil,
        // jadi setelah kembali dari halaman Notifikasi titik merah ikut diperbarui.
        observeWhileStarted {
            FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .collection("notifications")
                .whereEqualTo("dibaca", false)
                .addSnapshotListener { snap, error ->
                    if (error != null) return@addSnapshotListener
                    badge.isVisible = (snap?.size() ?: 0) > 0
                }
        }
    }

    // BARU: ubah status booking oleh petugas -> jadi notifikasi di sisi pelanggan
    BookingNotificationSync.start(this)

    return badge
}

/**
 * Memasang listener Firestore saat halaman tampil (onStart) dan melepasnya saat halaman
 * tidak tampil (onStop). Berbeda dengan addSnapshotListener(activity, ...) yang hanya
 * dipasang sekali dan TIDAK menyala lagi ketika kembali dari halaman lain.
 */
fun Activity.observeWhileStarted(register: () -> ListenerRegistration) {
    val owner = this as? LifecycleOwner ?: return
    var registration: ListenerRegistration? = null
    owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            registration?.remove()
            registration = register()
        }

        override fun onStop(owner: LifecycleOwner) {
            registration?.remove()
            registration = null
        }
    })
}

private fun Activity.toastTopBar(msg: String) =
    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

fun Activity.confirmLogout() {
    MaterialAlertDialogBuilder(this)
        .setTitle("Keluar")
        .setMessage("Yakin ingin keluar dari akun?")
        .setNegativeButton("Batal", null)
        .setPositiveButton("Keluar") { _, _ -> performLogout() }
        .show()
}

private fun Activity.performLogout() {
    FirebaseAuth.getInstance().signOut()
    val intent = Intent(this, LoginActivity::class.java).apply {
        // Bersihkan back stack supaya tombol Back tidak kembali ke Home
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    startActivity(intent)
    finish()
}

fun Activity.applySystemBarInsets(root: View) {
    val topBarView = findViewById<View>(R.id.topBar)
    val appBar = topBarView?.parent as? View ?: root

    WindowCompat.getInsetsController(window, window.decorView)
        .isAppearanceLightStatusBars = true

    ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        appBar.setPadding(0, bars.top, 0, 0)
        insets
    }
}