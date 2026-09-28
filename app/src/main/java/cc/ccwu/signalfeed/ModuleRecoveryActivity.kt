package cc.ccwu.signalfeed

import android.app.Activity
import android.app.ActivityManager
import android.os.Bundle
import android.content.Intent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class ModuleRecoveryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 80, 48, 48) }
        layout.addView(TextView(this).apply { text = "模块恢复\n此入口不加载任何模块。停用后保留订阅和文章数据。"; textSize = 20f })
        layout.addView(Button(this).apply {
            text = "停用全部运行时模块并打开应用"
            setOnClickListener {
                getSharedPreferences("reading", MODE_PRIVATE).edit().putBoolean("runtime_modules", false).commit()
                val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                manager.runningAppProcesses.orEmpty().filter { it.processName == packageName && it.uid == android.os.Process.myUid() }.forEach { android.os.Process.killProcess(it.pid) }
                startActivity(Intent(this@ModuleRecoveryActivity, MainActivity::class.java).putExtra("safeMode", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            }
        })
        setContentView(layout)
    }
}
