package com.k2j3.seniorassistant.device.ui.document

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.k2j3.seniorassistant.device.ui.theme.AppColor
import com.k2j3.seniorassistant.device.ui.theme.AppIcons
import com.k2j3.seniorassistant.device.ui.theme.AppScreen
import com.k2j3.seniorassistant.device.ui.theme.AppType
import com.k2j3.seniorassistant.device.ui.theme.PillButton
import com.mikimn.droiduvc.StreamFormat
import com.mikimn.droiduvc.UvcCamera
import com.mikimn.droiduvc.uvcDevices
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * USB UVC 웹캠 미리보기와 문서 사진 촬영.
 *
 * Android Camera2 provider를 거치지 않고 UsbManager의 파일 기술자로 C920 같은 UVC 카메라를 직접 연다.
 * 따라서 보드 펌웨어가 외부 카메라를 CameraX 장치로 등록하지 않아도 동작한다.
 */
@Composable
internal fun CameraStep(actions: DocumentActions) {
    var takePhoto by remember { mutableStateOf<(() -> Unit)?>(null) }
    var requestUsbPermission by remember { mutableStateOf<(() -> Unit)?>(null) }

    AppScreen(title = "문서 촬영", onBack = actions.onBack) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            UvcDocumentPreview(
                modifier = Modifier.fillMaxSize(),
                onReady = { takePhoto = it; requestUsbPermission = null },
                onPermissionNeeded = { requestUsbPermission = it },
                onCaptured = actions.onImage,
                onError = actions.onCameraFailed,
            )
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                when {
                    takePhoto != null -> PillButton(
                        "사진 찍기", { takePhoto?.invoke() }, leading = AppIcons.Camera,
                        container = AppColor.Green, border = AppColor.Green, content = Color.White,
                    )
                    requestUsbPermission != null -> PillButton(
                        "웹캠 사용 허용", { requestUsbPermission?.invoke() }, leading = AppIcons.Camera,
                        container = AppColor.Green, border = AppColor.Green, content = Color.White,
                    )
                    else -> {
                        CircularProgressIndicator(Modifier.size(48.dp), color = Color.White)
                        Text("웹캠을 준비하고 있어요", style = AppType.Body, color = Color.White, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun UvcDocumentPreview(
    modifier: Modifier,
    onReady: ((() -> Unit) -> Unit),
    onPermissionNeeded: ((() -> Unit) -> Unit),
    onCaptured: (ByteArray) -> Unit,
    onError: () -> Unit,
) {
    val context = LocalContext.current
    val usb = remember(context) { context.getSystemService(UsbManager::class.java) }
    val scope = rememberCoroutineScope()
    val texture = remember(context) { TextureView(context) }
    var surface: Surface? by remember { mutableStateOf(null) }
    var session: UvcSession? by remember { mutableStateOf(null) }
    var opening by remember { mutableStateOf(false) }
    val device = remember(usb) { usb.uvcDevices().firstOrNull() }

    fun closeSession() {
        session?.close()
        session = null
    }

    fun capture() {
        val bitmap = texture.bitmap ?: return onError()
        val jpeg = ByteArrayOutputStream().use { output ->
            if (bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) output.toByteArray() else ByteArray(0)
        }
        bitmap.recycle()
        if (jpeg.isEmpty()) onError() else onCaptured(jpeg)
    }

    fun openCamera() {
        val target = device ?: return onError()
        val targetSurface = surface ?: return
        if (opening || session != null) return
        opening = true
        scope.launch {
            val opened = withContext(Dispatchers.IO) {
                runCatching {
                    val connection = checkNotNull(usb.openDevice(target)) { "Cannot open USB webcam" }
                    val camera = UvcCamera.open(connection.fileDescriptor)
                    // 이 보드에서는 이 스트림만 실제 프레임을 안정적으로 표시했다.
                    camera.start(targetSurface, PREVIEW_WIDTH, PREVIEW_HEIGHT, PREVIEW_FPS, StreamFormat.MJPEG)
                    UvcSession(connection, camera)
                }
            }
            opening = false
            opened.onSuccess {
                session = it
                onReady(::capture)
            }.onFailure {
                Log.w(TAG, "Cannot open UVC webcam", it)
                onError()
            }
        }
    }

    DisposableEffect(device, surface) {
        if (device == null) {
            onError()
            onDispose { }
        } else {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context, intent: Intent) {
                    if (intent.action != ACTION_USB_PERMISSION) return
                    val grantedDevice = intent.parcelableDevice()
                    if (grantedDevice != device) return
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) openCamera()
                    else onError()
                }
            }
            ContextCompat.registerReceiver(context, receiver, IntentFilter(ACTION_USB_PERMISSION), ContextCompat.RECEIVER_NOT_EXPORTED)
            if (usb.hasPermission(device)) openCamera()
            else onPermissionNeeded {
                val permission = PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                usb.requestPermission(device, permission)
            }
            onDispose {
                runCatching { context.unregisterReceiver(receiver) }
                closeSession()
            }
        }
    }

    AndroidView(
        factory = { texture.apply {
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surfaceTexture: android.graphics.SurfaceTexture, width: Int, height: Int) {
                    surface = Surface(surfaceTexture)
                }

                override fun onSurfaceTextureSizeChanged(surfaceTexture: android.graphics.SurfaceTexture, width: Int, height: Int) = Unit

                override fun onSurfaceTextureDestroyed(surfaceTexture: android.graphics.SurfaceTexture): Boolean {
                    closeSession()
                    surface?.release()
                    surface = null
                    return true
                }

                override fun onSurfaceTextureUpdated(surfaceTexture: android.graphics.SurfaceTexture) = Unit
            }
        } },
        modifier = modifier,
    )
}

private fun Intent.parcelableDevice(): UsbDevice? =
    if (android.os.Build.VERSION.SDK_INT >= 33) getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
    else @Suppress("DEPRECATION") (getParcelableExtra(UsbManager.EXTRA_DEVICE) as? UsbDevice)

private class UvcSession(
    private val connection: android.hardware.usb.UsbDeviceConnection,
    private val camera: UvcCamera,
) : AutoCloseable {
    override fun close() {
        runCatching { camera.stop() }
        runCatching { camera.close() }
        connection.close()
    }
}

private const val ACTION_USB_PERMISSION = "com.k2j3.seniorassistant.device.USB_UVC_PERMISSION"
private const val PREVIEW_WIDTH = 640
private const val PREVIEW_HEIGHT = 480
private const val PREVIEW_FPS = 30
private const val JPEG_QUALITY = 90
private const val TAG = "DocumentCamera"
