package lee.module.lottiesample

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import lee.module.lottieswiperefreshlayout.LottieSwipeRefreshLayout
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<RecyclerView>(R.id.rcvSimple).apply {
            val list = List(100) { Random.nextInt().toString() }
            adapter = object : RecyclerView.Adapter<BaseViewHolder>() {
                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
                    val view = TextView(parent.context).apply {
                        layoutParams = RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
                    }
                    return BaseViewHolder(view)
                }

                override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
                    (holder.itemView as TextView).text = list[position]
                }

                override fun getItemCount(): Int {
                    return list.size
                }
            }
        }

        findViewById<LottieSwipeRefreshLayout>(R.id.lottieSwipeRefresh).apply {
            setOnRefreshListener {
                lifecycleScope.launch {
                    delay(3000)
                    isRefreshing = false
                }
            }
        }
    }

    class BaseViewHolder(view: View) : RecyclerView.ViewHolder(view)
}