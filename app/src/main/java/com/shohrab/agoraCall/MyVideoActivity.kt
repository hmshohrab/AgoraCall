package com.shohrab.agoraCall

import android.Manifest
import android.app.Activity
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.agora.rtc2.Constants
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.video.VideoCanvas


private const val PERMISSION_REQ_ID = 22

private val permissions = arrayOf(
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.CAMERA,
    Manifest.permission.BLUETOOTH_CONNECT,
)

class MyVideoActivity : ComponentActivity() {
    protected lateinit var agoraManager: AgoraManager
    protected var forceShowRemoteViews = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val channelName = intent.getStringExtra("ChannelName")
        val userRole = intent.getStringExtra("UserRole")

        setContent {
            Scaffold {
                UIRequirePermissions(
                    modifier = Modifier.padding(it),
                    permissions = permissions,
                    onPermissionGranted = {
                        if (channelName != null && userRole != null) {
                            MyVideoCallScreen(channelName = channelName, userRole = userRole)
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
private fun MyVideoCallScreen(channelName: String, userRole: String) {
    val context = LocalContext.current
    val lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current

    var myUid: Int by remember { mutableIntStateOf(0) }
    var localSurfaceView: SurfaceView by remember {
        mutableStateOf(SurfaceView(context))
    }

    var remoteUserMap by remember {
        mutableStateOf(mapOf<Int, SurfaceView?>())
    }


    val rawVideoAudioManager = remember {
        RawVideoAudioManager(context).apply {
            setListener(object : AgoraManager.AgoraManagerListener {
                override fun onMessageReceived(message: String?) {
                    //  showMessage(message)
                }

                override fun onRemoteUserJoined(remoteUid: Int, surfaceView: SurfaceView?) {
                    Log.d(TAG, "onUserJoined:$remoteUserMap")
                    val desiredUserList = remoteUserMap.toMutableMap()
                    desiredUserList[remoteUid] = surfaceView
                    remoteUserMap = desiredUserList.toMap()
                    // showRemoteVideo(remoteUid, surfaceView)
                }

                override fun onRemoteUserLeft(remoteUid: Int) {
                    val desiredUserList = remoteUserMap.toMutableMap()
                    desiredUserList.remove(remoteUid)
                    remoteUserMap = desiredUserList.toMap()
                }

                override fun onJoinChannelSuccess(channel: String?, uid: Int, elapsed: Int) {
                    myUid = uid
                }

                override fun onEngineEvent(eventName: String, eventArgs: Map<String, Any>) {
                    //    handleEngineEvent(eventName, eventArgs)
                }
            })
            joinChannelWithToken()
            localSurfaceView = localVideo

        }
    }
    DisposableEffect(lifecycleOwner) {
        // Create an observer that triggers our remembered callbacks
        // for sending analytics events
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                // currentOnStart()
            } else if (event == Lifecycle.Event.ON_STOP) {
                rawVideoAudioManager.leaveChannel();
            }
        }

        // Add the observer to the lifecycle
        lifecycleOwner.lifecycle.addObserver(observer)

        // When the effect leaves the Composition, remove the observer
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    Box(Modifier.fillMaxSize()) {
        localSurfaceView?.let { local ->
            AndroidView(factory = { local }, Modifier.fillMaxSize())
        }
        if(remoteUserMap.isNotEmpty()){
            RemoteView(remoteListInfo = remoteUserMap){
               val newLocalVideoView = it.value
                val desiredUserList = remoteUserMap.toMutableMap()
                desiredUserList[myUid] = localSurfaceView
                desiredUserList.remove(it.key)
                remoteUserMap = desiredUserList.toMap()
                localSurfaceView = newLocalVideoView ?:rawVideoAudioManager.localVideo
            }
        }
     UserControls(rawVideoAudioManager)
    }

}
@Composable
private fun RemoteView(remoteListInfo: Map<Int, SurfaceView?>,onSwap:(entry: Map.Entry<Int, SurfaceView?>)->Unit) {
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
                modifier = Modifier.size(Dp(112f), Dp(160f)).clickable {
                    onSwap(entry)
                }
            )
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

@Composable
private fun UserControls(rawVideoAudioManager: RawVideoAudioManager) {
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
               rawVideoAudioManager.muteLocalAudioStream(muted)
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
                rawVideoAudioManager.leaveChannel()
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
             rawVideoAudioManager.muteLocalVideoStream(videoDisabled)
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

