package id.ac.stmi.mobileproject

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.util.Pair as CorePair
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class LaporanPendapatanActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PRESET = "preset"
        const val PRESET_HARI_INI = "hari_ini"
        const val PRESET_7_HARI = "7_hari"
        const val PRESET_BULAN_INI = "bulan_ini"

        private const val TAG = "LaporanPendapatan"
        private const val COL_USERS = "users"
        private const val ROLE_PEMILIK = "pemilik"
        private const val MAX_PREVIEW = 50
        private const val BATAS_HARIAN_MS = 62L * 24L * 3600L * 1000L
        private const val CHIP_CUSTOM = 0
    }

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private val localeId = Locale("id", "ID")
    private val fmtTampil = SimpleDateFormat("dd MMM yyyy", localeId)
    private val fmtJam = SimpleDateFormat("dd/MM/yyyy HH:mm", localeId)
    private val fmtFile = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private var awal = 0L
    private var akhir = 0L
    private var chipAktif = R.id.chipBulanIni
    private var data: List<TransaksiPendapatan> = emptyList()
    private var requestKe = 0

    private lateinit var tvRentang: TextView
    private lateinit var tvTotalPendapatan: TextView
    private lateinit var tvTotalTransaksi: TextView
    private lateinit var tvRataRata: TextView
    private lateinit var tvTotalDiskon: TextView
    private lateinit var tvKosong: TextView
    private lateinit var tvCatatan: TextView
    private lateinit var tvJudulHarian: TextView
    private lateinit var tvJudulRincian: TextView
    private lateinit var cardHarian: View
    private lateinit var cardRincian: View
    private lateinit var llHarian: LinearLayout
    private lateinit var llRows: LinearLayout
    private lateinit var progress: ProgressBar
    private lateinit var btnCsv: MaterialButton
    private lateinit var btnPdf: MaterialButton

    private lateinit var chipHariIni: TextView
    private lateinit var chip7Hari: TextView
    private lateinit var chipBulanIni: TextView

    private var aksiTertunda: (() -> Unit)? = null
    private val izinTulis =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { diberi ->
            val aksi = aksiTertunda
            aksiTertunda = null
            if (diberi) aksi?.invoke()
            else toast("Izin penyimpanan ditolak, file tidak bisa disimpan")
        }

    private fun warna(id: Int) = ContextCompat.getColor(this, id)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_laporan_pendapatan)

        tvRentang = findViewById(R.id.tvRentang)
        tvTotalPendapatan = findViewById(R.id.tvTotalPendapatan)
        tvTotalTransaksi = findViewById(R.id.tvTotalTransaksi)
        tvRataRata = findViewById(R.id.tvRataRata)
        tvTotalDiskon = findViewById(R.id.tvTotalDiskon)
        tvKosong = findViewById(R.id.tvKosong)
        tvCatatan = findViewById(R.id.tvCatatan)
        tvJudulHarian = findViewById(R.id.tvJudulHarian)
        tvJudulRincian = findViewById(R.id.tvJudulRincian)
        cardHarian = findViewById(R.id.cardHarian)
        cardRincian = findViewById(R.id.cardRincian)
        llHarian = findViewById(R.id.llHarian)
        llRows = findViewById(R.id.llRows)
        progress = findViewById(R.id.progress)
        btnCsv = findViewById(R.id.btnCsv)
        btnPdf = findViewById(R.id.btnPdf)

        chipHariIni = findViewById(R.id.chipHariIni)
        chip7Hari = findViewById(R.id.chip7Hari)
        chipBulanIni = findViewById(R.id.chipBulanIni)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.cardTanggal).setOnClickListener { pilihTanggal() }
        btnCsv.setOnClickListener { eksporCsv() }
        btnPdf.setOnClickListener { eksporPdf() }

        pasangChip(R.id.chipHariIni)
        pasangChip(R.id.chip7Hari)
        pasangChip(R.id.chipBulanIni)

        chipAktif = when (intent.getStringExtra(EXTRA_PRESET)) {
            PRESET_HARI_INI -> R.id.chipHariIni
            PRESET_7_HARI -> R.id.chip7Hari
            else -> R.id.chipBulanIni
        }
        terapkanPreset(chipAktif)
        aturStatusChip(chipAktif)

        setTombolAktif(false)
        cekAksesPemilik()
    }

    private fun cekAksesPemilik() {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            finish()
            return
        }
        db.collection(COL_USERS).document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc.getString("role")?.lowercase() == ROLE_PEMILIK) {
                    muat()
                } else {
                    toast("Halaman ini hanya untuk pemilik")
                    finish()
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "Gagal cek role", it)
                toast("Gagal memverifikasi akses")
                finish()
            }
    }

    private fun pasangChip(id: Int) {
        findViewById<TextView>(id).setOnClickListener {
            chipAktif = id
            aturStatusChip(id)
            terapkanPreset(id)
            muat()
        }
    }

    private fun aturStatusChip(idAktif: Int) {
        val chips = listOf(chipHariIni, chip7Hari, chipBulanIni)
        val ids = listOf(R.id.chipHariIni, R.id.chip7Hari, R.id.chipBulanIni)

        for (i in chips.indices) {
            val chip = chips[i]
            val isSelected = (ids[i] == idAktif)
            if (isSelected) {
                chip.setBackgroundResource(R.drawable.bg_filter_chip_on)
                chip.setTextColor(warna(android.R.color.white))
            } else {
                chip.setBackgroundResource(R.drawable.bg_filter_chip_off)
                chip.setTextColor(warna(R.color.bk_text_secondary))
            }
        }
    }

    private fun terapkanPreset(id: Int) {
        val (a, b) = when (id) {
            R.id.chipHariIni -> RentangTanggal.hariIni()
            R.id.chip7Hari -> RentangTanggal.tujuhHari()
            else -> RentangTanggal.bulanIni()
        }
        awal = a
        akhir = b
        tvRentang.text = rentangTeks()
    }

    private fun lokalKeUtc(ms: Long): Long {
        val lokal = Calendar.getInstance().apply { timeInMillis = ms }
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(lokal.get(Calendar.YEAR), lokal.get(Calendar.MONTH), lokal.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }

    private fun pilihTanggal() {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Pilih rentang tanggal")
            .setSelection(CorePair(lokalKeUtc(awal), lokalKeUtc(akhir)))
            .build()
        picker.addOnPositiveButtonClickListener { sel ->
            awal = RentangTanggal.awalHari(RentangTanggal.utcKeLokal(sel.first))
            akhir = RentangTanggal.akhirHari(RentangTanggal.utcKeLokal(sel.second))
            chipAktif = CHIP_CUSTOM
            aturStatusChip(CHIP_CUSTOM)
            tvRentang.text = rentangTeks()
            muat()
        }
        picker.show(supportFragmentManager, "rentang")
    }

    private fun rentangTeks() = "${fmtTampil.format(Date(awal))} s.d. ${fmtTampil.format(Date(akhir))}"

    private fun muat() {
        val ke = ++requestKe
        progress.visibility = View.VISIBLE
        tvKosong.visibility = View.GONE
        setKontenVisible(false)
        setTombolAktif(false)

        PendapatanRepo.ambil(
            awal, akhir,
            onResult = { hasil ->
                if (ke != requestKe) return@ambil
                data = hasil
                progress.visibility = View.GONE
                tampilkan()
            },
            onError = {
                if (ke != requestKe) return@ambil
                Log.e(TAG, "Gagal memuat laporan", it)
                progress.visibility = View.GONE
                toast("Gagal memuat laporan")
            }
        )
    }

    private fun setKontenVisible(v: Boolean) {
        val vis = if (v) View.VISIBLE else View.GONE
        tvJudulHarian.visibility = vis
        cardHarian.visibility = vis
        tvJudulRincian.visibility = vis
        cardRincian.visibility = vis
    }

    private fun setTombolAktif(aktif: Boolean) {
        btnCsv.isEnabled = aktif
        btnPdf.isEnabled = aktif
    }

    private fun totalPendapatan() = data.sumOf { it.total }
    private fun totalDiskon() = data.sumOf { it.diskon }
    private fun totalSubtotal() = data.sumOf { it.subtotal }
    private fun rataRata() = if (data.isEmpty()) 0L else totalPendapatan() / data.size

    private fun perBulan() = (akhir - awal) > BATAS_HARIAN_MS

    private fun rekap(): List<Triple<String, Int, Long>> {
        val fmt = if (perBulan()) SimpleDateFormat("MMM yyyy", localeId)
        else SimpleDateFormat("EEE, dd MMM yyyy", localeId)
        val grup = LinkedHashMap<String, Pair<Int, Long>>()
        data.forEach { r ->
            val k = fmt.format(r.tanggal)
            val (n, t) = grup[k] ?: (0 to 0L)
            grup[k] = (n + 1) to (t + r.total)
        }
        return grup.map { (k, v) -> Triple(k, v.first, v.second) }
    }

    private fun tampilkan() {
        tvTotalPendapatan.text = PendapatanRepo.rupiah(totalPendapatan())
        tvTotalTransaksi.text = data.size.toString()
        tvRataRata.text = PendapatanRepo.rupiah(rataRata())
        tvTotalDiskon.text = PendapatanRepo.rupiah(totalDiskon())

        llHarian.removeAllViews()
        llRows.removeAllViews()
        tvCatatan.text = ""

        if (data.isEmpty()) {
            tvKosong.visibility = View.VISIBLE
            setKontenVisible(false)
            setTombolAktif(false)
            return
        }
        tvKosong.visibility = View.GONE
        setKontenVisible(true)
        setTombolAktif(true)
        tvJudulHarian.text = if (perBulan()) "PENDAPATAN PER BULAN" else "PENDAPATAN PER HARI"

        bangunRekap()
        bangunRincian()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun bangunRekap() {
        val rows = rekap()
        val maks = rows.maxOf { it.third }.coerceAtLeast(1L)

        rows.forEach { (label, jumlah, total) ->
            val baris = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                    .apply { topMargin = dp(6); bottomMargin = dp(6) }
            }
            baris.addView(TextView(this).apply {
                text = "$label\n$jumlah transaksi"
                textSize = 11f
                setTextColor(warna(R.color.bk_text_secondary))
                layoutParams = LinearLayout.LayoutParams(dp(112), WRAP_CONTENT)
            })
            baris.addView(
                ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100
                    progress = ((total * 100) / maks).toInt().coerceAtLeast(if (total > 0) 3 else 0)
                    progressTintList = ColorStateList.valueOf(warna(R.color.bk_primary))
                    layoutParams = LinearLayout.LayoutParams(0, dp(8), 1f)
                        .apply { marginStart = dp(4); marginEnd = dp(8) }
                }
            )
            baris.addView(TextView(this).apply {
                text = PendapatanRepo.rupiah(total)
                textSize = 12f
                setTextColor(warna(R.color.bk_text_primary))
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(dp(92), WRAP_CONTENT)
            })
            llHarian.addView(baris)
        }
    }

    private fun bangunRincian() {
        val pad = dp(14)
        val tampil = data.asReversed().take(MAX_PREVIEW)

        tampil.forEachIndexed { i, r ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(pad, pad, pad, pad)
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            }

            val kiri = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            }
            kiri.addView(TextView(this).apply {
                text = "${r.plat.ifBlank { "-" }} - ${r.pelanggan.ifBlank { "-" }}"
                textSize = 14f
                setTextColor(warna(R.color.bk_text_primary))
                setTypeface(typeface, Typeface.BOLD)
            })
            kiri.addView(TextView(this).apply {
                text = r.layanan.ifBlank { "-" }
                textSize = 12f
                setTextColor(warna(R.color.bk_text_secondary))
            })
            kiri.addView(TextView(this).apply {
                text = "${fmtJam.format(r.tanggal)} • ${r.petugas.ifBlank { "-" }}"
                textSize = 11f
                setTextColor(warna(R.color.bk_text_secondary))
            })
            row.addView(kiri)

            row.addView(TextView(this).apply {
                text = PendapatanRepo.rupiah(r.total)
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(warna(R.color.bk_primary))
                setPadding(dp(10), dp(6), dp(10), dp(6))
                background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(warna(R.color.bk_icon_circle_bg))
                    setStroke(dp(1), warna(R.color.bk_stroke_soft))
                }
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
                    .apply { marginStart = dp(8) }
            })
            llRows.addView(row)

            if (i < tampil.lastIndex) {
                llRows.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 1)
                    setBackgroundColor(warna(R.color.bk_stroke_soft))
                })
            }
        }
        tvCatatan.text = if (data.size > MAX_PREVIEW)
            "Menampilkan $MAX_PREVIEW transaksi terbaru dari ${data.size}. File download berisi semuanya."
        else ""
    }

    private fun namaFile(ext: String) =
        "Laporan_Pendapatan_${fmtFile.format(Date(awal))}_sd_${fmtFile.format(Date(akhir))}.$ext"

    private fun csv(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""

    private fun eksporCsv() = pastikanIzinTulis {
        simpanDanBeriTahu(namaFile("csv"), "text/csv", "CSV") { out -> tulisCsv(out) }
    }

    private fun eksporPdf() = pastikanIzinTulis {
        simpanDanBeriTahu(namaFile("pdf"), "application/pdf", "PDF") { out -> tulisPdf(out) }
    }

    private fun pastikanIzinTulis(aksi: () -> Unit) {
        val perluIzin = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED
        if (perluIzin) {
            aksiTertunda = aksi
            izinTulis.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            aksi()
        }
    }

    private fun simpanDanBeriTahu(
        nama: String, mime: String, label: String, tulis: (OutputStream) -> Unit
    ) {
        if (data.isEmpty()) {
            toast("Tidak ada data untuk diunduh")
            return
        }
        val uri = simpanFile(nama, mime, tulis)
        if (uri == null) {
            toast("Gagal menyimpan $label")
            return
        }
        val bar = Snackbar.make(
            findViewById(android.R.id.content),
            "$label tersimpan di folder Download:\n$nama",
            Snackbar.LENGTH_LONG
        )
        bar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.maxLines = 3
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            bar.setAction("BUKA") { bukaFile(uri, mime) }
        }
        bar.show()
    }

    private fun bukaFile(uri: Uri, mime: String) {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: ActivityNotFoundException) {
            toast("Tidak ada aplikasi untuk membuka file ini")
        }
    }

    private fun tulisCsv(out: OutputStream) {
        val sep = ";"
        val sb = StringBuilder()
        sb.append("\uFEFF")
        sb.append("Laporan Pendapatan").append(sep).append(csv(rentangTeks())).append("\n\n")
        sb.append(
            listOf(
                "Tanggal", "Pelanggan", "Plat Nomor", "Layanan", "Petugas",
                "Subtotal (Rp)", "Diskon (Rp)", "Total (Rp)"
            ).joinToString(sep)
        ).append('\n')
        data.forEach { r ->
            sb.append(
                listOf(
                    csv(fmtJam.format(r.tanggal)), csv(r.pelanggan), csv(r.plat),
                    csv(r.layanan), csv(r.petugas),
                    r.subtotal.toString(), r.diskon.toString(), r.total.toString()
                ).joinToString(sep)
            ).append('\n')
        }
        sb.append(
            listOf(
                "", "", "", "", csv("TOTAL"),
                totalSubtotal().toString(), totalDiskon().toString(), totalPendapatan().toString()
            ).joinToString(sep)
        ).append("\n\n")
        sb.append(if (perBulan()) "Rekap per Bulan" else "Rekap per Hari")
            .append(sep).append("Transaksi").append(sep).append("Pendapatan (Rp)").append('\n')
        rekap().forEach { (l, n, t) -> sb.append(csv(l)).append(sep).append(n).append(sep).append(t).append('\n') }

        out.write(sb.toString().toByteArray(Charsets.UTF_8))
        out.flush()
    }

    private fun tulisPdf(out: OutputStream) {
        val doc = PdfDocument()
        try {
            val judul = Paint().apply { textSize = 14f; isFakeBoldText = true }
            val sub = Paint().apply { textSize = 11f; isFakeBoldText = true }
            val tebal = Paint().apply { textSize = 9f; isFakeBoldText = true }
            val biasa = Paint().apply { textSize = 9f }

            var nomor = 1
            var page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, nomor).create())
            var c = page.canvas
            var y = 50f

            fun halamanBaru() {
                doc.finishPage(page)
                nomor++
                page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, nomor).create())
                c = page.canvas
                y = 50f
            }
            fun fit(s: String, max: Int) = if (s.length <= max) s else s.take(max - 1) + "…"

            c.drawText("Laporan Pendapatan - Annova AutoCare", 40f, y, judul); y += 18f
            c.drawText("Periode: ${rentangTeks()}", 40f, y, biasa); y += 18f
            c.drawText("Total pendapatan : ${PendapatanRepo.rupiah(totalPendapatan())}", 40f, y, tebal); y += 13f
            c.drawText("Transaksi selesai : ${data.size}", 40f, y, biasa); y += 13f
            c.drawText("Rata-rata / transaksi : ${PendapatanRepo.rupiah(rataRata())}", 40f, y, biasa); y += 13f
            c.drawText("Diskon diberikan : ${PendapatanRepo.rupiah(totalDiskon())}", 40f, y, biasa); y += 26f

            c.drawText(if (perBulan()) "Rekap per Bulan" else "Rekap per Hari", 40f, y, sub); y += 16f
            c.drawText("Periode", 40f, y, tebal)
            c.drawText("Transaksi", 300f, y, tebal)
            c.drawText("Pendapatan", 400f, y, tebal)
            y += 6f; c.drawLine(40f, y, 555f, y, biasa); y += 12f
            rekap().forEach { (l, n, t) ->
                if (y > 800f) halamanBaru()
                c.drawText(l, 40f, y, biasa)
                c.drawText(n.toString(), 300f, y, biasa)
                c.drawText(PendapatanRepo.rupiah(t), 400f, y, biasa)
                y += 14f
            }
            y += 18f

            fun headerTabel() {
                c.drawText("Rincian Transaksi", 40f, y, sub); y += 16f
                c.drawText("Tanggal", 40f, y, tebal)
                c.drawText("Pelanggan", 115f, y, tebal)
                c.drawText("Plat", 215f, y, tebal)
                c.drawText("Layanan", 270f, y, tebal)
                c.drawText("Petugas", 385f, y, tebal)
                c.drawText("Total", 480f, y, tebal)
                y += 6f; c.drawLine(40f, y, 555f, y, biasa); y += 12f
            }
            if (y > 740f) halamanBaru()
            headerTabel()
            data.forEach { r ->
                if (y > 800f) {
                    halamanBaru()
                    headerTabel()
                }
                c.drawText(fmtJam.format(r.tanggal), 40f, y, biasa)
                c.drawText(fit(r.pelanggan, 18), 115f, y, biasa)
                c.drawText(fit(r.plat, 10), 215f, y, biasa)
                c.drawText(fit(r.layanan, 22), 270f, y, biasa)
                c.drawText(fit(r.petugas, 17), 385f, y, biasa)
                c.drawText(PendapatanRepo.rupiah(r.total), 480f, y, biasa)
                y += 14f
            }

            if (y > 790f) halamanBaru()
            y += 2f; c.drawLine(40f, y, 555f, y, biasa); y += 13f
            c.drawText("TOTAL", 385f, y, tebal)
            c.drawText(PendapatanRepo.rupiah(totalPendapatan()), 480f, y, biasa)

            doc.finishPage(page)
            doc.writeTo(out)
            out.flush()
        } finally {
            doc.close()
        }
    }

    private fun simpanFile(nama: String, mime: String, tulis: (OutputStream) -> Unit): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, nama)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                try {
                    val stream = resolver.openOutputStream(uri) ?: throw IOException("Stream null")
                    stream.use { tulis(it) }
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    uri
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists() && !dir.mkdirs()) throw IOException("Folder Download tidak bisa dibuat")
                val file = File(dir, nama)
                file.outputStream().use { tulis(it) }
                MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), arrayOf(mime), null)
                Uri.fromFile(file)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menyimpan $nama", e)
            null
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}