package id.ac.stmi.mobileproject

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import id.ac.stmi.mobileproject.databinding.ActivityNotificationBinding
import id.ac.stmi.mobileproject.databinding.ItemNotificationBinding
import id.ac.stmi.mobileproject.databinding.ItemNotificationHeaderBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// =====================================================================
// MODEL
// =====================================================================

/**
 * Satu dokumen di Firestore: users/{uid}/notifications/{id}
 *
 *  judul       : "Booking Servis Dikonfirmasi!"
 *  isi         : isi pesan
 *  kategori    : status_servis | servis_selesai | booking | pengingat | promo | info_bengkel
 *  waktu       : Timestamp
 *  dibaca      : true / false
 *  aksi_label  : teks link (boleh kosong), misal "Lihat Detail Booking"
 *  aksi_target : tujuan link (boleh kosong): promo | status_servis | booking | riwayat
 */
data class NotificationItem(
    val id: String = "",
    val judul: String = "",
    val isi: String = "",
    val kategori: String = "",
    val waktu: Date = Date(),
    val dibaca: Boolean = false,
    val aksiLabel: String = "",
    val aksiTarget: String = ""
)

/** Baris di RecyclerView: judul grup ("HARI INI") atau kartu notifikasi. */
private sealed class NotifRow {
    data class Header(val label: String, val date: String) : NotifRow()
    data class Item(val data: NotificationItem) : NotifRow()
}

// Nilai filter chip
private const val FILTER_ALL = "all"
private const val FILTER_SERVIS = "servis"
private const val FILTER_BOOKING = "booking"
private const val FILTER_INFO = "info"

/** Tampilan tiap kategori: label chip, ikon, dan masuk filter yang mana. */
private data class CategoryMeta(val label: String, val icon: Int, val filter: String)

private fun metaOf(kategori: String) = when (kategori) {
    "status_servis" -> CategoryMeta("Status Servis", R.drawable.ic_build, FILTER_SERVIS)
    "servis_selesai" -> CategoryMeta("Servis Selesai", R.drawable.ic_svc_check_circle, FILTER_SERVIS)
    "booking" -> CategoryMeta("Booking", R.drawable.ic_calendar, FILTER_BOOKING)
    "pengingat" -> CategoryMeta("Pengingat Jadwal", R.drawable.ic_notification, FILTER_BOOKING)
    "promo" -> CategoryMeta("Promo", R.drawable.ic_gift, FILTER_INFO)
    else -> CategoryMeta("Info Bengkel", R.drawable.ic_svc_info, FILTER_INFO)
}

// =====================================================================
// FUNGSI WAKTU
// =====================================================================
private val localeId = Locale("id", "ID")
private val dateFormat = SimpleDateFormat("d MMM yyyy", localeId)
private val clockFormat = SimpleDateFormat("HH:mm", localeId)
private val shortDateFormat = SimpleDateFormat("d MMM", localeId)

