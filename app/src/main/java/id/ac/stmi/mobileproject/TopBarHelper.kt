package id.ac.stmi.mobileproject

import android.app.Activity
import android.graphics.Color
import android.widget.Toast
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.badge.ExperimentalBadgeUtils
@androidx.annotation.OptIn(ExperimentalBadgeUtils::class)
fun Activity.setupTopBar(
    showNotificationDot: Boolean = false,
    onSupport: () -> Unit = { toastTopBar("Bantuan") },
    onNotification: () -> Unit = { toastTopBar("Notifikasi") },
    onLogout: () -> Unit = { toastTopBar("Logout") }
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
