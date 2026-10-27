package id.ac.stmi.mobileproject

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class VoucherAdapter(
    private val onSalin: (VoucherSaya) -> Unit,
    private val onPakai: (VoucherSaya) -> Unit
) : ListAdapter<VoucherSaya, VoucherAdapter.VH>(Diff) {

    class VH(
        v: View,
        val judul: TextView,
        val label: TextView,
        val kode: TextView,
        val berlaku: TextView,
        val status: TextView,
        val salin: TextView,
        val pakai: TextView
    ) : RecyclerView.ViewHolder(v)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        fun dp(n: Int) = (n * ctx.resources.displayMetrics.density).toInt()
        fun warna(id: Int) = ContextCompat.getColor(ctx, id)

        fun tombol(teks: String, bg: Int, fg: Int) = TextView(ctx).apply {
            text = teks
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_status_chip)
            backgroundTintList = ColorStateList.valueOf(bg)
            setTextColor(fg)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(null, Typeface.BOLD)
        }

        val judul = TextView(ctx).apply {
            setTextColor(warna(R.color.bk_text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(null, Typeface.BOLD)
        }
        val label = TextView(ctx).apply {
            setBackgroundResource(R.drawable.bg_status_chip)
            backgroundTintList = ColorStateList.valueOf(warna(R.color.bk_accent))
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
        }
        val atas = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(judul, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
            addView(label)
        }

        val kode = TextView(ctx).apply {
            setTextColor(warna(R.color.bk_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(null, Typeface.BOLD)
        }
        val salin = tombol("Salin", Color.WHITE, warna(R.color.bk_accent)).apply {
            setPadding(dp(14), dp(6), dp(14), dp(6))
        }
        val barisKode = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundResource(R.drawable.bg_status_chip)
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F2F5FA"))
            addView(kode, LinearLayout.LayoutParams(0, -2, 1f))
            addView(salin)
        }

        val berlaku = TextView(ctx).apply {
            setTextColor(warna(R.color.bk_text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        val status = TextView(ctx).apply {
            setBackgroundResource(R.drawable.bg_status_chip)
            setPadding(dp(10), dp(3), dp(10), dp(3))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(null, Typeface.BOLD)
        }
        val barisInfo = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(berlaku, LinearLayout.LayoutParams(0, -2, 1f))
            addView(status)
        }

        val pakai = tombol("Pakai di Booking", warna(R.color.bk_primary), Color.WHITE)

        val isi = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            addView(atas)
            addView(barisKode, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            addView(barisInfo, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            addView(pakai, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(12) })
        }
        val kartu = MaterialCardView(ctx).apply {
            layoutParams = RecyclerView.LayoutParams(-1, -2).apply {
                // Margin horizontal 0 karena XML sudah punya padding 16dp kiri/kanan
                setMargins(0, dp(6), 0, dp(6))
            }
            radius = dp(20).toFloat()
            cardElevation = dp(3).toFloat()
            strokeWidth = dp(1)
            strokeColor = warna(R.color.bk_stroke_soft)
            setCardBackgroundColor(Color.WHITE)
            addView(isi)
        }
        return VH(kartu, judul, label, kode, berlaku, status, salin, pakai)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val v = getItem(position)
        val st = v.status()
        h.judul.text = v.judul
        h.label.text = v.label
        h.kode.text = v.kode
        h.berlaku.text = "Berlaku s/d ${v.berakhir.toTanggal()}"
        h.status.text = st.label
        h.status.backgroundTintList = ColorStateList.valueOf(
            Color.parseColor(if (st == StatusVoucher.TERSEDIA) "#2E7D32" else "#9AA3AF")
        )
        h.salin.setOnClickListener { onSalin(v) }

        // Tombol pakai hanya aktif untuk voucher yang masih tersedia
        val aktif = st == StatusVoucher.TERSEDIA
        h.pakai.visibility = if (aktif) View.VISIBLE else View.GONE
        h.salin.visibility = if (aktif) View.VISIBLE else View.GONE
        h.pakai.setOnClickListener { onPakai(v) }
    }

    private object Diff : DiffUtil.ItemCallback<VoucherSaya>() {
        override fun areItemsTheSame(a: VoucherSaya, b: VoucherSaya) = a.id == b.id
        override fun areContentsTheSame(a: VoucherSaya, b: VoucherSaya) = a == b
    }
}