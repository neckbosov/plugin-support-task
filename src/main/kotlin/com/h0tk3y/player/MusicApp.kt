package com.h0tk3y.player

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URLClassLoader
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

open class MusicApp(
    private val pluginClasspath: List<File>,
    private val enabledPluginClasses: Set<String>
) : AutoCloseable {
    fun init() {
        plugins.forEach { plugin ->
            val filename = "./build/tmp/" + plugin.pluginId
            if (File(filename).exists()) {
                FileInputStream(filename).use { stream ->
                    plugin.init(stream)
                }
            } else {
                plugin.init(null)
            }
        }
        musicLibrary // access to initialize
        player.init()
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        plugins.forEach { plugin ->
            val filename = "./build/tmp/" + plugin.pluginId
            File(filename).createNewFile()
            FileOutputStream(filename).use { stream ->
                plugin.persist(stream)
            }
        }
    }

    fun wipePersistedPluginData() {
        plugins.forEach { plugin ->
            val filename = "./build/tmp/" + plugin.pluginId
            File(filename).delete()
        }
    }

    private val pluginClassLoader: ClassLoader =
        URLClassLoader(pluginClasspath.map { it.toURI().toURL() }.toTypedArray())

    private val plugins: List<MusicPlugin> by lazy {
        enabledPluginClasses.map { className ->
            try {
                val cl = pluginClassLoader.loadClass(className)
                if (!cl.kotlin.isSubclassOf(MusicPlugin::class)) {
                    throw PluginClassNotFoundException(className)
                }
                val ctors = cl.kotlin.constructors
                val appCtor = ctors.find {
                    it.parameters.singleOrNull()?.type == MusicApp::class.createType()
                }
                if (appCtor != null && appCtor == cl.kotlin.primaryConstructor) {
                    appCtor.call(this) as MusicPlugin
                } else {
                    val zeroCtor = ctors.singleOrNull { it.parameters.isEmpty() }
                    if (zeroCtor != null) {
                        val prop = cl.kotlin.memberProperties
                            .find { it.name == "musicAppInstance" }
                                as? KMutableProperty1<*, *>
                            ?: throw IllegalPluginException(cl)
                        (zeroCtor.call() as MusicPlugin).also {
                            prop.setter.call(it, this)
                        }
                    } else {
                        throw IllegalPluginException(cl)
                    }
                }
            } catch (e: ClassNotFoundException) {
                throw PluginClassNotFoundException(className)
            }
        }
    }

    fun findSinglePlugin(pluginClassName: String): MusicPlugin? =
        plugins.singleOrNull { it.pluginId == pluginClassName }

    fun <T : MusicPlugin> getPlugins(pluginClass: Class<T>): List<T> =
        plugins.filterIsInstance(pluginClass)

    private val musicLibraryContributors: List<MusicLibraryContributorPlugin>
        get() = getPlugins(MusicLibraryContributorPlugin::class.java)

    protected val playbackListeners: List<PlaybackListenerPlugin>
        get() = getPlugins(PlaybackListenerPlugin::class.java)

    val musicLibrary: MusicLibrary by lazy {
        musicLibraryContributors
            .sortedWith(compareBy({ it.preferredOrder }, { it.pluginId }))
            .fold(MusicLibrary(mutableListOf())) { acc, it -> it.contribute(acc) }
    }

    open val player: MusicPlayer by lazy {
        JLayerMusicPlayer(
            playbackListeners
        )
    }

    fun startPlayback(playlist: Playlist, fromPosition: Int) {
        player.playbackState = PlaybackState.Playing(
            PlaylistPosition(
                playlist,
                fromPosition
            ), isResumed = false
        )
    }

    fun nextOrStop() = player.playbackState.playlistPosition?.let {
        val nextPosition = it.position + 1
        player.playbackState =
            if (nextPosition in it.playlist.tracks.indices)
                PlaybackState.Playing(
                    PlaylistPosition(
                        it.playlist,
                        nextPosition
                    ), isResumed = false
                )
            else
                PlaybackState.Stopped
    }

    @Volatile
    var isClosed = false
        private set
}