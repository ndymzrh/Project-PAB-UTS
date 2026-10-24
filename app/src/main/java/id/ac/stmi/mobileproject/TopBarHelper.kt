package id.ac.stmi.mobileproject

import android.app.Activity
import android.graphics.Color
import android.widget.Toast
import android.content.Intent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.badge.ExperimentalBadgeUtils
@androidx.annotation.OptIn(ExperimentalBadgeUtils::class)
fun Activity.setupTopBar(
    showNotificationDot: Boolean = false,
    onSupport: () -> Unit = { toastTopBar("Bantuan") },
    onNotification: () -> Unit = { toastTopBar("Notifikasi") },
    onLogout: () -> Unit = { confirmLogout() }
): BadgeDrawable {

    val toolbar = findViewById<MaterialToolbar>(R.id.topBar)

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
        isVisible = showNotificationDot
    }
    toolbar.post {
        BadgeUtils.attachBadgeDrawable(badge, toolbar, R.id.action_notification)
    }

    return badge
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
