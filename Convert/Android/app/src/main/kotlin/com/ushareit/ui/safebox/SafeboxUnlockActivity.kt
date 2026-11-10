package com.ushareit.ui.safebox

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.nextai.sharenow.R
import com.nextai.sharenow.databinding.ActivitySafeboxUnlockBinding
import com.ushareit.core.utils.ButtonLightSweep

class SafeboxUnlockActivity : com.ushareit.ui.base.BaseActivity() {

    private lateinit var binding: ActivitySafeboxUnlockBinding
    private val pinCode = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySafeboxUnlockBinding.inflate(layoutInflater)
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

        val keys = listOf(
            binding.key0 to "0",
            binding.key1 to "1",
            binding.key2 to "2",
            binding.key3 to "3",
            binding.key4 to "4",
            binding.key5 to "5",
            binding.key6 to "6",
            binding.key7 to "7",
            binding.key8 to "8",
            binding.key9 to "9"
        )

        keys.forEach { (view, digit) ->
            view.setOnClickListener {
                if (pinCode.length < 4) {
                    pinCode.append(digit)
                    updatePinDots()
                    if (pinCode.length == 4) {
                        unlockSafebox()
                    }
                }
            }
        }

        binding.keyDelete.setOnClickListener {
            if (pinCode.isNotEmpty()) {
                pinCode.deleteCharAt(pinCode.length - 1)
                updatePinDots()
            }
        }
    }

    private fun updatePinDots() {
        val dots = listOf(binding.dot1, binding.dot2, binding.dot3, binding.dot4)
        for (i in dots.indices) {
            if (i < pinCode.length) {
                dots[i].setBackgroundResource(R.drawable.bg_btn_send)
            } else {
                dots[i].setBackgroundResource(R.drawable.bg_circle_radar)
            }
        }
    }

    private fun unlockSafebox() {
        Toast.makeText(this, getString(R.string.safebox_unlock_success), Toast.LENGTH_SHORT).show()
        binding.layoutPinScreen.visibility = View.GONE
        binding.layoutVaultContent.visibility = View.VISIBLE

        ButtonLightSweep.attachTo(binding.btnAddVaultFile)

        binding.btnAddVaultFile.setOnClickListener {
            Toast.makeText(this, getString(R.string.safebox_select_file_prompt), Toast.LENGTH_SHORT).show()
            startActivity(android.content.Intent(this, com.ushareit.ui.transfer.SendActivity::class.java))
        }

        binding.btnLockVault.setOnClickListener {
            pinCode.clear()
            updatePinDots()
            binding.layoutVaultContent.visibility = View.GONE
            binding.layoutPinScreen.visibility = View.VISIBLE
            Toast.makeText(this, getString(R.string.safebox_locked_toast), Toast.LENGTH_SHORT).show()
        }
    }
}
