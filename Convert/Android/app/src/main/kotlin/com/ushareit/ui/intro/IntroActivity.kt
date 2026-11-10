package com.ushareit.ui.intro

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.nextai.sharenow.R
import com.nextai.sharenow.databinding.ActivityIntroBinding
import com.ushareit.core.ads.AdMediationManager
import com.ushareit.core.locale.LocaleManager
import com.ushareit.core.onboarding.OnboardingManager
import com.ushareit.core.utils.ButtonLightSweep
import com.ushareit.ui.main.MainActivity

class IntroActivity : com.ushareit.ui.base.BaseActivity() {

    private lateinit var binding: ActivityIntroBinding
    private var currentStep = 0
    private lateinit var languageAdapter: IntroLanguageAdapter

    companion object {
        private const val EXTRA_INITIAL_STEP = "extra_initial_step"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIntroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            binding.root.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }

        setupLightStatusBar()
        currentStep = savedInstanceState?.getInt(EXTRA_INITIAL_STEP)
            ?: intent.getIntExtra(EXTRA_INITIAL_STEP, 0)

        initViews()
        updateStepUI(currentStep, animate = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(EXTRA_INITIAL_STEP, currentStep)
    }

    private fun setupLightStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
    }

    private fun initViews() {
        ButtonLightSweep.attachTo(binding.btnIntroAction)
        loadNativeAds()

        // Setup Language RecyclerView
        val savedLang = LocaleManager.getSavedLanguage()
        languageAdapter = IntroLanguageAdapter(
            selectedCode = savedLang
        ) { selectedLang ->
            // Apply language immediately
            LocaleManager.applyLanguage(this, selectedLang.code)
            // Save state and recreate to re-inflate with the new locale
            val restartIntent = Intent(this, IntroActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(EXTRA_INITIAL_STEP, 2)
            }
            startActivity(restartIntent)
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        binding.rvIntroLanguages.apply {
            layoutManager = LinearLayoutManager(this@IntroActivity)
            adapter = languageAdapter
            setHasFixedSize(true)
        }

        // Skip Button -> Go to Step 2 (Language Selection)
        binding.btnIntroSkip.setOnClickListener {
            if (currentStep < 2) {
                goToStep(2)
            } else {
                finishOnboarding()
            }
        }

        // Action Button -> Next step or Finish
        binding.btnIntroAction.setOnClickListener {
            if (currentStep < 2) {
                goToStep(currentStep + 1)
            } else {
                finishOnboarding()
            }
        }
    }

    private fun goToStep(step: Int) {
        if (step in 0..2 && step != currentStep) {
            currentStep = step
            updateStepUI(currentStep, animate = true)
        }
    }

    private fun loadNativeAds() {
        AdMediationManager.loadNativeAd(this, onLoaded = { nativeAd ->
            if (!isFinishing && !isDestroyed) {
                AdMediationManager.bindNativeAdToContainer(binding.layoutNativeAdIntroStep1, nativeAd)
            }
        })
        AdMediationManager.loadNativeAd(this, onLoaded = { nativeAd ->
            if (!isFinishing && !isDestroyed) {
                AdMediationManager.bindNativeAdToContainer(binding.layoutNativeAdIntroStep2, nativeAd)
            }
        })
        AdMediationManager.loadNativeAd(this, onLoaded = { nativeAd ->
            if (!isFinishing && !isDestroyed) {
                AdMediationManager.bindNativeAdToContainer(binding.layoutNativeAdIntroStep3, nativeAd)
            }
        })
    }

    private fun updateStepUI(step: Int, animate: Boolean) {
        binding.vfIntroSteps.displayedChild = step

        if (animate) {
            val inAnim = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
            binding.vfIntroSteps.currentView?.startAnimation(inAnim)
        }

        // Update Dots
        updateDots(step)

        // Update Skip & Action button text
        if (step == 2) {
            binding.btnIntroSkip.visibility = View.GONE
            binding.btnIntroAction.text = getString(R.string.intro_btn_get_started)
        } else {
            binding.btnIntroSkip.visibility = View.VISIBLE
            binding.btnIntroAction.text = getString(R.string.btn_next)
        }
    }

    private fun updateDots(step: Int) {
        val dp8 = (8 * resources.displayMetrics.density).toInt()
        val dp24 = (24 * resources.displayMetrics.density).toInt()

        setDotState(binding.dot1, step == 0, dp8, dp24)
        setDotState(binding.dot2, step == 1, dp8, dp24)
        setDotState(binding.dot3, step == 2, dp8, dp24)
    }

    private fun setDotState(view: View, isActive: Boolean, dp8: Int, dp24: Int) {
        val params = view.layoutParams
        if (isActive) {
            view.setBackgroundResource(R.drawable.bg_indicator_dot_active)
            params.width = dp24
            params.height = dp8
        } else {
            view.setBackgroundResource(R.drawable.bg_indicator_dot_inactive)
            params.width = dp8
            params.height = dp8
        }
        view.layoutParams = params
    }

    private fun finishOnboarding() {
        OnboardingManager.setOnboardingCompleted(true)
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (currentStep > 0) {
            goToStep(currentStep - 1)
        } else {
            super.onBackPressed()
        }
    }
}