private fun startOfDay(date: Date): Long =
    Calendar.getInstance().apply {
        time = date
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

/** 0 = hari ini, 1 = kemarin, dst. */
private fun dayDiff(date: Date): Int =
    ((startOfDay(Date()) - startOfDay(date)) / 86_400_000L).toInt()

private fun sectionLabel(date: Date): String = when (dayDiff(date)) {
    0 -> "HARI INI"
    1 -> "KEMARIN"
    in 2..7 -> "MINGGU LALU"
    else -> "LEBIH LAMA"
}

/** "10 mnt lalu", "2 jam lalu", "16:45 WIB", "18 Okt" */
private fun timeLabel(date: Date): String {
    val diff = System.currentTimeMillis() - date.time
    val minutes = diff / 60_000
    return when {
        diff < 60_000 -> "Baru saja"
        minutes < 60 -> "$minutes mnt lalu"
        dayDiff(date) == 0 -> "${minutes / 60} jam lalu"
        dayDiff(date) == 1 -> "${clockFormat.format(date)} WIB"
        else -> shortDateFormat.format(date)
    }
}

// =====================================================================
// ACTIVITY
// =====================================================================
class NotificationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationBinding

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private lateinit var uid: String
    private var allItems: List<NotificationItem> = emptyList()
    private var filter = FILTER_ALL

    private val adapter = NotifAdapter(
        onClick = { markRead(it) },
        onAction = { openAction(it) },
        onDismiss = { delete(it) }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityNotificationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val currentUid = auth.currentUser?.uid
        if (currentUid == null) {
            toast("Silakan login terlebih dahulu")
            finish()
            return
        }
        uid = currentUid

        binding.rvNotif.layoutManager = LinearLayoutManager(this)
        binding.rvNotif.adapter = adapter

        setupClicks()
        BookingNotificationSync.start(this) // ubah status booking -> notifikasi
        listenNotifications()

        // UJI COBA: hapus tanda // di bawah, jalankan SEKALI, lalu beri // lagi.
        // seedSampleNotifications()
    }

    private fun setupClicks() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnMarkAll.setOnClickListener { markAllRead() }

        binding.chipAll.setOnClickListener { setFilter(FILTER_ALL) }
        binding.chipServis.setOnClickListener { setFilter(FILTER_SERVIS) }
        binding.chipBooking.setOnClickListener { setFilter(FILTER_BOOKING) }
        binding.chipInfo.setOnClickListener { setFilter(FILTER_INFO) }
    }

    // ------------------------------------------------------------------
    // 1. AMBIL DATA (real-time): notifikasi baru langsung muncul sendiri
    // ------------------------------------------------------------------
    private fun listenNotifications() = observeWhileStarted {
        db.collection("users").document(uid).collection("notifications")
            .orderBy("waktu", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    toast("Gagal memuat notifikasi")
                    return@addSnapshotListener
                }
                allItems = snap?.documents.orEmpty()
                    .filter { it.getBoolean("dihapus") != true }
                    .map { d ->
                        NotificationItem(
                            id = d.id,
                            judul = d.getString("judul") ?: "",
                            isi = d.getString("isi") ?: "",
                            kategori = d.getString("kategori") ?: "",
                            waktu = d.getTimestamp("waktu")?.toDate() ?: Date(),
                            dibaca = d.getBoolean("dibaca") ?: false,
                            aksiLabel = d.getString("aksi_label") ?: "",
                            aksiTarget = d.getString("aksi_target") ?: ""
                        )
                    }
                render()
            }
    }

    // ------------------------------------------------------------------
    // 2. TAMPILKAN (filter + kelompokkan per hari)
    // ------------------------------------------------------------------
    private fun render() {
        // Jumlah belum dibaca
        val unread = allItems.count { !it.dibaca }
        binding.tvUnread.visibility = if (unread > 0) View.VISIBLE else View.GONE
        binding.tvUnread.text = "$unread belum dibaca"
        binding.btnMarkAll.visibility = if (unread > 0) View.VISIBLE else View.GONE

        // Terapkan filter
        val shown = allItems.filter { filter == FILTER_ALL || metaOf(it.kategori).filter == filter }

        // Kelompokkan: sisipkan Header setiap ganti hari
        val rows = mutableListOf<NotifRow>()
        var lastLabel: String? = null
        for (n in shown) {
            val label = sectionLabel(n.waktu)
            if (label != lastLabel) {
                rows += NotifRow.Header(label, dateFormat.format(n.waktu))
                lastLabel = label
            }
            rows += NotifRow.Item(n)
        }

        adapter.submit(rows)
        binding.tvEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun setFilter(value: String) {
        filter = value
        styleChip(binding.chipAll, value == FILTER_ALL)
        styleChip(binding.chipServis, value == FILTER_SERVIS)
        styleChip(binding.chipBooking, value == FILTER_BOOKING)
        styleChip(binding.chipInfo, value == FILTER_INFO)
        render()
    }

    private fun styleChip(chip: TextView, selected: Boolean) {
        chip.setBackgroundResource(if (selected) R.drawable.bg_ep_pill else R.drawable.bg_ep_chip_off)
        chip.setTextColor(
            if (selected) ContextCompat.getColor(this, android.R.color.white)
            else ContextCompat.getColor(this, R.color.bk_text_secondary)
        )
    }

    // ------------------------------------------------------------------
    // 3. AKSI: tandai dibaca / hapus / buka tujuan
    // ------------------------------------------------------------------
    private fun notifRef(id: String) =
        db.collection("users").document(uid).collection("notifications").document(id)

    private fun markRead(item: NotificationItem) {
        if (!item.dibaca) notifRef(item.id).update("dibaca", true)
    }

    private fun markAllRead() {
        val unread = allItems.filter { !it.dibaca }
        if (unread.isEmpty()) return
        val batch = db.batch()
        unread.forEach { batch.update(notifRef(it.id), "dibaca", true) }
        batch.commit().addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    /**
     * "Hapus" = disembunyikan (dihapus = true), bukan delete dokumen.
     * Kalau benar-benar didelete, pemantau status booking akan membuatnya lagi.
     */
    private fun delete(item: NotificationItem) {
        notifRef(item.id).update(mapOf("dihapus" to true, "dibaca" to true))
            .addOnFailureListener { toast("Gagal menghapus: ${it.message}") }
    }

    /** Link di bawah kartu. Sesuaikan tujuan dengan halaman yang sudah Anda punya. */
    private fun openAction(item: NotificationItem) {
        markRead(item)
        when (item.aksiTarget) {
            "promo" -> startActivity(Intent(this, PromoActivityCustomer::class.java))
            "status_servis" -> toast("Halaman status servis belum tersedia")
            "booking" -> toast("Halaman detail booking belum tersedia")
            "riwayat" -> toast("Halaman riwayat belum tersedia")
        }
    }

    // ------------------------------------------------------------------
    // 4. DATA CONTOH (untuk uji coba tampilan)
    // ------------------------------------------------------------------
    /**
     * Menulis 6 notifikasi contoh ke Firestore milik user yang sedang login.
     * Di aplikasi sungguhan, notifikasi dibuat oleh sisi bengkel/admin atau Cloud Functions,
     * bukan oleh aplikasi pelanggan.
     */
    private fun seedSampleNotifications() {
        val now = System.currentTimeMillis()
        fun ago(minutes: Long) = Timestamp(Date(now - minutes * 60_000))

        val samples = listOf(
            mapOf("judul" to "Mobil Sedang Dikerjakan (Servis Berjalan)",
                "isi" to "Toyota Avanza (B 1234 ABC) sedang dalam proses ganti oli mesin dan tune-up oleh teknisi Mas Budi.",
                "kategori" to "status_servis", "waktu" to ago(10), "dibaca" to false,
                "aksi_label" to "Lihat Status Servis →", "aksi_target" to "status_servis"),
            mapOf("judul" to "Booking Servis Dikonfirmasi!",
                "isi" to "Jadwal servis berkala untuk besok pukul 09:00 WIB telah disetujui bengkel. Kode Booking: #BK-78921.",
                "kategori" to "booking", "waktu" to ago(120), "dibaca" to false,
                "aksi_label" to "Lihat Detail Booking →", "aksi_target" to "booking"),
            mapOf("judul" to "Promo Baru: Diskon Ganti Oli 20%",
                "isi" to "Berlaku sampai akhir bulan untuk semua jenis kendaraan. Pakai kode OLI20 saat booking.",
                "kategori" to "promo", "waktu" to ago(180), "dibaca" to false,
                "aksi_label" to "Lihat Promo →", "aksi_target" to "promo"),
            mapOf("judul" to "Jam Operasional Akhir Pekan",
                "isi" to "Bengkel buka normal hari Sabtu–Minggu pukul 08:00 – 17:00 WIB. Disarankan booking melalui aplikasi.",
                "kategori" to "info_bengkel", "waktu" to ago(60 * 26), "dibaca" to true,
                "aksi_label" to "", "aksi_target" to ""),
            mapOf("judul" to "Servis Selesai & Kendaraan Siap Diambil",
                "isi" to "Pengerjaan servis berkala Toyota Avanza telah rampung 100%. Rincian biaya dapat ditinjau di tab Riwayat.",
                "kategori" to "servis_selesai", "waktu" to ago(60 * 24 * 5), "dibaca" to true,
                "aksi_label" to "Rincian Tagihan & Garansi →", "aksi_target" to "riwayat")
        )

        val col = db.collection("users").document(uid).collection("notifications")
        val batch = db.batch()
        samples.forEach { batch.set(col.document(), it) }
        batch.commit()
            .addOnSuccessListener { toast("Notifikasi contoh ditambahkan") }
            .addOnFailureListener { toast("Gagal: ${it.message}") }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

// =====================================================================
// ADAPTER
// =====================================================================
private class NotifAdapter(
    private val onClick: (NotificationItem) -> Unit,
    private val onAction: (NotificationItem) -> Unit,
    private val onDismiss: (NotificationItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<NotifRow> = emptyList()

    fun submit(list: List<NotifRow>) {
        rows = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = rows.size

    override fun getItemViewType(position: Int) =
        if (rows[position] is NotifRow.Header) TYPE_HEADER else TYPE_ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER)
            HeaderHolder(ItemNotificationHeaderBinding.inflate(inflater, parent, false))
        else
            ItemHolder(ItemNotificationBinding.inflate(inflater, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is NotifRow.Header -> (holder as HeaderHolder).b.apply {
                tvSection.text = row.label
                tvDate.text = row.date
            }
            is NotifRow.Item -> bindItem((holder as ItemHolder).b, row.data)
        }
    }

    private fun bindItem(b: ItemNotificationBinding, n: NotificationItem) {
        val meta = metaOf(n.kategori)
        b.ivIcon.setImageResource(meta.icon)
        b.tvCategory.text = meta.label
        b.tvTitle.text = n.judul
        b.tvBody.text = n.isi
        b.tvTime.text = timeLabel(n.waktu)

        // Belum dibaca: garis biru di kiri + titik biru
        val unread = if (!n.dibaca) View.VISIBLE else View.GONE
        b.viewAccent.visibility = unread
        b.viewDot.visibility = unread

        // INVISIBLE (bukan GONE) supaya tombol hapus tetap di kanan
        b.tvAction.visibility = if (n.aksiLabel.isBlank()) View.INVISIBLE else View.VISIBLE
        b.tvAction.text = n.aksiLabel

        b.root.setOnClickListener { onClick(n) }
        b.tvAction.setOnClickListener { onAction(n) }
        b.btnDismiss.setOnClickListener { onDismiss(n) }
    }

    private class HeaderHolder(val b: ItemNotificationHeaderBinding) : RecyclerView.ViewHolder(b.root)
    private class ItemHolder(val b: ItemNotificationBinding) : RecyclerView.ViewHolder(b.root)

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }
}