
package id.ac.stmi.mobileproject

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import id.ac.stmi.mobileproject.databinding.ActivityPromoCustomerBinding
import java.text.NumberFormat
import java.util.Locale

class PromoActivityCustomer : AppCompatActivity() {

    private lateinit var binding: ActivityPromoCustomerBinding

    private val auth by lazy {
        FirebaseAuth.getInstance()
    }

    private val db by lazy {
        FirebaseFirestore.getInstance()
    }

    private val idFormat =
        NumberFormat.getIntegerInstance(Locale("id", "ID"))

    private var filter: KategoriPromo = KategoriPromo.SERVIS
    private lateinit var promoAdapter: PetugasPromoAdapter

    private var semuaPromo: List<PromoItem> = emptyList()
    private var promoSudahDiklaim: Set<String> = emptySet()

    private var promoListener: PromoListener? = null
    private var voucherClaimListener: PromoListener? = null
    private var voucherJumlahListener: PromoListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPromoCustomerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTopBar(showNotificationDot = true)

        val uid = auth.currentUser?.uid
        if (uid == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        promoAdapter = PetugasPromoAdapter { promo ->
            startActivity(
                Intent(this, PromoDetailActivity::class.java)
                    .putExtra(
                        PromoDetailActivity.EXTRA_PROMO_ID,
                        promo.id
                    )
            )
        }

        siapkanDaftar()
        setupCategoryChips()
        setupBottomNav()
        setupKartuPoinVoucher()

        PromoStore.seedSekali(this)
        listenPoints(uid)
    }

    override fun onStart() {
        super.onStart()

        promoListener = PromoStore.listen(
            this,
            onChange = { promos ->
                semuaPromo = promos
                render()
            },
            onError = {
                toast("Gagal memuat promo")
            }
        )

        voucherClaimListener =
            VoucherStore.dengarkanPromoDiklaim(
                this,
                onChange = { ids ->
                    promoSudahDiklaim = ids
                    render()
                },
                onError = {
                    toast("Gagal memuat status klaim promo")
                }
            )

        voucherJumlahListener = VoucherStore.listen(
            this,
            onError = {
                toast("Gagal memuat jumlah voucher")
            }
        ) { daftarVoucher ->
            val jumlah =
                VoucherStore.jumlahTersedia(daftarVoucher)

            binding.tvVoucherSaya.text = jumlah.toString()
        }
    }

    override fun onStop() {
        super.onStop()

        promoListener?.remove()
        promoListener = null

        voucherClaimListener?.remove()
        voucherClaimListener = null

        voucherJumlahListener?.remove()
        voucherJumlahListener = null
    }

    private fun siapkanDaftar() {
        binding.rvPromo.adapter = promoAdapter
    }

    private fun render() {
        val now = System.currentTimeMillis()

        val aktif = semuaPromo.filter { promo ->
            promo.berakhir >= now &&
                    promo.id !in promoSudahDiklaim
        }

        val list = aktif.filter { promo ->
            promo.kategori == filter
        }

        promoAdapter.submitList(list)

        binding.tvPromoHeader.visibility = View.VISIBLE
        binding.scrollKategori.visibility = View.VISIBLE

        binding.rvPromo.visibility =
            if (list.isNotEmpty()) View.VISIBLE else View.GONE

        binding.tvKosong.visibility =
            if (list.isEmpty()) View.VISIBLE else View.GONE

        if (list.isEmpty()) {
            binding.tvKosong.text = when {
                semuaPromo.isEmpty() ->
                    "Belum ada promo tersedia."

                aktif.isEmpty() ->
                    "Semua promo sudah diklaim atau kedaluwarsa."

                else ->
                    "Belum ada promo untuk kategori ini."
            }
        }
    }

    private fun listenPoints(uid: String) {
        db.collection("users")
            .document(uid)
            .addSnapshotListener(this) { snap, error ->
                if (error != null) {
                    toast("Gagal memuat poin")
                    return@addSnapshotListener
                }

                val points = snap?.getLong("points") ?: 0L

                binding.tvPoinSaya.text =
                    idFormat.format(points)
            }
    }

    private fun setupCategoryChips() {
        val chips = listOf(
            binding.chipServis,
            binding.chipSparepart,
            binding.chipPoin
        )

        chips.forEach { chip ->
            chip.isCheckedIconVisible = false
            styleChip(chip, chip.isChecked)
        }

        binding.chipGroupKategori
            .setOnCheckedStateChangeListener { _, checkedIds ->

                val checkedId = checkedIds.firstOrNull()
                    ?: return@setOnCheckedStateChangeListener

                chips.forEach { chip ->
                    styleChip(
                        chip,
                        chip.id == checkedId
                    )
                }

                filter = when (checkedId) {
                    R.id.chipSparepart ->
                        KategoriPromo.SPAREPART

                    R.id.chipPoin ->
                        KategoriPromo.POIN

                    else ->
                        KategoriPromo.SERVIS
                }

                render()
            }
    }

    private fun styleChip(
        chip: Chip,
        selected: Boolean
    ) {
        val primary = ContextCompat.getColor(
            this,
            R.color.bk_primary
        )

        chip.chipBackgroundColor =
            ColorStateList.valueOf(
                if (selected) primary else Color.WHITE
            )

        chip.setTextColor(
            if (selected) Color.WHITE else primary
        )
    }

    private fun setupKartuPoinVoucher() {
        binding.cardPoin.setOnClickListener {
            startActivity(
                Intent(
                    this,
                    RiwayatPoinCustActivity::class.java
                )
            )
        }

        binding.cardVoucherSaya.setOnClickListener {
            startActivity(
                Intent(
                    this,
                    VoucherSayaActivity::class.java
                )
            )
        }
    }

    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = R.id.nav_promo

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(
                        Intent(
                            this,
                            HomeActivityCustomer::class.java
                        ).addFlags(
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                        )
                    )

                    overridePendingTransition(0, 0)
                    false
                }

                R.id.nav_promo -> true

                R.id.nav_bengkel -> {
                    toast("Peta bengkel")
                    false
                }

                R.id.nav_account -> {
                    startActivity(
                        Intent(
                            this,
                            AccountActivity::class.java
                        )
                    )
                    false
                }

                else -> false
            }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(
            this,
            msg,
            Toast.LENGTH_SHORT
        ).show()
    }
}
