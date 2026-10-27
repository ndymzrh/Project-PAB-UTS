package id.ac.stmi.mobileproject

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Kalender bulanan yang tampil langsung di halaman (bukan dialog), seperti booking hotel.
 * - Tanggal sebelum hari ini tidak bisa dipilih.
 * - Minggu dimulai dari Senin.
 * - Hasil pilihan dikirim sebagai teks "yyyy-MM-dd" lewat [onDateSelected].
 *
 * Dipakai di XML:
 *   <id.ac.stmi.mobileproject.InlineCalendarView android:id="@+id/calendarView" ... />
 */
class InlineCalendarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    var onDateSelected: ((String) -> Unit)? = null

    private val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val bulanFormat = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))

    private val today = Calendar.getInstance().apply { nolkanJam() }
    private val bulanIni = (today.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    private val month = bulanIni.clone() as Calendar   // bulan yang sedang ditampilkan
    private var selected: String? = null

    private val cPrimary = ContextCompat.getColor(context, R.color.bk_primary)
    private val cAccent = ContextCompat.getColor(context, R.color.bk_accent)
    private val cText = ContextCompat.getColor(context, R.color.bk_text_primary)
    private val cMuted = ContextCompat.getColor(context, R.color.bk_text_secondary)

    private val tvBulan = TextView(context)
    private val btnPrev = ImageView(context)
    private val btnNext = ImageView(context)
    private val dayViews = ArrayList<TextView>(42)

    init {
        orientation = VERTICAL
        buildHeader()
        buildWeekdays()
        buildGrid()
        render()
    }

    /** Pilih tanggal dari luar (mis. saat layar dipulihkan) dan pindah ke bulannya. */
    fun setSelectedDate(iso: String?) {
        selected = iso
        iso?.let {
            runCatching { isoFormat.parse(it) }.getOrNull()?.let { d ->
                month.time = d
                month.set(Calendar.DAY_OF_MONTH, 1)
                month.nolkanJam()
            }
        }
        render()
    }

    // ---------- bangun tampilan ----------

    private fun buildHeader() {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun panah(v: ImageView, putar: Float, delta: Int) {
            v.setImageResource(R.drawable.ic_chevron_right)
            v.rotation = putar
            v.setColorFilter(cPrimary)
            v.setPadding(dp(8), dp(8), dp(8), dp(8))
            val tv = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)
            v.setBackgroundResource(tv.resourceId)
            v.setOnClickListener { geser(delta) }
            row.addView(v, LayoutParams(dp(40), dp(40)))
        }
        panah(btnPrev, 180f, -1)
        tvBulan.apply {
            gravity = Gravity.CENTER
            setTextColor(cPrimary)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(typeface, Typeface.BOLD)
        }
        row.addView(tvBulan, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        panah(btnNext, 0f, 1)
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun buildWeekdays() {
        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min").forEach { nama ->
            row.addView(TextView(context).apply {
                text = nama
                gravity = Gravity.CENTER
                setTextColor(cMuted)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTypeface(typeface, Typeface.BOLD)
            }, LayoutParams(0, dp(32), 1f))
        }
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
    }

    private fun buildGrid() {
        val grid = GridLayout(context).apply { columnCount = 7; rowCount = 6 }
        for (i in 0 until 42) {
            val cell = FrameLayout(context)
            val tv = TextView(context).apply {
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            }
            cell.addView(tv, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER))
            dayViews.add(tv)
            grid.addView(cell, GridLayout.LayoutParams(
                GridLayout.spec(i / 7), GridLayout.spec(i % 7, 1f)
            ).apply { width = 0; height = dp(44) })
        }
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    // ---------- isi tampilan ----------

    private fun geser(delta: Int) {
        val target = (month.clone() as Calendar).apply { add(Calendar.MONTH, delta) }
        if (target.before(bulanIni)) return // tidak boleh sebelum bulan ini
        month.time = target.time
        render()
    }

    private fun render() {
        tvBulan.text = bulanFormat.format(month.time).replaceFirstChar { it.uppercase() }

        val bisaMundur = month.after(bulanIni)
        btnPrev.isEnabled = bisaMundur
        btnPrev.alpha = if (bisaMundur) 1f else 0.3f

        val offset = (month.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Senin = 0
        val jumlahHari = month.getActualMaximum(Calendar.DAY_OF_MONTH)
        val kursor = month.clone() as Calendar

        for (i in 0 until 42) {
            val tv = dayViews[i]
            val hari = i - offset + 1
            if (hari < 1 || hari > jumlahHari) {
                tv.visibility = View.INVISIBLE
                tv.setOnClickListener(null)
                tv.isClickable = false
                continue
            }
            tv.visibility = View.VISIBLE
            kursor.set(Calendar.DAY_OF_MONTH, hari)
            val iso = isoFormat.format(kursor.time)
            val lampau = kursor.before(today)
            val dipilih = iso == selected
            val hariIni = !lampau && iso == isoFormat.format(today.time)

            tv.text = hari.toString()
            when {
                dipilih -> {
                    tv.background = lingkaran(cAccent, 0)
                    tv.setTextColor(Color.WHITE)
                    tv.setTypeface(null, Typeface.BOLD)
                }
                hariIni -> {
                    tv.background = lingkaran(Color.TRANSPARENT, cPrimary)
                    tv.setTextColor(cPrimary)
                    tv.setTypeface(null, Typeface.BOLD)
                }
                else -> {
                    tv.background = null
                    tv.setTextColor(if (lampau) cMuted else cText)
                    tv.setTypeface(null, Typeface.NORMAL)
                }
            }
            tv.alpha = if (lampau) 0.4f else 1f
            tv.isClickable = !lampau
            tv.setOnClickListener(
                if (lampau) null else View.OnClickListener {
                    selected = iso
                    render()
                    onDateSelected?.invoke(iso)
                }
            )
        }
    }

    private fun lingkaran(isi: Int, garis: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(isi)
        if (garis != 0) setStroke(dp(2), garis)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun Calendar.nolkanJam() {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
}