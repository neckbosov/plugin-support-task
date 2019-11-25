package com.h0tk3y.player.test

import com.h0tk3y.player.StaticPlaylistsLibraryContributor
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private val thirdPartyPluginClasses: List<File> =
    System.getProperty("third-party-plugin-classes").split(File.pathSeparator).map { File(it) }
private val currentPlugin = "com.h0tk3y.third.party.plugin.AdvertisingPlugin"

class AdvertisingPluginTest {
    private val defaultEnabledPlugins = setOf(
        StaticPlaylistsLibraryContributor::class.java.canonicalName,
        currentPlugin
    )

    private fun withApp(
        wipePersistedData: Boolean = false,
        pluginClasspath: List<File> = thirdPartyPluginClasses,
        enabledPlugins: Set<String> = defaultEnabledPlugins,
        doTest: TestableMusicApp.() -> Unit
    ) {
        val app = TestableMusicApp(pluginClasspath, enabledPlugins)
        if (wipePersistedData) {
            app.wipePersistedPluginData()
        }
        app.use {
            it.init()
            it.doTest()
        }
    }

    private val ads = listOf(
        "Проблемы с доступом в JCasino?...",
        "Все говорят : \"АК, а как поднять бабла?\" - \"A-A-Azino, Azino777\"",
        "https://tsarn.website/sp - лучшая страничка интернета",
        "8-800-555-35-35 - лучше позвонить чем у кого-то занимать",
        "\"Каким файлообменником ты пользуешься?\" - \"Конечно же скайпом.\""
    )

    @Test
    fun testCounter() {
        withApp(true) {
            val playlist = musicLibrary.playlists.first()
            check(playlist.tracks.size >= 3)
            startPlayback(playlist, 0)
            player.finishedTrack()
            val plugin = assertNotNull(findSinglePlugin(currentPlugin))
            assertEquals(2, plugin::class.memberFunctions.single { it.name == "tracksToAd" }.call(plugin))
        }
    }

    @Test
    fun testDefaultAd() {
        withApp(true) {
            val byteStream = ByteArrayOutputStream()
            val plugin = assertNotNull(findSinglePlugin(currentPlugin))
            val prop =
                plugin::class.memberProperties.single { it.name == "advertisingStream" } as KMutableProperty1<*, *>
            prop.setter.call(plugin, PrintStream(byteStream))
            val playlist = musicLibrary.playlists.first()
            check(playlist.tracks.size >= 3)
            startPlayback(playlist, 0)
            for (i in 0..3) {
                player.finishedTrack()
            }
            assertEquals("Your advertising can be here\n", byteStream.toString("UTF-8"))
        }
    }

    @Test
    fun testCustomAd() {
        withApp(true) {
            val byteStream = ByteArrayOutputStream()
            val plugin = assertNotNull(findSinglePlugin(currentPlugin))
            val prop =
                plugin::class.memberProperties.single { it.name == "advertisingStream" } as KMutableProperty1<*, *>
            prop.setter.call(plugin, PrintStream(byteStream))
            plugin::class.memberFunctions.single { it.name == "rmAd" }.call(plugin, "Your advertising can be here")
            plugin::class.memberFunctions.single { it.name == "addAd" }.call(plugin, ads.first())
            val playlist = musicLibrary.playlists.first()
            check(playlist.tracks.size >= 3)
            startPlayback(playlist, 0)
            for (i in 0..3) {
                player.finishedTrack()
            }
            assertEquals("${ads.first()}\n", byteStream.toString("UTF-8"))
        }
    }

    @Test
    fun testPersist() {
        withApp(true) {
            val plugin = assertNotNull(findSinglePlugin(currentPlugin))
            plugin::class.memberFunctions.single { it.name == "rmAd" }.call(plugin, "Your advertising can be here")
            ads.forEach {
                plugin::class.memberFunctions.single { it.name == "addAd" }.call(plugin, it)
            }
            val playlist = musicLibrary.playlists.first()
            check(playlist.tracks.size >= 3)
            startPlayback(playlist, 0)
            for (i in 0..1) {
                player.finishedTrack()
            }
        }
        withApp {
            val plugin = assertNotNull(findSinglePlugin(currentPlugin))
            assertEquals(2, plugin::class.memberProperties.single { it.name == "tracksCount" }.getter.call(plugin))
            assertEquals(ads, plugin::class.memberProperties.single { it.name == "advertising" }.getter.call(plugin))
        }
    }
}