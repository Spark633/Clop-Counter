package de.clopcounter

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import android.os.Build
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import de.clopcounter.data.AppDatabase
import de.clopcounter.data.ClopRepository

class ClopApp : Application(), ImageLoaderFactory {
    val database by lazy { AppDatabase.create(this) }
    val repository by lazy { ClopRepository(this, database) }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
            }
            .crossfade(true)
            .build()
}
