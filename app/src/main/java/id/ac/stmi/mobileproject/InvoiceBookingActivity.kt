package id.ac.stmi.mobileproject

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import id.ac.stmi.mobileproject.databinding.ActivityInvoiceBookingBinding
import id.ac.stmi.mobileproject.databinding.ItemInvoiceLineBinding

/** Invoice booking yang sudah selesai (customer). Bisa dibagikan sebagai teks. */
class InvoiceBookingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInvoiceBookingBinding
    private var listener: ListenerRegistration? = null
    private var teksInvoice: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityInvoiceBookingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false

        val headerTop = binding.header.paddingTop
        val scrollBottom = binding.scroll.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.header.updatePadding(top = headerTop + bars.top)
            binding.scroll.updatePadding(bottom = scrollBottom + bars.bottom)
            insets
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.btnBagikan.setOnClickListener {
            if (teksInvoice.isBlank()) return@setOnClickListener
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, teksInvoice),
                    "Bagikan invoice"
                )
            )
        }

        val bookingId = intent.getStringExtra(EXTRA_BOOKING_ID)
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (bookingId.isNullOrBlank() || uid.isNullOrBlank()) {
            toast("Data booking tidak ditemukan")
            finish()
            return
        }

        listener = BookingRepository.listenOne(
            uid = uid,
            bookingId = bookingId,
            onResult = { booking ->
                binding.progress.isVisible = false
                when {
                    booking == null -> tampilkanKosong("Booking tidak ditemukan")
                    booking.status != BookingStatus.SELESAI ->
                        tampilkanKosong("Invoice tersedia setelah servis selesai.")
                    else -> render(booking)
                }
            },
            onError = {
                binding.progress.isVisible = false
                tampilkanKosong("Gagal memuat invoice")
            }
        )
    }

    override fun onDestroy() {
        listener?.remove()
        super.onDestroy()
    }

    private fun tampilkanKosong(pesan: String) {
        binding.scroll.isVisible = false
        binding.tvKosong.text = pesan
        binding.tvKosong.isVisible = true
    }

    private fun render(b: BookingRecord) = with(binding) {
        scroll.isVisible = true
        tvKosong.isVisible = false

        val noInvoice = "INV-" + b.id.removePrefix("BK-")
        val tanggal = if (b.completedAt != null) PointsRepository.displayDate(b.completedAt)
        else b.scheduleText

        tvNoInvoice.text = noInvoice
        tvBengkel.text = b.workshopName
        tvTanggal.text = tanggal
        tvCustomer.text = b.customerName
        tvKendaraan.text = "${b.vehicleName} • ${b.plateNumber}"
        tvBookingId.text = b.id

        layoutItems.removeAllViews()
        val inflater = LayoutInflater.from(this@InvoiceBookingActivity)
        b.services.forEach { s ->
            val row = ItemInvoiceLineBinding.inflate(inflater, layoutItems, false)
            row.tvLineName.text = s.name
            row.tvLinePrice.text = s.price.keRupiah()
            layoutItems.addView(row.root)
        }

        val subtotal = if (b.subtotal > 0L) b.subtotal else b.services.sumOf { it.price }
        tvSubtotal.text = subtotal.keRupiah()
        rowDiskon.isVisible = b.discount > 0L
        tvDiskon.text = "-" + b.discount.keRupiah()
        tvTotal.text = b.totalPriceText
        tvPoints.text = if (b.pointsStatus == PointsStatus.AWARDED)
            "Points diperoleh: ${b.awardedPoints}" else ""
        tvPoints.isVisible = tvPoints.text.isNotEmpty()

        teksInvoice = buildString {
            appendLine("INVOICE $noInvoice")
            appendLine("Annova AutoCare - ${b.workshopName}")
            appendLine("Tanggal : $tanggal")
            appendLine("Customer: ${b.customerName}")
            appendLine("Kendaraan: ${b.vehicleName} (${b.plateNumber})")
            appendLine()
            b.services.forEach { appendLine("- ${it.name}: ${it.price.keRupiah()}") }
            appendLine()
            appendLine("Subtotal: ${subtotal.keRupiah()}")
            if (b.discount > 0L) appendLine("Diskon  : -${b.discount.keRupiah()}")
            appendLine("TOTAL   : ${b.totalPriceText}")
            if (b.pointsStatus == PointsStatus.AWARDED) appendLine("Points diperoleh: ${b.awardedPoints}")
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_BOOKING_ID = "booking_id"
    }
}