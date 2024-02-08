package com.shohrab.agoraCall

import android.Manifest
import android.app.Activity
import android.content.ContentValues.TAG
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.agora.rtc2.Constants.RENDER_MODE_FIT
import io.agora.rtc2.Constants.RENDER_MODE_HIDDEN
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.video.VideoCanvas


const val APP_ID = "14d46377a63243d9b4ce8e82783afe9b"

val token =
    "007eJxTYLi+u121bC7/K17/9QGu6V9/B5if6zugPPl3wOXtszrVAk0VGAxNUkzMjM3NE82MjUyMUyyTTJJTLVItjMwtjBPTUi2TRKYdSW0IZGQw2szAwAiFID4LQ0pqbj4DAwBibB8q"
private const val PERMISSION_REQ_ID = 22

// Ask for Android device permissions at runtime.
private val REQUESTED_PERMISSIONS = arrayOf<String>(
    Manifest.permission.BLUETOOTH_CONNECT,
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.CAMERA,
    Manifest.permission.WRITE_EXTERNAL_STORAGE
)
private val permissions = arrayOf(
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.CAMERA,
    Manifest.permission.BLUETOOTH_CONNECT,
)

class VideoActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Turn off the decor fitting system windows, which allows us to handle insets,
        // including IME animations
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val channelName = intent.getStringExtra("ChannelName")
        val userRole = intent.getStringExtra("UserRole")

        setContent {
            Scaffold {
                UIRequirePermissions(
                    modifier = Modifier.padding(it),
                    permissions = permissions,
                    onPermissionGranted = {
                        if (channelName != null && userRole != null) {
                            VideoCallScreen(channelName = channelName, userRole = userRole)
                        }
                    },
                    onPermissionDenied = {
                        AlertScreen(it)
                    }
                )
            }
        }
    }
}

@Composable
private fun VideoCallScreen(channelName: String, userRole: String) {
    val context = LocalContext.current
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current


    val localSurfaceView: SurfaceView? by remember {
        mutableStateOf( SurfaceView(context))
    }

    var remoteUserMap by remember {
        mutableStateOf(mapOf<Int, SurfaceView?>())
    }

    val mEngine = remember {
        initVideoEngine(context, object : IRtcEngineEventHandler() {
            override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
                Log.d(TAG, "channel:$channel,uid:$uid,elapsed:$elapsed")
            }

            override fun onUserJoined(uid: Int, elapsed: Int) {
                Log.d(TAG, "onUserJoined:$uid")
                val desiredUserList = remoteUserMap.toMutableMap()
                desiredUserList[uid] = null
                remoteUserMap = desiredUserList.toMap()
            }

            override fun onUserOffline(uid: Int, reason: Int) {
                Log.d(TAG, "onUserOffline:$uid")
                val desiredUserList = remoteUserMap.toMutableMap()
                desiredUserList.remove(uid)
                remoteUserMap = desiredUserList.toMap()
            }


        }, channelName, userRole)
    }
    // If `lifecycleOwner` changes, dispose and reset the effect
    DisposableEffect(lifecycleOwner) {
        // Create an observer that triggers our remembered callbacks
        // for sending analytics events
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                // currentOnStart()
            } else if (event == Lifecycle.Event.ON_STOP) {
                mEngine.leaveChannel();
            }
        }

        // Add the observer to the lifecycle
        lifecycleOwner.lifecycle.addObserver(observer)

        // When the effect leaves the Composition, remove the observer
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    if (userRole == "Broadcaster") {
        mEngine.setupLocalVideo(VideoCanvas(localSurfaceView, RENDER_MODE_FIT, 0))
    }

    Box(Modifier.fillMaxSize()) {
        localSurfaceView?.let { local ->
            AndroidView(factory = { local }, Modifier.fillMaxSize())
        }
        RemoteView(remoteListInfo = remoteUserMap, mEngine = mEngine)
        UserControls(mEngine = mEngine)
    }

}

