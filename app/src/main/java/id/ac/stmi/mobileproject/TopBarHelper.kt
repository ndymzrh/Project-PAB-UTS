package id.ac.stmi.mobileproject

import android.app.Activity
import android.graphics.Color
import android.widget.Toast
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.badge.BadgeUtils
import com.google.android.material.badge.ExperimentalBadgeUtils

/**
 * Pasang top bar (res/menu/menu_top_bar.xml) di Activity mana pun.
 *
 * Syarat: layout Activity sudah memuat  <include layout="@layout/include_top_bar" />
 *
 * Pemakaian di Activity, setelah setContentView(...):
 *     val notifBadge = setupTopBar(showNotificationDot = true)
 *     // nanti: notifBadge.isVisible = false  (saat semua notifikasi sudah dibaca)
 *
 * @return badge titik merah pada ikon notifikasi, bisa diubah isVisible-nya.
 */
@androidx.annotation.OptIn(ExperimentalBadgeUtils::class)
fun Activity.setupTopBar(
    showNotificationDot: Boolean = false,
    onLanguage: () -> Unit = { toastTopBar("Pilih bahasa") },
    onSupport: () -> Unit = { toastTopBar("Bantuan") },
    onNotification: () -> Unit = { toastTopBar("Notifikasi") }
): BadgeDrawable {

    val toolbar = findViewById<MaterialToolbar>(R.id.topBar)

    toolbar.setOnMenuItemClickListener { item ->
        when (item.itemId) {
            R.id.action_language -> { onLanguage(); true }
            R.id.action_support -> { onSupport(); true }
            R.id.action_notification -> { onNotification(); true }
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
