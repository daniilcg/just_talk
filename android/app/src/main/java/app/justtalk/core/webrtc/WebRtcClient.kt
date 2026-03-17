package app.justtalk.core.webrtc

import android.content.Context
import org.json.JSONObject
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

class WebRtcClient(
    private val appContext: Context,
    private val eglBase: EglBase,
    private val onLocalTrack: (VideoTrack) -> Unit,
    private val onRemoteTrack: (VideoTrack) -> Unit,
    private val onIceCandidate: (IceCandidate) -> Unit,
    private val onConnectionState: (PeerConnection.PeerConnectionState) -> Unit,
    private val onDataChannel: (DataChannel) -> Unit
) {
    private val factory: PeerConnectionFactory
    private val peerConnection: PeerConnection

    private var videoSource: VideoSource? = null
    private var audioSource: AudioSource? = null
    private var capturer: VideoCapturer? = null

    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
        )

        val encoderFactory = DefaultVideoEncoderFactory(
            eglBase.eglBaseContext,
            true,
            true
        )
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )

        peerConnection =
            factory.createPeerConnection(
                PeerConnection.RTCConfiguration(iceServers).apply {
                    sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                },
                object : PeerConnection.Observer {
                    override fun onSignalingChange(newState: PeerConnection.SignalingState) = Unit
                    override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) = Unit
                    override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
                    override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) = Unit
                    override fun onIceCandidate(candidate: IceCandidate) {
                        onIceCandidate(candidate)
                    }

                    override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) = Unit
                    override fun onAddStream(stream: org.webrtc.MediaStream) = Unit
                    override fun onRemoveStream(stream: org.webrtc.MediaStream) = Unit
                    override fun onDataChannel(dc: DataChannel) {
                        onDataChannel(dc)
                    }

                    override fun onRenegotiationNeeded() = Unit
                    override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out org.webrtc.MediaStream>) = Unit
                    override fun onTrack(transceiver: PeerConnection.RtpTransceiver) {
                        val track = transceiver.receiver.track() ?: return
                        if (track.kind() == MediaStreamTrack.VIDEO_TRACK_KIND) {
                            (track as? VideoTrack)?.let(onRemoteTrack)
                        }
                    }

                    override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                        onConnectionState(newState)
                    }
                }
            )
                ?: error("Failed to create PeerConnection")

        startLocalMedia()
        createOrAttachDataChannel()
    }

    private fun startLocalMedia() {
        videoSource = factory.createVideoSource(false)
        audioSource = factory.createAudioSource(MediaConstraints())

        val videoTrack = factory.createVideoTrack("localVideo", videoSource)
        val audioTrack = factory.createAudioTrack("localAudio", audioSource)
        localVideoTrack = videoTrack
        localAudioTrack = audioTrack

        onLocalTrack(videoTrack)

        val streamId = "stream0"
        peerConnection.addTrack(videoTrack, listOf(streamId))
        peerConnection.addTrack(audioTrack, listOf(streamId))

        capturer = createCameraCapturer(appContext)
        val textureHelper = SurfaceTextureHelper.create("CameraThread", eglBase.eglBaseContext)
        (capturer as? CameraVideoCapturer)?.initialize(textureHelper, appContext, videoSource?.capturerObserver)
        capturer?.startCapture(720, 1280, 30)
    }

    private fun createOrAttachDataChannel() {
        val init = DataChannel.Init().apply {
            ordered = true
        }
        val dc = peerConnection.createDataChannel("chat", init)
        if (dc != null) onDataChannel(dc)
    }

    private fun createCameraCapturer(context: Context): VideoCapturer {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames
        val front = deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
        val chosen = front ?: deviceNames.firstOrNull() ?: error("No camera devices")
        return enumerator.createCapturer(chosen, null) ?: error("Failed to create camera capturer")
    }

    fun createOffer(onSdp: (SessionDescription) -> Unit) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        peerConnection.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection.setLocalDescription(SimpleSdpObserver(), desc)
                onSdp(desc)
            }
        }, constraints)
    }

    fun createAnswer(onSdp: (SessionDescription) -> Unit) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        peerConnection.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription) {
                peerConnection.setLocalDescription(SimpleSdpObserver(), desc)
                onSdp(desc)
            }
        }, constraints)
    }

    fun setRemoteDescription(desc: SessionDescription) {
        peerConnection.setRemoteDescription(SimpleSdpObserver(), desc)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection.addIceCandidate(candidate)
    }

    fun toggleCamera(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun toggleMic(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    fun close() {
        runCatching { capturer?.stopCapture() }
        runCatching { capturer?.dispose() }
        runCatching { videoSource?.dispose() }
        runCatching { audioSource?.dispose() }
        runCatching { peerConnection.close() }
        runCatching { factory.dispose() }
    }

    companion object {
        fun sdpToJson(desc: SessionDescription): JSONObject =
            JSONObject().put("kind", "sdp").put("type", desc.type.canonicalForm()).put("sdp", desc.description)

        fun jsonToSdp(obj: JSONObject): SessionDescription? {
            if (obj.optString("kind") != "sdp") return null
            val type = SessionDescription.Type.fromCanonicalForm(obj.optString("type"))
            val sdp = obj.optString("sdp")
            if (sdp.isBlank()) return null
            return SessionDescription(type, sdp)
        }

        fun iceToJson(c: IceCandidate): JSONObject =
            JSONObject()
                .put("kind", "ice")
                .put("sdpMid", c.sdpMid)
                .put("sdpMLineIndex", c.sdpMLineIndex)
                .put("candidate", c.sdp)

        fun jsonToIce(obj: JSONObject): IceCandidate? {
            if (obj.optString("kind") != "ice") return null
            val sdpMid = obj.optString("sdpMid")
            val sdpMLineIndex = obj.optInt("sdpMLineIndex", -1)
            val candidate = obj.optString("candidate")
            if (sdpMid.isBlank() || sdpMLineIndex < 0 || candidate.isBlank()) return null
            return IceCandidate(sdpMid, sdpMLineIndex, candidate)
        }
    }
}

open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(desc: SessionDescription) = Unit
    override fun onSetSuccess() = Unit
    override fun onCreateFailure(error: String) = Unit
    override fun onSetFailure(error: String) = Unit
}

