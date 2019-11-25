package com.h0tk3y.third.party.plugin

import com.h0tk3y.player.MusicApp
import com.h0tk3y.player.PlaybackListenerPlugin
import com.h0tk3y.player.PlaybackState
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintStream

//interface AdsPlugin : PlaybackListenerPlugin {
//    var advertisingStream: PrintStream
//    fun addAd(msg: String)
//    fun rmAd(msg: String)
//    val frequency: Int
//    val songsToAd: Int
//}

class AdvertisingPlugin : PlaybackListenerPlugin {
    override lateinit var musicAppInstance: MusicApp
    var advertisingStream: PrintStream = System.out
    var tracksCount: Int = 0
        private set

    var advertising: MutableList<String> = mutableListOf("Your advertising can be here")
    fun addAd(msg: String) = advertising.add(msg)

    fun rmAd(msg: String) = advertising.remove(msg)

    private var curentAdvert: Int = 0
    val frequency = 3
    fun tracksToAd() = frequency - tracksCount

    override fun init(persistedState: InputStream?) {
        if (persistedState != null) {
            val text = persistedState.reader().readText().lines()
            tracksCount = text[0].toIntOrNull() ?: 0
            advertising.clear()
            advertising.addAll(text.drop(1).filterNot { it.isEmpty() })
        }

    }

    override fun persist(stateStream: OutputStream) {
        stateStream.write(
            buildString {
                appendln(tracksCount)
                advertising.forEach {
                    appendln(it)
                }
            }.toByteArray()
        )
        if (musicAppInstance.isClosed) {
            advertisingStream.close()
        }
    }

    override fun onPlaybackStateChange(oldPlaybackState: PlaybackState, newPlaybackState: PlaybackState) {
        if (oldPlaybackState.playlistPosition?.currentTrack != newPlaybackState.playlistPosition?.currentTrack
            && oldPlaybackState.playlistPosition != null
        ) {
            tracksCount++
            if (tracksCount == frequency) {
                tracksCount = 0
                advertisingStream.println(advertising[curentAdvert])
                curentAdvert = (curentAdvert + 1) % advertising.size
                Thread.sleep(5000)
            }
        }
    }
}
