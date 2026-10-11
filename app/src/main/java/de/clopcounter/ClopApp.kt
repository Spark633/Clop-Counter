package de.clopcounter

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.VideoFrameDecoder
import de.clopcounter.data.AppDatabase
import de.clopcounter.data.ClopRepository
import de.clopcounter.data.LinkPreviewFetcher
import de.clopcounter.data.SyncManager
import de.clopcounter.data.remote.SessionStore
import de.clopcounter.data.remote.SupabaseApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class ClopApp : Application(), ImageLoaderFactory {
    val database by lazy { AppDatabase.create(this) }
    val repository by lazy { ClopRepository(this, database) }
    val session by lazy { SessionStore(this) }
    val api by lazy { SupabaseApi(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY, session) }
    val sync by lazy { SyncManager(database, repository, api, session) }

    /** Lives as long as the process; used for background sync. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        sync.start(appScope)
    }

    /** Full sync (upload + download), e.g. when the app comes to the foreground. */
    fun requestFullSync() {
        appScope.launch { sync.sync(pull = true) }
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
                if (Build.VERSION.SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
            }
            .okHttpClient {
                // Some image hosts reject OkHttp's default user agent.
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        chain.proceed(
                            chain.request().newBuilder()
                                .header("User-Agent", LinkPreviewFetcher.BROWSER_USER_AGENT)
                                .build(),
                        )
                    }
                    .build()
            }
            .crossfade(true)
            .build()
}
