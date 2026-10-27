package id.ac.stmi.mobileproject

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import id.ac.stmi.mobileproject.databinding.ActivityRiwayatPoinCustBinding
import id.ac.stmi.mobileproject.databinding.ItemPointsTransactionBinding
import java.text.NumberFormat
import java.util.Locale

class RiwayatPoinCustActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRiwayatPoinCustBinding
    private val format = NumberFormat.getIntegerInstance(Locale("id", "ID"))
    private var listener: ListenerRegistration? = null
    private var allTransactions: List<PointsTransaction> = emptyList()
    private var selectedTab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRiwayatPoinCustBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.tabFilter.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                selectedTab = tab.position
                render()
            }

            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) = Unit
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) = Unit
        })

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            toast("Silakan login terlebih dahulu")
            finish()
            return
        }
        listener = PointsRepository.listen(
            uid = uid,
            onBalance = { balance -> binding.tvTotalPoin.text = "${format.format(balance)} poin" },
            onTransactions = { allTransactions = it; render() },
            onError = { toast("Gagal memuat riwayat poin") }
        )
    }

    override fun onDestroy() {
        listener?.remove()
        super.onDestroy()
    }

    private fun render() {
        val filtered = when (selectedTab) {
            1 -> allTransactions.filter { it.type == "EARN" }
            2 -> allTransactions.filter { it.type != "EARN" }
            else -> allTransactions
        }
        binding.riwayatContainer.removeAllViews()
        filtered.forEach { transaction ->
            val row = ItemPointsTransactionBinding.inflate(layoutInflater, binding.riwayatContainer, false)
            val isEarn = transaction.type == "EARN"
            row.tvType.text = if (isEarn) "POINTS MASUK" else "POINTS KELUAR"
            row.tvType.setTextColor(
                ContextCompat.getColor(this, if (isEarn) R.color.bk_primary else R.color.bk_accent)
            )
            row.tvDescription.text = transaction.description.ifBlank { "Transaksi points" }
            row.tvBookingId.text = "Booking: ${transaction.bookingId.ifBlank { "-" }}"
            row.tvDate.text = PointsRepository.displayDate(transaction.createdAt)
            val prefix = if (transaction.points >= 0) "+" else ""
            row.tvPoints.text = "$prefix${format.format(transaction.points)}"
            row.tvPoints.setTextColor(
                ContextCompat.getColor(this, if (isEarn) R.color.bk_primary else R.color.bk_accent)
            )
            binding.riwayatContainer.addView(row.root)
        }
        binding.tvKosong.isVisible = filtered.isEmpty()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
