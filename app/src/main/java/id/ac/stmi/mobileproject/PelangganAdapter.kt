package id.ac.stmi.mobileproject

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import id.ac.stmi.mobileproject.databinding.ItemPelangganBinding

data class Pelanggan(
    val uid: String,
    val nama: String,
    val email: String,
    val noHp: String
)

class PelangganAdapter : RecyclerView.Adapter<PelangganAdapter.VH>() {

    private var data: List<Pelanggan> = emptyList()

    fun submit(list: List<Pelanggan>) {
        data = list
        notifyDataSetChanged()
    }

    class VH(val b: ItemPelangganBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemPelangganBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = data[position]
        holder.b.tvInisial.text = p.nama.firstOrNull()?.uppercase() ?: "?"
        holder.b.tvNama.text = p.nama
        holder.b.tvEmail.text = p.email
        holder.b.tvNoHp.text = p.noHp
    }

    override fun getItemCount() = data.size
}
