package net.aspw.client.utils

import net.minecraft.client.Minecraft
import net.minecraft.client.audio.SoundManager
import paulscode.sound.SoundSystem
import java.io.File

object MusicManager {
    val musicDir = File(Minecraft.getMinecraft().mcDataDir, "Daylight-Client/Music")
    
    var currentSongName = ""
    private var isMusicPlaying = false
    private var lastPlayTime = 0L
    private var currentSourceName = ""
    
    // 🌟 废弃通道垃圾回收队列
    private val cleanupQueue = mutableMapOf<String, Long>()
    
    // 🌟 新增黑科技：强制切歌冷却时间 (拦截器)
    private var switchDelayTime = 0L

    // 兼容了 .ogg 和截图里出现的 .wav
    fun getMusicList() = musicDir.listFiles { _, name -> 
        val lower = name.lowercase()
        lower.endsWith(".ogg") || lower.endsWith(".wav")
    }?.toList() ?: emptyList()

    private fun getSoundSystem(): SoundSystem? {
        try {
            val soundHandler = Minecraft.getMinecraft().soundHandler
            val smField = soundHandler.javaClass.declaredFields.firstOrNull { it.type == SoundManager::class.java } ?: return null
            smField.isAccessible = true
            val soundManager = smField.get(soundHandler) as SoundManager
            val ssField = soundManager.javaClass.declaredFields.firstOrNull { it.type.name.contains("SoundSystem") } ?: return null
            ssField.isAccessible = true
            return ssField.get(soundManager) as SoundSystem
        } catch (e: Exception) { return null }
    }

    fun play(file: File, volume: Float) {
        val sndSystem = getSoundSystem() ?: return
        try {
            // 1. 如果有老歌，温柔地停止并回收
            if (currentSourceName.isNotEmpty()) {
                try { sndSystem.stop(currentSourceName) } catch(e: Exception){}
                cleanupQueue[currentSourceName] = System.currentTimeMillis() + 2500L 
            }

            // 2. 重置状态与冷却
            switchDelayTime = 0L 
            currentSourceName = "DL_BGM_" + System.currentTimeMillis()
            currentSongName = file.nameWithoutExtension
            isMusicPlaying = true
            lastPlayTime = System.currentTimeMillis()

            // 3. 播放新歌
            sndSystem.newStreamingSource(true, currentSourceName, file.toURI().toURL(), file.name, false, 0f, 0f, 0f, 0, 0f)
            sndSystem.setVolume(currentSourceName, volume)
            sndSystem.play(currentSourceName)
            
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun setVolume(volume: Float) {
        if (currentSourceName.isNotEmpty()) {
            getSoundSystem()?.setVolume(currentSourceName, volume)
        }
    }

    fun stop() {
        if (currentSourceName.isNotEmpty()) {
            try { getSoundSystem()?.stop(currentSourceName) } catch(e: Exception){}
            cleanupQueue[currentSourceName] = System.currentTimeMillis() + 2500L
        }
        isMusicPlaying = false
        currentSongName = ""
        currentSourceName = ""
        switchDelayTime = 0L // 重置冷却
    }

    fun isPlaying(): Boolean {
        val ss = getSoundSystem() ?: return false
        val now = System.currentTimeMillis()
        
        // 🌟 智能垃圾回收 (GC) 后台默默清理
        val toRemove = mutableListOf<String>()
        for ((key, time) in cleanupQueue) {
            if (now > time) {
                try { ss.removeSource(key) } catch(e: Exception){}
                toRemove.add(key)
            }
        }
        toRemove.forEach { cleanupQueue.remove(it) }

        // 🌟 拦截器 1：判断是否处于“强制冷却期”
        if (switchDelayTime > 0) {
            if (now < switchDelayTime) {
                return true // 时间还没到，欺骗外部“还在播放”，强制挂起！
            } else {
                switchDelayTime = 0L
                return false // 1.5秒到了，放行切歌信号！
            }
        }

        if (!isMusicPlaying || currentSourceName.isEmpty()) return false
        
        // 3秒新手保护期，防止刚切歌时声卡没反应过来被误判为播放结束
        if (now - lastPlayTime < 3000) return true
        
        // 查询当前通道播放状态
        val playing = ss.playing(currentSourceName)
        if (!playing) {
            // 🌟 拦截器 2：歌终于放完了！触发结算
            try { ss.stop(currentSourceName) } catch(e: Exception){}
            cleanupQueue[currentSourceName] = now + 2500L
            currentSourceName = ""
            
            // 【视觉效果】提前清空歌名，你的灵动岛会自动失去名字并缩回去
            currentSongName = "" 
            isMusicPlaying = false
            
            // 【核心】设置 1500 毫秒 (1.5秒) 的强制冷却等待时间！
            switchDelayTime = now + 1500L
            
            return true // 返回 true，卡住外部代码的切歌请求
        }
        return true
    }
}

