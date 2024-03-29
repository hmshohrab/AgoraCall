package com.shohrab.agoraCall

import android.content.Context
import android.util.Log
import io.agora.rtc2.*
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL

open class AuthenticationManager(context: Context) : AgoraManager(
    context
) {
    var serverUrl: String // The base URL to your token server
    private val tokenExpiryTime: Int // Time in seconds after which the token will expire.
    private val baseEventHandler: IRtcEngineEventHandler? // To extend the event handler from the base class

    // Callback interface to receive the http response from an async token request
    interface TokenCallback {
        fun onTokenReceived(rtcToken: String?)
        fun onError(errorMessage: String)
    }

    init {
        // Read the server url and expiry time from the config file
        serverUrl = config?.optString("serverUrl")?:""
        tokenExpiryTime = config?.optInt("tokenExpiryTime", 600) ?:600
        baseEventHandler = super.iRtcEngineEventHandler
    }

    // Listen for the event that a token is about to expire
    override val iRtcEngineEventHandler: IRtcEngineEventHandler
        get() = object : IRtcEngineEventHandler() {
            // Listen for the event that the token is about to expire
            override fun onTokenPrivilegeWillExpire(token: String) {
                sendMessage("Token is about to expire")
                // Get a new token
                fetchToken(channelName, object : TokenCallback {
                    override fun onTokenReceived(rtcToken: String?) {
                        // Use the token to renew
                        agoraEngine?.renewToken(rtcToken)
                        sendMessage("Token renewed")
                    }

                    override fun onError(errorMessage: String) {
                        // Handle the error
                        sendMessage("Error: $errorMessage")
                    }
                })
                super.onTokenPrivilegeWillExpire(token)
            }

            // Reuse events handlers from the base class
            override fun onUserJoined(uid: Int, elapsed: Int) {
                baseEventHandler?.onUserJoined(uid, elapsed)
            }

            override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
                baseEventHandler?.onJoinChannelSuccess(channel, uid, elapsed)
                handleOnJoinChannelSuccess(channel, uid, elapsed)
            }

            override fun onUserOffline(uid: Int, reason: Int) {
                baseEventHandler?.onUserOffline(uid, reason)
            }

            override fun onConnectionStateChanged(state: Int, reason: Int) {
                connectionStateChanged(state, reason)
            }

            override fun onEncryptionError(errorType: Int) {
                Log.d("Encryption error", errorType.toString())
            }

            override fun onProxyConnected(
                channel: String?,
                uid: Int,
                proxyType: Int,
                localProxyIp: String?,
                elapsed: Int
            ) {
                // Connected to proxyType
            }
        }

    open fun handleOnJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
        // For adding code in derived classes
    }

    fun fetchToken(channelName: String, callback: TokenCallback) {
        // Use the uid from the config file if not specified
        fetchToken(channelName, config?.optInt("uid")?:0, callback)
    }

    fun fetchToken(channelName: String, uid: Int, callback: TokenCallback) {
        val tokenRole = if (isBroadcaster) 1 else 2
        // Prepare the Url
        val urlLString = "$serverUrl/rtc/$channelName/$tokenRole/uid/$uid/?expiry=$tokenExpiryTime"

        val client = OkHttpClient()

        // Create a request
        val request: Request = Request.Builder()
            .url(urlLString)
            .header("Content-Type", "application/json; charset=UTF-8")
            .get()
            .build()

        // Send the async http request
        val call = client.newCall(request)
        call.enqueue(object : Callback {
            // Receive the response in a callback
            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    try {
                        // Extract rtcToken from the response
                        val responseBody = response.body?.string()
                        val jsonObject = JSONObject(responseBody)
                        val rtcToken = jsonObject.getString("rtcToken")
                        // Return the token
                        callback.onTokenReceived(rtcToken)
                    } catch (e: JSONException) {
                        e.printStackTrace()
                        callback.onError("Invalid token response")
                    }
                } else {
                    callback.onError("Token request failed")
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                callback.onError("IOException: $e")
            }
        })
    }

    fun joinChannelWithToken(): Int {
        // Use the default channel name if one is not specified
        return joinChannelWithToken(channelName)
    }

    open fun joinChannelWithToken(channelName: String): Int {
        if (agoraEngine == null) setupAgoraEngine()
        return if (isValidURL(serverUrl)) { // A valid server url is available
            // Fetch a token from the server for channelName
            fetchToken(channelName, object : TokenCallback {
                override fun onTokenReceived(rtcToken: String?) {
                    // Handle the received rtcToken
                    joinChannel(channelName, rtcToken)
                }

                override fun onError(errorMessage: String) {
                    // Handle the error
                    sendMessage("Error: $errorMessage")
                }
            })
            0
        } else { // use the token from the config.json file
            val token = config?.optString("rtcToken")
            joinChannel(channelName, token)
        }
    }

    open fun connectionStateChanged(state: Int, reason: Int) {

    }

    companion object {
        // A helper function to check that the URL is in the correct form
        fun isValidURL(urlString: String?): Boolean {
            return try {
                // Attempt to create a URL object from the given string
                val url = URL(urlString)
                // Check if the protocol and host in the URL are not empty
                url.protocol != null && url.host != null
            } catch (e: MalformedURLException) {
                // The given string is not a valid URL
                false
            }
        }
    }
}