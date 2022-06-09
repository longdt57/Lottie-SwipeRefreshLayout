package lee.module.lottieswiperefreshlayout.custom

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import lee.module.lottieswiperefreshlayout.R

class LottieSwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : SimpleSwipeRefreshLayout(context, attrs, defStyle) {

    private var animationFile: Int = -1
    private val lottieAnimationView by lazy {
        LottieAnimationView(context).apply {
            if (animationFile == -1) {
                animationFile = R.raw.lottie_swipe_refresh_default
            }

            setAnimation(animationFile)
            repeatCount = LottieDrawable.INFINITE
            val size = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                80f,
                context.resources.displayMetrics
            ).toInt()

            layoutParams = LayoutParams(ViewGroup.LayoutParams(size, size))

            val density = context.resources.displayMetrics.density
            ViewCompat.setElevation(this, ELEVATION * density)
            speed = 2.0f
        }
    }

    init {
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.LottieSwipeRefreshLayout,
            defStyle,
            0
        ).let { style ->
            animationFile =
                style.getResourceId(R.styleable.LottieSwipeRefreshLayout_lottie_rawRes, -1)
            addView(lottieAnimationView)
            style.recycle()
        }

        addProgressListener {
            lottieAnimationView.progress = it
        }

        addTriggerListener {
            lottieAnimationView.resumeAnimation()
        }
    }

    override fun stopRefreshing() {
        super.stopRefreshing()
        lottieAnimationView.pauseAnimation()
    }

    override fun startRefreshing() {
        super.startRefreshing()
        lottieAnimationView.resumeAnimation()
    }
}