@Composable
private fun RemoteView(remoteListInfo: Map<Int, SurfaceView?>, mEngine: RtcEngine) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(fraction = 0.2f)
            .horizontalScroll(state = rememberScrollState())
    ) {
        remoteListInfo.forEach { entry ->
            // Create a new SurfaceView
            val remoteSurfaceView = SurfaceView(context).takeIf { entry.value == null } ?: entry.value
            remoteSurfaceView?.setZOrderMediaOverlay(true)
            // Create a VideoCanvas using the remoteSurfaceView


           // val remoteTextureView = RtcEngine.CreateTextureView(context).takeIf { entry.value == null } ?: entry.value

            AndroidView(
                factory = { remoteSurfaceView!! },
                modifier = Modifier.size(Dp(180f), Dp(240f))
            )
            val videoCanvas = VideoCanvas(
                remoteSurfaceView,
                RENDER_MODE_HIDDEN, entry.key
            )
            mEngine.setupRemoteVideo(
                videoCanvas
            )
        }
    }
}

fun initVideoEngine(
    current: Context,
    eventHandler: IRtcEngineEventHandler,
    channelName: String,
    userRole: String
): RtcEngine =
    RtcEngine.create(current, APP_ID, eventHandler).apply {
        enableVideo()
        setChannelProfile(1)
        if (userRole == "Broadcaster") {
            setClientRole(1)
        } else {
            setClientRole(0)
        }
        joinChannel(token, channelName, "", 0)
    }

@Composable
private fun UserControls(mEngine: RtcEngine) {
    var muted by remember { mutableStateOf(false) }
    var videoDisabled by remember { mutableStateOf(false) }
    val activity = (LocalContext.current as? Activity)

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 50.dp),
        Arrangement.SpaceEvenly,
        Alignment.Bottom
    ) {
        OutlinedButton(
            onClick = {
                muted = !muted
                mEngine.muteLocalAudioStream(muted)
            },
            shape = CircleShape,
            modifier = Modifier.size(50.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (muted) Color.Blue else Color.White)
        ) {
            if (muted) {
                Icon(
                    Icons.Rounded.MicOff,
                    contentDescription = "Tap to unmute mic",
                    tint = Color.White
                )
            } else {
                Icon(Icons.Rounded.Mic, contentDescription = "Tap to mute mic", tint = Color.Blue)
            }
        }
        OutlinedButton(
            onClick = {
                mEngine.leaveChannel()
                activity?.finish()
            },
            shape = CircleShape,
            modifier = Modifier.size(70.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Red)
        ) {
            Icon(
                Icons.Rounded.CallEnd,
                contentDescription = "Tap to disconnect Call",
                tint = Color.White
            )

        }
        OutlinedButton(
            onClick = {
                videoDisabled = !videoDisabled
                mEngine.muteLocalVideoStream(videoDisabled)
            },
            shape = CircleShape,
            modifier = Modifier.size(50.dp),
            contentPadding = PaddingValues(0.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (videoDisabled) Color.Blue else Color.White)
        ) {
            if (videoDisabled) {
                Icon(
                    Icons.Rounded.VideocamOff,
                    contentDescription = "Tap to enable Video",
                    tint = Color.White
                )
            } else {
                Icon(
                    Icons.Rounded.Videocam,
                    contentDescription = "Tap to disable Video",
                    tint = Color.Blue
                )
            }
        }
    }
}

@Composable
private fun AlertScreen(requester: () -> Unit) {
    val context = LocalContext.current

    Log.d(TAG, "AlertScreen: ")
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Red),
        contentAlignment = Alignment.Center
    ) {
        Button(onClick = {
            requestPermissions(
                context as Activity,
                permissions,
                22
            )
            requester()
        }) {
            Icon(Icons.Rounded.Warning, "Permission Required")
            Text(text = "Permission Required")
        }
    }
}

/**
 * Helper Function for Permission Check
 */
@Composable
private fun UIRequirePermissions(
    modifier: Modifier = Modifier,
    permissions: Array<String>,
    onPermissionGranted: @Composable () -> Unit,
    onPermissionDenied: @Composable (requester: () -> Unit) -> Unit
) {
    Log.d(TAG, "UIRequirePermissions: ")
    val context = LocalContext.current

    var grantState by remember {
        mutableStateOf(permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }

    if (grantState) onPermissionGranted()
    else {
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
            onResult = {
                grantState = !it.containsValue(false)
            }
        )
        onPermissionDenied {
            Log.d(TAG, "launcher.launch")
            launcher.launch(permissions)
        }
    }
}