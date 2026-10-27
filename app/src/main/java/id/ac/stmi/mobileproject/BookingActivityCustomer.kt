package id.ac.stmi.mobileproject

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import id.ac.stmi.mobileproject.databinding.ActivityBookingCustomerBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class BookingActivityCustomer : AppCompatActivity() {

    private lateinit var binding: ActivityBookingCustomerBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val dateStoreFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val dateDisplayFormat = SimpleDateFormat("dd/MM/yyyy", Locale("id", "ID"))
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
    private val calendar = Calendar.getInstance()

    private var vehicleListener: ListenerRegistration? = null
    private var serviceLoaded = false
    private var vehicles: List<Vehicle> = emptyList()
    private var selectedVehicle: Vehicle? = null
    private var selectedServices: List<LayananItem> = emptyList()
    private var selectedWorkshopId: String? = null
    private var selectedWorkshopName: String? = null
    private var selectedDateIso: String? = null
    private var selectedTime: String? = null
    private var vouchers: List<VoucherSaya> = emptyList()
    private var selectedVoucher: VoucherSaya? = null
    private var voucherListener: PromoListener? = null
    private var bookingId = BookingRepository.newBookingId()
    private var submitting = false

    private val workshopLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            selectedWorkshopId = result.data?.getStringExtra(BengkelActivityCustomer.RESULT_ID)
            selectedWorkshopName = result.data?.getStringExtra(BengkelActivityCustomer.RESULT_NAMA)
            renderWorkshop()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityBookingCustomerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        bookingId = savedInstanceState?.getString(KEY_BOOKING_ID) ?: bookingId
        selectedDateIso = savedInstanceState?.getString(KEY_DATE)
        selectedTime = savedInstanceState?.getString(KEY_TIME)

        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnAddVehicle.setOnClickListener {
            startActivity(Intent(this, AddVehicleActivityCustomer::class.java))
        }
        binding.btnChooseWorkshop.setOnClickListener {
            workshopLauncher.launch(
                Intent(this, BengkelListActivityCustomer::class.java)
                    .putExtra(BengkelActivityCustomer.EXTRA_MODE_PILIH, true)
            )
        }
        binding.etDate.setOnClickListener { chooseDate() }
        binding.etTime.setOnClickListener { chooseTime() }
        binding.btnConfirm.setOnClickListener { submitBooking() }
        binding.actVoucher.setOnItemClickListener { _, _, position, _ ->
            selectedVoucher = vouchers.getOrNull(position - 1)
            renderTotals()
        }

        renderSelectedDateTime()
        loadServices()
        loadVehicles()
        loadVouchers()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_BOOKING_ID, bookingId)
        outState.putString(KEY_DATE, selectedDateIso)
        outState.putString(KEY_TIME, selectedTime)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        vehicleListener?.remove()
        voucherListener?.remove()
        super.onDestroy()
    }

    private fun loadServices() {
        val ids = intent.getStringArrayListExtra(EXTRA_SERVICE_IDS).orEmpty().toSet()
        if (ids.isEmpty()) {
            toast("Pilih minimal satu layanan")
            finish()
            return
        }
        LayananCatalog.listenSemua(
            this,
            onError = { toast("Gagal memuat layanan") }
        ) { list ->
            selectedServices = list.filter { it.id in ids }
            serviceLoaded = true
            if (selectedServices.isEmpty()) {
                toast("Layanan tidak tersedia")
                finish()
                return@listenSemua
            }
            binding.tvServiceSummary.text = selectedServices.joinToString("\n") {
                "• ${it.judul} — ${it.harga.keRupiah()}"
            }
            renderTotals()
        }
    }

    private fun loadVehicles() {
        val uid = auth.currentUser?.uid ?: return
        vehicleListener = VehicleRepository.listen(
            uid,
            onResult = { list ->
                vehicles = list
                if (selectedVehicle == null) selectedVehicle = list.firstOrNull { it.primary } ?: list.firstOrNull()
                val labels = list.map { "${it.displayName} • ${it.plateNumber}" }
                binding.actVehicle.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, labels))
                binding.actVehicle.setText(
                    selectedVehicle?.let { "${it.displayName} • ${it.plateNumber}" }.orEmpty(),
                    false
                )
                binding.layoutNoVehicle.isVisible = list.isEmpty()
                binding.actVehicle.isVisible = list.isNotEmpty()
                binding.btnConfirm.isEnabled = list.isNotEmpty()
            },
            onError = { toast("Gagal memuat kendaraan") }
        )
        binding.actVehicle.setOnItemClickListener { _, _, position, _ ->
            selectedVehicle = vehicles.getOrNull(position)
        }
    }

    private fun loadVouchers() {
        voucherListener = VoucherStore.listen(this) { list ->
            vouchers = list.filter { it.status() == StatusVoucher.TERSEDIA }
            val labels = listOf("Tanpa voucher") + vouchers.map { "${it.judul} • ${it.label}" }
            binding.actVoucher.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, labels))
            if (selectedVoucher == null) binding.actVoucher.setText(labels.first(), false)
        }
    }

    private fun chooseDate() {
        val now = Calendar.getInstance()
        val initial = selectedDateIso?.let { runCatching { dateStoreFormat.parse(it) }.getOrNull() }
        val base = initial?.let { Calendar.getInstance().apply { time = it } } ?: now
        DatePickerDialog(
            this,
            { _, year, month, day ->
                calendar.set(year, month, day)
                selectedDateIso = dateStoreFormat.format(calendar.time)
                renderSelectedDateTime()
            },
            base.get(Calendar.YEAR),
            base.get(Calendar.MONTH),
            base.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = now.startOfDayMillis()
        }.show()
    }

    private fun chooseTime() {
        val base = selectedTime?.let { runCatching { timeFormat.parse(it) }.getOrNull() }
        val initial = Calendar.getInstance().apply { if (base != null) time = base }
        TimePickerDialog(
            this,
            { _, hour, minute ->
                selectedTime = String.format(Locale.US, "%02d:%02d", hour, minute)
                renderSelectedDateTime()
            },
            initial.get(Calendar.HOUR_OF_DAY),
            initial.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun renderSelectedDateTime() {
        binding.etDate.setText(
            selectedDateIso?.let { runCatching { dateDisplayFormat.format(dateStoreFormat.parse(it)!!) }.getOrNull() }.orEmpty()
        )
        binding.etTime.setText(selectedTime.orEmpty())
    }

    private fun renderWorkshop() {
        binding.tvWorkshop.text = selectedWorkshopName ?: "Belum memilih bengkel"
    }

    private fun renderTotals() {
        if (!serviceLoaded) return
        val subtotal = selectedServices.sumOf { it.harga }
        val discount = selectedVoucher?.let { calculateDiscount(it, subtotal) } ?: 0L
        val total = (subtotal - discount).coerceAtLeast(0L)
        binding.tvSubtotal.text = "Subtotal: ${subtotal.keRupiah()}"
        binding.tvDiscount.text = "Diskon: - ${discount.keRupiah()}"
        binding.tvTotal.text = total.keRupiah()
        binding.tvEstimatedPoints.text = "${BookingRepository.estimatedPoints(total).keRupiah().removePrefix("Rp ")} points"
    }

    private fun calculateDiscount(voucher: VoucherSaya, subtotal: Long): Long {
        val label = voucher.label.trim()
        val value = label.filter { it.isDigit() }.toLongOrNull() ?: return 0L
        return if (label.contains("%")) subtotal * value / 100L else value
    }

    private fun submitBooking() {
        if (submitting) return
        val uid = auth.currentUser?.uid ?: return
        val vehicle = selectedVehicle
        val workshopId = selectedWorkshopId
        val date = selectedDateIso
        val time = selectedTime
        when {
            vehicle == null -> toast("Pilih kendaraan terlebih dahulu")
            selectedServices.isEmpty() -> toast("Pilih minimal satu layanan")
            workshopId.isNullOrBlank() -> toast("Pilih bengkel terlebih dahulu")
            date.isNullOrBlank() || date < dateStoreFormat.format(Calendar.getInstance().time) -> toast("Pilih tanggal yang valid")
            time.isNullOrBlank() -> toast("Pilih jam kedatangan")
            else -> {
                submitting = true
                binding.progress.isVisible = true
                binding.btnConfirm.isEnabled = false
                val draft = BookingDraft(
                    vehicleId = vehicle.id,
                    vehicleName = vehicle.displayName,
                    plateNumber = vehicle.plateNumber,
                    serviceIds = selectedServices.map { it.id },
                    workshopId = workshopId,
                    workshopName = selectedWorkshopName.orEmpty(),
                    bookingDate = date,
                    bookingTime = time,
                    notes = binding.etNotes.text?.toString()?.trim().orEmpty(),
                    voucherId = selectedVoucher?.id,
                    requestedDiscount = selectedVoucher?.let {
                        calculateDiscount(it, selectedServices.sumOf { service -> service.harga })
                    } ?: 0L
                )
                BookingRepository.create(
                    uid = uid,
                    bookingId = bookingId,
                    draft = draft,
                    onSuccess = {
                        selectedVoucher?.let { VoucherStore.tandaiDipakai(this, it.id) }
                        startActivity(
                            Intent(this, BookingSuccessActivity::class.java)
                                .putExtra(BookingSuccessActivity.EXTRA_BOOKING_ID, it)
                        )
                        finish()
                    },
                    onError = {
                        submitting = false
                        binding.progress.isVisible = false
                        binding.btnConfirm.isEnabled = true
                        toast(it.message ?: "Booking gagal disimpan")
                    }
                )
            }
        }
    }

    private fun Calendar.startOfDayMillis(): Long {
        return (clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_SERVICE_IDS = "service_ids"
        private const val KEY_BOOKING_ID = "booking_id"
        private const val KEY_DATE = "booking_date"
        private const val KEY_TIME = "booking_time"
    }
}
