package com.ushareit.core.utils

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import java.util.Collections
import java.util.WeakHashMap

/**
 * Hiệu ứng ánh sáng nhẹ quét chéo (Diagonal Light Sweep / Shimmer) trên Button
 */
class LightSweepDrawable(private val cornerRadiusPx: Float = 0f) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipPath = android.graphics.Path()
    private val rectF = android.graphics.RectF()
    private var sweepOffset = -1.5f
    private var animator: ValueAnimator? = null

    init {
        paint.style = Paint.Style.FILL
    }

    fun startAnimation(hostView: View) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(-1.2f, 2.2f).apply {
            duration = 2400
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = AccelerateDecelerateInterpolator()
            startDelay = 400
            addUpdateListener { va ->
                sweepOffset = va.animatedValue as Float
                hostView.invalidate()
            }
            start()
        }
    }

    fun triggerSingleSweep(hostView: View, sweepDuration: Long = 1200L, onComplete: () -> Unit) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(-1.2f, 2.2f).apply {
            duration = sweepDuration
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { va ->
                sweepOffset = va.animatedValue as Float
                hostView.invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    sweepOffset = -1.5f
                    hostView.invalidate()
                    onComplete()
                }
            })
            start()
        }
    }

    fun stopAnimation() {
        animator?.cancel()
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return

        val width = b.width().toFloat()
        val height = b.height().toFloat()

        rectF.set(0f, 0f, width, height)
        // Bán kính bo góc tương ứng với nút để tia sáng không bao giờ tràn ra góc
        val density = hostViewDensity.coerceAtLeast(1.5f)
        val radius = if (cornerRadiusPx > 0f) cornerRadiusPx else (height * 0.5f).coerceAtMost(20f * density)

        val saveCount = canvas.save()
        clipPath.reset()
        clipPath.addRoundRect(rectF, radius, radius, android.graphics.Path.Direction.CW)
        canvas.clipPath(clipPath)

        // Tia sáng chạy chéo góc 45 độ, giới hạn chặt chẽ bên trong
        val beamWidth = width * 0.45f
        val currentX = sweepOffset * width

        val gradient = LinearGradient(
            currentX, 0f,
            currentX + beamWidth, height,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(20, 255, 255, 255),
                Color.argb(90, 255, 255, 255),
                Color.argb(20, 255, 255, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.0f, 0.35f, 0.5f, 0.65f, 1.0f),
            Shader.TileMode.CLAMP
        )

        paint.shader = gradient
        canvas.drawRoundRect(rectF, radius, radius, paint)
        canvas.restoreToCount(saveCount)
    }

    private var hostViewDensity: Float = 2.0f
    fun setDensity(d: Float) { hostViewDensity = d }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

object ButtonLightSweep {
    private val attachedViews = Collections.newSetFromMap(WeakHashMap<View, Boolean>())

    fun attachTo(view: View) {
        // Luôn bật clipToOutline và clipChildren để hiệu ứng tuyệt đối không tràn ra ngoài viền nút
        view.clipToOutline = true
        if (view is android.view.ViewGroup) {
            view.clipChildren = true
        }

        if (attachedViews.contains(view)) return
        val applySweep = {
            if (!attachedViews.contains(view) && view.width > 0 && view.height > 0) {
                attachedViews.add(view)
                val sweepDrawable = LightSweepDrawable()
                sweepDrawable.setDensity(view.resources.displayMetrics.density)
                sweepDrawable.setBounds(0, 0, view.width, view.height)
                view.overlay.add(sweepDrawable)
                sweepDrawable.startAnimation(view)
            }
        }
        if (view.isLaidOut && view.width > 0) {
            applySweep()
        } else {
            view.post { applySweep() }
        }
    }

    /**
     * Chạy hiệu ứng ánh sáng tuần tự: Quét qua SEND xong, tiếp đó mới đến RECEIVE -> tạo cảm giác lần lượt
     */
    fun attachAlternating(
        viewSend: View,
        viewReceive: View,
        sweepDuration: Long = 1200L,
        pauseBetween: Long = 250L,
        pauseCycle: Long = 1000L
    ) {
        viewSend.clipToOutline = true
        if (viewSend is android.view.ViewGroup) viewSend.clipChildren = true

        viewReceive.clipToOutline = true
        if (viewReceive is android.view.ViewGroup) viewReceive.clipChildren = true

        var sweepSend: LightSweepDrawable? = null
        var sweepReceive: LightSweepDrawable? = null

        val startCycle = {
            if (sweepSend == null && viewSend.width > 0 && viewSend.height > 0) {
                val d = LightSweepDrawable()
                d.setDensity(viewSend.resources.displayMetrics.density)
                d.setBounds(0, 0, viewSend.width, viewSend.height)
                viewSend.overlay.add(d)
                sweepSend = d
            }
            if (sweepReceive == null && viewReceive.width > 0 && viewReceive.height > 0) {
                val d = LightSweepDrawable()
                d.setDensity(viewReceive.resources.displayMetrics.density)
                d.setBounds(0, 0, viewReceive.width, viewReceive.height)
                viewReceive.overlay.add(d)
                sweepReceive = d
            }

            fun runCycle() {
                val s = sweepSend
                val r = sweepReceive
                if (s == null || r == null) return
                if (!viewSend.isAttachedToWindow || !viewReceive.isAttachedToWindow) return

                // Bước 1: Ánh sáng quét chéo qua card SEND
                s.triggerSingleSweep(viewSend, sweepDuration) {
                    // Bước 2: SEND hoàn tất -> Chờ 250ms -> Quét tiếp qua card RECEIVE
                    viewReceive.postDelayed({
                        if (!viewReceive.isAttachedToWindow) return@postDelayed
                        r.triggerSingleSweep(viewReceive, sweepDuration) {
                            // Bước 3: RECEIVE hoàn tất -> Nghỉ 1000ms -> Lặp lại chu trình từ SEND
                            viewSend.postDelayed({
                                if (viewSend.isAttachedToWindow && viewReceive.isAttachedToWindow) {
                                    runCycle()
                                }
                            }, pauseCycle)
                        }
                    }, pauseBetween)
                }
            }

            runCycle()
        }

        viewSend.post {
            viewReceive.post {
                startCycle()
            }
        }
    }
}
