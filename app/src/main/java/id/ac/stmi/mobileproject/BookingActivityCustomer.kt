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
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import id.ac.stmi.mobileproject.databinding.ActivityBookingCustomerBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class BookingActivityCustomer : AppCompatActivity() {

    private lateinit var binding: ActivityBookingCustomerBinding
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val dateStoreFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val jadwalFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale("id", "ID"))
    private val idFormat = NumberFormat.getIntegerInstance(Locale("id", "ID"))
    private val timeChips = linkedMapOf<String, Chip>()

    private var vehicleListener: ListenerRegistration? = null
    private var pointsListener: ListenerRegistration? = null
    private var serviceLoaded = false
    private var vehicles: List<Vehicle> = emptyList()
    private var selectedVehicle: Vehicle? = null
    private var selectedServices: List<LayananItem> = emptyList()
    private var selectedWorkshopId: String? = null
    private var selectedWorkshopName: String? = null
    private var selectedDateIso: String? = null
    private var selectedTime: String? = null
    private var vouchers: List<VoucherSaya> = emptyList()          // voucher yang cocok dengan layanan terpilih
    private var vouchersTersedia: List<VoucherSaya> = emptyList()  // semua voucher yang belum dipakai
    private var selectedVoucher: VoucherSaya? = null
    private var voucherListener: PromoListener? = null
    private var bookingId = BookingRepository.newBookingId()
    private var submitting = false
    private var userPoints = 0L

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

        // Poin
        binding.etUsePoints.doAfterTextChanged { renderTotals() }
        binding.btnUseAllPoints.setOnClickListener {
            binding.etUsePoints.setText(maxUsablePoints().toString())
        }

        renderSelectedDateTime()
        loadServices()
        loadVehicles()
        loadVouchers()
        loadPoints()
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
        pointsListener?.remove()
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
            refreshVoucherOptions()
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
            vouchersTersedia = list.filter { it.status() == StatusVoucher.TERSEDIA }
            refreshVoucherOptions()
            renderTotals()
        }
    }

    /**
     * Hanya voucher yang namanya SAMA/MIRIP dengan layanan terpilih yang bisa dipilih.
     * Voucher untuk layanan lain disembunyikan.
     */
    private fun refreshVoucherOptions() {
        val namaLayanan = selectedServices.map { it.judul }
        vouchers = vouchersTersedia.filter { it.bisaDipakaiUntuk(namaLayanan) }

        // Voucher yang sedang dipilih tapi tidak cocok lagi -> dilepas
        if (selectedVoucher != null && vouchers.none { it.id == selectedVoucher?.id }) {
            selectedVoucher = null
        }

        val labels = listOf("Tanpa voucher") + vouchers.map { "${it.judul} • ${it.label}" }
        binding.actVoucher.setAdapter(ArrayAdapter(this, R.layout.item_dropdown, labels))
        binding.actVoucher.setText(
            selectedVoucher?.let { "${it.judul} • ${it.label}" } ?: labels.first(),
            false
        )
    }

    // ------------------------------------------------------------------
    // POIN
    // ------------------------------------------------------------------
    private fun loadPoints() {
        val uid = auth.currentUser?.uid ?: return
        pointsListener = FirebaseFirestore.getInstance().collection("users").document(uid)
            .addSnapshotListener(this) { snap, _ ->
                userPoints = (snap?.get("points") as? Number)?.toLong() ?: 0L
                binding.tvCurrentPoints.text = "Saldo tersedia: ${idFormat.format(userPoints)} poin"
                renderTotals()
            }
    }

    private fun subtotalServices(): Long = selectedServices.sumOf { it.harga }

    /** Dasar diskon: voucher hanya memotong harga layanan yang namanya cocok. */
    private fun dasarDiskon(voucher: VoucherSaya): Long =
        selectedServices
            .filter { voucher.bisaDipakaiUntuk(it.judul) }
            .sumOf { it.harga }

    private fun voucherDiscount(): Long =
        selectedVoucher?.let { calculateDiscount(it, dasarDiskon(it)) } ?: 0L

    private fun afterVoucher(): Long =
        (subtotalServices() - voucherDiscount()).coerceAtLeast(0L)

    private fun maxUsablePoints(): Long =
        minOf(userPoints, afterVoucher() / BookingRepository.POINT_VALUE)

    private fun pointsToUse(): Long {
        val input = binding.etUsePoints.text?.toString()?.toLongOrNull() ?: 0L
        return input.coerceIn(0L, maxUsablePoints())
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
        val subtotal = subtotalServices()
        val discount = voucherDiscount()
        val pointDiscount = pointsToUse() * BookingRepository.POINT_VALUE
        val total = (subtotal - discount - pointDiscount).coerceAtLeast(0L)
        binding.tvSubtotal.text = "Subtotal: ${subtotal.keRupiah()}"
        binding.tvDiscount.text = "Diskon: - ${discount.keRupiah()}"
        binding.tvPointDiscount.text = "Potongan poin: ${pointDiscount.keRupiah()}"
        binding.tvSummaryPoin.text = "Potongan poin: - ${pointDiscount.keRupiah()}"
        binding.tvTotal.text = total.keRupiah()
        binding.tvEstimatedPoints.text = "${BookingRepository.estimatedPoints(total).keRupiah().removePrefix("Rp ")} points"
    }

    private fun calculateDiscount(voucher: VoucherSaya, subtotal: Long): Long =
        BookingRepository.hitungDiskonVoucher(
            voucher.jenisDiskon, voucher.nilaiDiskon, voucher.label, subtotal
        )

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
                    requestedDiscount = voucherDiscount(),   // voucher saja
                    pointsUsed = pointsToUse()               // poin dipotong atomic di create()
                )
                BookingRepository.create(
                    uid = uid,
                    bookingId = bookingId,
                    draft = draft,
                    onSuccess = {
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