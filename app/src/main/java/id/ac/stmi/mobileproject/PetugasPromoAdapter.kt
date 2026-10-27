package id.ac.stmi.mobileproject

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class PetugasPromoAdapter (private val onClick: (PromoItem) -> Unit) :
    ListAdapter<PromoItem, PetugasPromoAdapter.VH>(Diff) {

    class VH(
        v: View,
        val img: ImageView,
        val label: TextView,
        val judul: TextView,
        val berlaku: TextView,
        val detail: TextView
    ) : RecyclerView.ViewHolder(v)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        fun dp(n: Int) = (n * ctx.resources.displayMetrics.density).toInt()
        fun warna(id: Int) = ContextCompat.getColor(ctx, id)

        val img = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        val label = TextView(ctx).apply {
            setBackgroundResource(R.drawable.bg_status_chip)
            backgroundTintList = ColorStateList.valueOf(warna(R.color.bk_accent))
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
        }
        val thumb = FrameLayout(ctx).apply {
            addView(img, FrameLayout.LayoutParams(-1, -1))
            addView(label, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START)
                .apply { setMargins(dp(10), 0, 0, dp(8)) })
        }
        val judul = TextView(ctx).apply {
            minLines = 2; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
            setTextColor(warna(R.color.bk_text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(null, Typeface.BOLD)
        }
        val berlaku = TextView(ctx).apply {
            setTextColor(warna(R.color.bk_text_secondary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        }
        val detail = TextView(ctx).apply {
            text = "Lihat Detail"
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_status_chip)
            backgroundTintList = ColorStateList.valueOf(warna(R.color.bk_primary))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, Typeface.BOLD)
        }
        val isi = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(judul)
            addView(berlaku, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2) })
            addView(detail, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(10) })
        }
        val kolom = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(thumb, LinearLayout.LayoutParams(-1, dp(90)))
            addView(isi)
        }
        val kartu = MaterialCardView(ctx).apply {
            layoutParams = RecyclerView.LayoutParams(-1, -2).apply { setMargins(dp(6), dp(6), dp(6), dp(6)) }
            radius = dp(20).toFloat()
            cardElevation = dp(3).toFloat()
            strokeWidth = dp(1)
            strokeColor = warna(R.color.bk_stroke_soft)
            setCardBackgroundColor(Color.WHITE)
            isClickable = true
            isFocusable = true
            addView(kolom)
        }
        return VH(kartu, img, label, judul, berlaku, detail)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val p = getItem(position)
        val ctx = h.itemView.context
        h.img.setImageResource(ctx.drawableId(p.gambar))
        h.label.text = p.labelDiskon
        h.judul.text = p.judul
        h.berlaku.text = "s/d ${p.berakhir.toTanggal()}"
        h.itemView.setOnClickListener { onClick(p) }
        h.detail.setOnClickListener { onClick(p) }
    }

    private object Diff : DiffUtil.ItemCallback<PromoItem>() {
        override fun areItemsTheSame(a: PromoItem, b: PromoItem) = a.id == b.id
        override fun areContentsTheSame(a: PromoItem, b: PromoItem) = a == b
    }
}