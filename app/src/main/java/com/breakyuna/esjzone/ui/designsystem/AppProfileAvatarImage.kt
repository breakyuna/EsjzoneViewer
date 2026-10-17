package com.breakyuna.esjzone.ui.designsystem

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil3.Image as CoilImage
import coil3.ImageLoader
import coil3.compose.asPainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.app.PresentationAccess
import com.breakyuna.esjzone.database.dao.CacheDao
import com.breakyuna.esjzone.database.dao.put
import com.breakyuna.esjzone.network.EsjzoneUrls
import com.breakyuna.esjzone.util.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Restore the last successful avatar before loading its replacement. */
@Composable
internal fun AppProfileAvatarImage(
    model: String,
    cacheKey: String,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sharedRequest = sharedMirrorImageRequest(model)
    val request = remember(sharedRequest) { sharedRequest.newBuilder().size(256).build() }
    var image by remember(cacheKey) { mutableStateOf<CoilImage?>(null) }

    LaunchedEffect(cacheKey, request) {
        try {
            loadProfileAvatar(request, cacheKey, PresentationAccess.imageLoader,
                PresentationAccess.database.cacheDao(), restoreCached = image == null) { image = it }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLogger.w("ProfileAvatar", "Unable to refresh avatar; retaining previous image", error)
        }
    }

    val painter = image?.asPainter(context) ?: painterResource(R.drawable.cover_missing)
    Image(painter, contentDescription, modifier, contentScale = ContentScale.Crop)
}

internal suspend fun loadProfileAvatar(
    request: ImageRequest,
    cacheKey: String,
    imageLoader: ImageLoader,
    cacheDao: CacheDao,
    restoreCached: Boolean,
    onImage: (CoilImage) -> Unit
) {
    val loadedKey = "$cacheKey-loaded"
    if (restoreCached) {
        val cachedUrl = withContext(Dispatchers.IO) {
            cacheDao.findByKey(loadedKey)?.value ?: cacheDao.findByKey(cacheKey)?.value
        }
        if (!cachedUrl.isNullOrBlank()) {
            val resolvedUrl = if (Uri.parse(cachedUrl).isRelative) EsjzoneUrls.resolve(cachedUrl) else cachedUrl
            val key = EsjzoneUrls.canonicalCacheUrl(resolvedUrl)
            val cachedRequest = request.newBuilder()
                .data(resolvedUrl)
                .memoryCacheKey(key)
                .diskCacheKey(key)
                .networkCachePolicy(CachePolicy.DISABLED)
                .build()
            val cached = imageLoader.execute(cachedRequest) as? SuccessResult
            if (cached != null) {
                onImage(cached.image)
                withContext(Dispatchers.IO) { cacheDao.put(loadedKey, resolvedUrl) }
                if (resolvedUrl == request.data) return
            }
        }
    }
    if (request.data.toString().isBlank()) return
    val fresh = imageLoader.execute(request) as? SuccessResult ?: return
    onImage(fresh.image)
    // A metadata refresh must never replace the address of a usable cached image.
    withContext(Dispatchers.IO) { cacheDao.put(loadedKey, request.data.toString()) }
}
