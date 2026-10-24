package id.ac.stmi.mobileproject

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
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
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val idFormat = NumberFormat.getIntegerInstance(Locale("id", "ID"))

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

        setupCategoryChips()
        setupPromoClicks()
        setupBottomNav()

        listenPoints(uid)
        listenVouchers(uid)
    }

    // ============ DATA ============

    /** Poin loyalti dari users/{uid}.points (realtime) */
    private fun listenPoints(uid: String) {
        db.collection("users").document(uid)
            .addSnapshotListener(this) { snap, error ->
                if (error != null) {
                    toast("Gagal memuat poin")
                    return@addSnapshotListener
                }
                binding.tvPoinSaya.text = idFormat.format(snap?.getLong("points") ?: 0L)
            }
    }

    /** Voucher tersedia = dokumen di users/{uid}/vouchers yang "used"-nya bukan true */
    private fun listenVouchers(uid: String) {
        db.collection("users").document(uid).collection("vouchers")
            .addSnapshotListener(this) { snap, error ->
                if (error != null) {
                    toast("Gagal memuat voucher")
                    return@addSnapshotListener
                }
                val available = snap?.documents.orEmpty()
                    .count { it.getBoolean("used") != true }
                binding.tvVoucherSaya.text = available.toString()
            }
    }

    // ============ CHIP KATEGORI ============

    private fun setupCategoryChips() {
        val chips = listOf(binding.chipServis, binding.chipSparepart, binding.chipPoin)

        chips.forEach {
            it.isCheckedIconVisible = false
            styleChip(it, it.isChecked)
        }

        binding.chipGroupKategori.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull()
                ?: return@setOnCheckedStateChangeListener
            chips.forEach { styleChip(it, it.id == checkedId) }
        }
    }

    private fun styleChip(chip: Chip, selected: Boolean) {
        val primary = ContextCompat.getColor(this, R.color.bk_primary)
        chip.chipBackgroundColor =
            ColorStateList.valueOf(if (selected) primary else Color.WHITE)
        chip.setTextColor(if (selected) Color.WHITE else primary)
    }

    // ============ CLICK ============

    private fun setupPromoClicks() {
        val cards = listOf(
            binding.cardPromo1, binding.cardPromo2,
            binding.cardPromo3, binding.cardPromo4
        )
        val buttons = listOf(
            binding.btnLihatDetail1, binding.btnLihatDetail2,
            binding.btnLihatDetail3, binding.btnLihatDetail4
        )
        for (i in cards.indices) {
            val openDetail = {
                startActivity(
                    Intent(this, PromoDetailActivity::class.java)
                        .putExtra(PromoDetailActivity.EXTRA_PROMO_INDEX, i)
                )
            }
            cards[i].setOnClickListener { openDetail() }
            buttons[i].setOnClickListener { openDetail() }
        }
    }

    // ============ BOTTOM NAV ============

    private fun setupBottomNav() {
        binding.bottomNav.selectedItemId = R.id.nav_promo // sebelum listener

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(
                        Intent(this, HomeActivityCustomer::class.java)
                            .addFlags(
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                            )
                    )
                    overridePendingTransition(0, 0)
                    false
                }
                R.id.nav_promo -> true
                R.id.nav_bengkel -> { toast("Peta bengkel"); false }
                R.id.nav_account -> { toast("Akun"); false }
                else -> false
            }
        }
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}