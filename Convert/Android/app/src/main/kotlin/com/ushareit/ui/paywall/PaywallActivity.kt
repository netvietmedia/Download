package com.ushareit.ui.paywall

import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.nextai.sharenow.R
import com.nextai.sharenow.databinding.ActivityPaywallBinding
import com.ushareit.core.subscription.BillingManager

class PaywallActivity : com.ushareit.ui.base.BaseActivity() {

    private lateinit var binding: ActivityPaywallBinding

    enum class PlanType { LIFETIME, MONTHLY, WEEKLY }
    private var selectedPlan = PlanType.LIFETIME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaywallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            binding.root.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }

        // Khởi tạo kết nối Google Play Billing v9
        BillingManager.initBilling(this)

        initViews()
    }

    private fun initViews() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Tương tác chuyển đổi giữa 3 gói đăng ký: Lifetime ($99), Monthly ($49), Weekly ($9)
        binding.cardPlanLifetime.setOnClickListener {
            selectPlan(PlanType.LIFETIME)
        }

        binding.cardPlanMonthly.setOnClickListener {
            selectPlan(PlanType.MONTHLY)
        }

        binding.cardPlanWeekly.setOnClickListener {
            selectPlan(PlanType.WEEKLY)
        }

        // Gắn hiệu ứng ánh sáng quét ngang nút VIP
        com.ushareit.core.utils.ButtonLightSweep.attachTo(binding.btnActionUpgrade)

        // Nút mua hàng / Đăng ký qua Google Play Billing
        binding.btnActionUpgrade.setOnClickListener {
            val (productId, planId) = when (selectedPlan) {
                PlanType.LIFETIME -> Pair(BillingManager.PRODUCT_LIFETIME, BillingManager.PLAN_LIFETIME)
                PlanType.MONTHLY -> Pair(BillingManager.PRODUCT_MONTHLY, BillingManager.PLAN_MONTHLY)
                PlanType.WEEKLY -> Pair(BillingManager.PRODUCT_WEEKLY, BillingManager.PLAN_WEEKLY)
            }

            BillingManager.launchPurchaseFlow(this, productId, planId) {
                finish()
            }
        }

        // Ban đầu chọn Lifetime VIP ($99)
        selectPlan(PlanType.LIFETIME)
    }

    private fun selectPlan(plan: PlanType) {
        selectedPlan = plan
        val primaryColor = androidx.core.content.ContextCompat.getColor(this, R.color.primary)
        val whiteTextColor = androidx.core.content.ContextCompat.getColor(this, R.color.text_white)

        // Reset all 3 cards
        binding.cardPlanLifetime.setBackgroundResource(if (plan == PlanType.LIFETIME) R.drawable.bg_plan_selected else R.drawable.bg_plan_unselected)
        binding.rbPlanLifetime.isChecked = (plan == PlanType.LIFETIME)
        binding.tvTitleLifetime.setTextColor(if (plan == PlanType.LIFETIME) primaryColor else whiteTextColor)
        binding.tvPriceLifetime.setTextColor(if (plan == PlanType.LIFETIME) primaryColor else whiteTextColor)

        binding.cardPlanMonthly.setBackgroundResource(if (plan == PlanType.MONTHLY) R.drawable.bg_plan_selected else R.drawable.bg_plan_unselected)
        binding.rbPlanMonthly.isChecked = (plan == PlanType.MONTHLY)
        binding.tvTitleMonthly.setTextColor(if (plan == PlanType.MONTHLY) primaryColor else whiteTextColor)
        binding.tvPriceMonthly.setTextColor(if (plan == PlanType.MONTHLY) primaryColor else whiteTextColor)

        binding.cardPlanWeekly.setBackgroundResource(if (plan == PlanType.WEEKLY) R.drawable.bg_plan_selected else R.drawable.bg_plan_unselected)
        binding.rbPlanWeekly.isChecked = (plan == PlanType.WEEKLY)
        binding.tvTitleWeekly.setTextColor(if (plan == PlanType.WEEKLY) primaryColor else whiteTextColor)
        binding.tvPriceWeekly.setTextColor(if (plan == PlanType.WEEKLY) primaryColor else whiteTextColor)
    }
}
