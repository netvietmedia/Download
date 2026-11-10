package com.ushareit.ui.coin

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.nextai.sharenow.R
import com.nextai.sharenow.databinding.ActivityCoinStoreBinding

class CoinStoreActivity : com.ushareit.ui.base.BaseActivity() {

    private lateinit var binding: ActivityCoinStoreBinding
    private var coins = 1250

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCoinStoreBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            binding.root.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }

        initViews()
    }

    private fun initViews() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        com.ushareit.core.ads.AdMediationManager.loadRewarded(this)

        binding.btnClaimDaily.setOnClickListener {
            com.ushareit.core.ads.AdMediationManager.showRewardedIfReady(
                activity = this,
                onUserEarnedReward = { _, _ ->
                    coins += 50
                    binding.tvCoinTotal.text = getString(R.string.coin_balance_format, coins.toString())
                    binding.btnClaimDaily.isEnabled = false
                    binding.btnClaimDaily.text = "✓ (+50)"
                    Toast.makeText(this, "+50 Coins!", Toast.LENGTH_SHORT).show()
                },
                onAdClosed = {
                    if (binding.btnClaimDaily.isEnabled) {
                        coins += 50
                        binding.tvCoinTotal.text = getString(R.string.coin_balance_format, coins.toString())
                        binding.btnClaimDaily.isEnabled = false
                        binding.btnClaimDaily.text = "✓ (+50)"
                        Toast.makeText(this, "+50 Coins!", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}
