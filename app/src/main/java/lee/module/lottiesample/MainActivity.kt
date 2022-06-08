package lee.module.lottiesample

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lee.module.lottieswiperefreshlayout.LottieSwipeRefreshLayout

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<LottieSwipeRefreshLayout>(R.id.lottieSwipeRefresh).apply {

            setOnRefreshListener {
                lifecycleScope.launch {
                    delay(3000)
                    isRefreshing = false
                }
            }
        }
    }
}