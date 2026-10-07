package net.aspw.client.features.module.impl.other

import net.aspw.client.event.EventTarget
import net.aspw.client.event.UpdateEvent
import net.aspw.client.features.module.Module
import net.aspw.client.features.module.ModuleCategory
import net.aspw.client.features.module.ModuleInfo
import net.aspw.client.utils.MusicManager
import net.aspw.client.value.FloatValue
import net.aspw.client.value.ListValue
import java.io.File

@ModuleInfo(name = "MusicPlayer", spacedName = "Music Player", category = ModuleCategory.OTHER)
class MusicPlayer : Module() {

    private val modeValue = ListValue("Mode", arrayOf("Sequential", "Random", "Single"), "Sequential")
    private val volumeValue = FloatValue("Volume", 1.0f, 0.0f, 2.0f)

    private val songList = MusicManager.getMusicList().map { it.name }.toTypedArray()
    private val songValue = ListValue(
        "Song", 
        if (songList.isNotEmpty()) songList else arrayOf("None"), 
        if (songList.isNotEmpty()) songList[0] else "None"
    )

    private var currentTrackIndex = -1
    private var lastVolume = 1.0f
    
    private var uiSelectedSong = ""
    private var lastSelectedSong = ""

    override fun onEnable() {
        val songs = MusicManager.getMusicList()
        if (songs.isEmpty()) {
            chat("§c[Music] 你的 Music 文件夹是空的！请放入音乐文件")
            state = false 
            return
        }

        lastVolume = volumeValue.get()
        
        uiSelectedSong = songValue.get()
        lastSelectedSong = uiSelectedSong
        
        if (modeValue.get().lowercase() == "single") {
            val targetFile = songs.find { it.name == songValue.get() } ?: songs[0]
            playTrack(targetFile)
        } else {
            playNext()
        }
    }

    override fun onDisable() {
        MusicManager.stop()
        chat("§e[Music] 音乐已停止")
    }

    @EventTarget
    fun onUpdate(event: UpdateEvent) {
        val songs = MusicManager.getMusicList()
        if (songs.isEmpty()) return

        if (volumeValue.get() != lastVolume) {
            lastVolume = volumeValue.get()
            MusicManager.setVolume(lastVolume)
        }

        if (songValue.get() != uiSelectedSong) {
            uiSelectedSong = songValue.get() 
            lastSelectedSong = uiSelectedSong 
            val targetFile = songs.find { it.name == lastSelectedSong }
            if (targetFile != null) {
                currentTrackIndex = songs.indexOf(targetFile)
                playTrack(targetFile)
            }
        }

        if (state && !MusicManager.isPlaying()) {
            playNext()
        }
    }

    private fun playNext() {
        val songs = MusicManager.getMusicList()
        if (songs.isEmpty()) return

        val targetFile: File = when (modeValue.get().lowercase()) {
            "random" -> songs.random()
            "single" -> songs.find { it.name == lastSelectedSong } ?: songs[0]
            "sequential" -> {
                currentTrackIndex++
                if (currentTrackIndex >= songs.size) currentTrackIndex = 0 
                songs[currentTrackIndex]
            }
            else -> songs[0]
        }

        lastSelectedSong = targetFile.name
        currentTrackIndex = songs.indexOf(targetFile)
        if (currentTrackIndex == -1) currentTrackIndex = 0

        playTrack(targetFile)
    }

    private fun playTrack(targetFile: File) {
        MusicManager.play(targetFile, volumeValue.get())
        chat("§a[Music] 正在播放: §f${targetFile.nameWithoutExtension}")
    }
}

