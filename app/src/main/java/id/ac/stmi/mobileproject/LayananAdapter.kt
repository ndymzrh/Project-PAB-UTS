package id.ac.stmi.mobileproject

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import id.ac.stmi.mobileproject.databinding.ItemLayananBinding
import java.text.NumberFormat
import java.util.Locale

data class Layanan(
    val id: String,
    val nama: String,
    val deskripsi: String,
    val harga: Long,
    val durasi: Long, // menit, 0 = tidak diisi
    val aktif: Boolean,
    val kategori: LayananKategori = LayananKategori.LAINNYA
)

class LayananAdapter(
    private val onEdit: (Layanan) -> Unit,
    private val onDelete: (Layanan) -> Unit
) : RecyclerView.Adapter<LayananAdapter.VH>() {

    private var data: List<Layanan> = emptyList()
    private val format = NumberFormat.getInstance(Locale("id", "ID"))

    fun submit(list: List<Layanan>) {
        data = list
        notifyDataSetChanged()
    }

    class VH(val b: ItemLayananBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemLayananBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val l = data[position]
        val ctx = holder.b.root.context

        holder.b.tvNama.text = l.nama

        holder.b.tvDeskripsi.text = l.deskripsi
        holder.b.tvDeskripsi.visibility = if (l.deskripsi.isBlank()) View.GONE else View.VISIBLE

        val durasi = if (l.durasi > 0) " • ${l.durasi} menit" else ""
        holder.b.tvHarga.text = "Rp ${format.format(l.harga)}$durasi • ${l.kategori.label}"

        holder.b.tvStatus.text = if (l.aktif) "Aktif" else "Nonaktif"
        holder.b.tvStatus.setTextColor(
            ContextCompat.getColor(ctx, if (l.aktif) R.color.bk_primary else R.color.bk_text_secondary)
        )

        holder.b.btnEdit.setOnClickListener { onEdit(l) }
        holder.b.btnHapus.setOnClickListener { onDelete(l) }
    }

    override fun getItemCount() = data.size
}