package id.ac.stmi.mobileproject

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import id.ac.stmi.mobileproject.databinding.ItemMenuGridBinding

data class MenuPetugas(
    val key: String,
    val label: String,
    val icon: Int
)

class MenuGridAdapter(
    private val onClick: (MenuPetugas) -> Unit
) : RecyclerView.Adapter<MenuGridAdapter.VH>() {

    private var data: List<MenuPetugas> = emptyList()

    fun submit(list: List<MenuPetugas>) {
        data = list
        notifyDataSetChanged()
    }

    class VH(val b: ItemMenuGridBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemMenuGridBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val m = data[position]
        holder.b.ivIcon.setImageResource(m.icon)
        holder.b.tvLabel.text = m.label
        holder.b.root.setOnClickListener { onClick(m) }
    }

    override fun getItemCount() = data.size
}