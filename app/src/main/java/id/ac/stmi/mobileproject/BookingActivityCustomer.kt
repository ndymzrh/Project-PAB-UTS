package id.ac.stmi.mobileproject

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.google.android.material.chip.Chip
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
    private val jadwalFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("id", "ID"))
    private val timeChips = linkedMapOf<String, Chip>()

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
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        binding = ActivityBookingCustomerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        binding.actVehicle.setDropDownBackgroundResource(R.drawable.bg_dropdown_popup)
        binding.actVoucher.setDropDownBackgroundResource(R.drawable.bg_dropdown_popup)

        val atasAwal = binding.header.paddingTop
        val bawahAwal = binding.main.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.header.updatePadding(top = atasAwal + bars.top)
            binding.main.updatePadding(bottom = bawahAwal + bars.bottom)
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

        binding.btnBack.setOnClickListener { finish() }
        binding.btnAddVehicle.setOnClickListener {
            startActivity(Intent(this, AddVehicleActivityCustomer::class.java))
        }
        binding.btnChooseWorkshop.setOnClickListener {
            workshopLauncher.launch(
                Intent(this, BengkelListActivityCustomer::class.java)
                    .putExtra(BengkelActivityCustomer.EXTRA_MODE_PILIH, true)
            )
        }
        setupJadwal()
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
                binding.actVehicle.setAdapter(ArrayAdapter(this, R.layout.item_dropdown, labels))
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
            binding.actVoucher.setAdapter(ArrayAdapter(this, R.layout.item_dropdown, labels))
            if (selectedVoucher == null) binding.actVoucher.setText(labels.first(), false)
        }
    }

    // ------------------------------------------------------------------
    // JADWAL: kalender inline + chip jam (tanpa dialog)
    // ------------------------------------------------------------------
    private fun setupJadwal() {
        binding.calendarView.setSelectedDate(selectedDateIso) // pulihkan setelah layar diputar
        binding.calendarView.onDateSelected = { iso ->
            selectedDateIso = iso
            refreshJamTersedia()
            renderSelectedDateTime()
        }

        val primary = ContextCompat.getColor(this, R.color.bk_primary)
        val abu = ContextCompat.getColor(this, R.color.bk_text_secondary)
        val states = arrayOf(
            intArrayOf(-android.R.attr.state_enabled),
            intArrayOf(android.R.attr.state_checked),
            intArrayOf()
        )
        val warnaLatar = ColorStateList(states, intArrayOf(Color.parseColor("#F1F3F6"), primary, Color.WHITE))
        val warnaGaris = ColorStateList(states, intArrayOf(Color.parseColor("#D5DAE1"), primary, primary))
        val warnaTeks = ColorStateList(states, intArrayOf(abu, Color.WHITE, primary))

        JAM_TERSEDIA.forEach { jam ->
            val chip = Chip(this).apply {
                id = View.generateViewId()
                text = jam
                isCheckable = true
                isCheckedIconVisible = false
                chipBackgroundColor = warnaLatar
                chipStrokeColor = warnaGaris
                chipStrokeWidth = resources.displayMetrics.density
                setTextColor(warnaTeks)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        selectedTime = jam
                        renderSelectedDateTime()
                    }
                }
            }
            timeChips[jam] = chip
            binding.chipGroupJam.addView(chip)
        }
        selectedTime?.let { timeChips[it]?.isChecked = true }
        refreshJamTersedia()
    }

    /** Kalau tanggalnya hari ini, jam yang sudah lewat tidak bisa dipilih. */
    private fun refreshJamTersedia() {
        val sekarang = Calendar.getInstance()
        val hariIni = dateStoreFormat.format(sekarang.time) == selectedDateIso
        val menitSekarang = sekarang.get(Calendar.HOUR_OF_DAY) * 60 + sekarang.get(Calendar.MINUTE)
        timeChips.forEach { (jam, chip) ->
            val menit = jam.substring(0, 2).toInt() * 60 + jam.substring(3).toInt()
            val lewat = hariIni && menit <= menitSekarang
            chip.isEnabled = !lewat
            if (lewat && chip.isChecked) {
                binding.chipGroupJam.clearCheck()
                selectedTime = null
            }
        }
    }

    private fun renderSelectedDateTime() {
        val tanggal = selectedDateIso?.let {
            runCatching { jadwalFormat.format(dateStoreFormat.parse(it)!!) }.getOrNull()
        }
        binding.tvJadwalDipilih.text = when {
            tanggal != null && selectedTime != null -> "$tanggal • $selectedTime"
            tanggal != null -> "$tanggal • pilih jam"
            else -> "Pilih tanggal dan jam kedatangan"
        }
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

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_SERVICE_IDS = "service_ids"
        /** Pilihan jam kedatangan. Ubah daftar ini sesuai jam buka bengkel. */
        private val JAM_TERSEDIA = listOf("08:00", "09:00", "10:00", "11:00", "13:00", "14:00", "15:00", "16:00")
        private const val KEY_BOOKING_ID = "booking_id"
        private const val KEY_DATE = "booking_date"
        private const val KEY_TIME = "booking_time"
    }
}