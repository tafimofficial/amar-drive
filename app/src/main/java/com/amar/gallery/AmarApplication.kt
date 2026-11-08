package com.amar.gallery

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.amar.gallery.data.tdlib.TdClientManager
import com.amar.gallery.util.NativeEngine

class AmarApplication : Application(), ImageLoaderFactory {

    companion object {
        private const val TAG = "AmarApp"
        lateinit var instance: AmarApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        Log.i(TAG, "Starting Amar Application...")

        // 1. Initialize C Native Engine
        val nativeStatus = NativeEngine.initEngine()
        Log.i(TAG, "Native Status: $nativeStatus")

        // 2. Pre-initialize TDLib background client
        TdClientManager.getInstance(this).initialize()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20) // 20% available heap for thumbnails
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150L * 1024L * 1024L) // 150 MB disk cache
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.w(TAG, "onTrimMemory level: $level. Releasing low-priority buffers.")
    }
}

