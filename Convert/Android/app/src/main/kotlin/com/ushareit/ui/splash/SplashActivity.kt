package com.ushareit.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.nextai.sharenow.databinding.ActivitySplashBinding
import com.ushareit.core.onboarding.OnboardingManager
import com.ushareit.ui.intro.IntroActivity
import com.ushareit.ui.main.MainActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : com.ushareit.ui.base.BaseActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            binding.root.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }

        setupLightStatusBar()
        startLogoAnimation()

        // Transition after 2.4 seconds
        handler.postDelayed({
            proceedNextScreen()
        }, 2400)
    }

    private fun setupLightStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
    }

    private fun startLogoAnimation() {
        val animSet = AnimationSet(true).apply {
            interpolator = AccelerateDecelerateInterpolator()
            duration = 1000
            addAnimation(AlphaAnimation(0.2f, 1.0f))
            addAnimation(ScaleAnimation(0.85f, 1.0f, 0.85f, 1.0f, ScaleAnimation.RELATIVE_TO_SELF, 0.5f, ScaleAnimation.RELATIVE_TO_SELF, 0.5f))
        }
        binding.layoutCenterLogo.startAnimation(animSet)
    }

    private fun proceedNextScreen() {
        if (isFinishing || isDestroyed) return

        val launchNext = {
            val intent = if (OnboardingManager.isOnboardingCompleted()) {
                Intent(this, MainActivity::class.java)
            } else {
                Intent(this, IntroActivity::class.java)
            }
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }

        if (com.ushareit.core.ads.AdMediationManager.isAppOpenReady()) {
            com.ushareit.core.ads.AdMediationManager.showAppOpenIfReady(this) {
                launchNext()
            }
        } else {
            launchNext()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
