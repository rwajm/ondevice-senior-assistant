package com.k2j3.seniorassistant.device

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.k2j3.seniorassistant.device.ui.navigation.AppNavDisplay
import com.k2j3.seniorassistant.device.ui.theme.SeniorAssistantTheme

class MainActivity : ComponentActivity() {
    // 말로 부탁하기에 마이크가 필요하다. 거절하면 듣지 않고(빈 녹음) 나머지 기능은 그대로 쓴다
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
        enableEdgeToEdge()
        // 보드 화면은 앱만 보이게: 상단·하단 시스템 막대 숨기기
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // 집에 두고 시계처럼 보는 기기라 앱이 떠 있는 동안 화면을 끄지 않는다
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            SeniorAssistantTheme {
                AppNavDisplay()
            }
        }
    }
}
