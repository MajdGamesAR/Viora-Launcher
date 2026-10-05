package com.viora.launcher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.viora.launcher.core.util.MinecraftPath
import com.viora.launcher.ui.theme.VioraColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI
import javax.imageio.ImageIO

/**
 * ✅ Cache في الذاكرة — يمنع إعادة التحميل عند التنقل
 */
private object AvatarMemoryCache {
    private val cache = mutableMapOf<String, ImageBitmap>()
    private const val MAX_SIZE = 100

    fun get(key: String): ImageBitmap? = synchronized(cache) { cache[key] }

    fun put(key: String, value: ImageBitmap) {
        synchronized(cache) {
            if (cache.size >= MAX_SIZE) {
                // احذف الأقدم (LRU بسيط)
                val firstKey = cache.keys.firstOrNull()
                if (firstKey != null) cache.remove(firstKey)
            }
            cache[key] = value
        }
    }

    fun clear() = synchronized(cache) { cache.clear() }
}

@Composable
fun SteveAvatar(
    skinUrl: String? = null,
    username: String = "Steve",
    uuid: String? = null,
    size: Int = 36
) {
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    val cacheDir = MinecraftPath.launcherAssets
    val cacheKey = "head_v4_${username}.png"
    val cacheFile = File(cacheDir, cacheKey)

    LaunchedEffect(username, uuid, skinUrl) {
        // ===== 1. Cache في الذاكرة =====
        val memCached = AvatarMemoryCache.get(username)
        if (memCached != null) {
            imageBitmap = memCached
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {
            try {
                // ===== 2. Cache على القرص =====
                if (cacheFile.exists() && cacheFile.length() > 100) {
                    val cached = loadImageFromFile(cacheFile)
                    if (cached != null) {
                        imageBitmap = cached
                        AvatarMemoryCache.put(username, cached)
                        println("✅ Using disk cache: $username")
                        return@withContext
                    }
                }

                // ===== 3. ابنِ قائمة المصادر =====
                val candidates = mutableListOf<Pair<String, Boolean>>()

                // الأولوية 1: skinUrl من Mojang
                if (!skinUrl.isNullOrBlank() && skinUrl.startsWith("http")) {
                    candidates.add(skinUrl to true)
                }

                // الأولوية 2: mc-heads.net بالـ username
                if (username.isNotBlank() && username != "Steve") {
                    candidates.add("https://mc-heads.net/avatar/$username/256" to false)
                }

                // الأولوية 3: mc-heads.net بالـ UUID
                if (!uuid.isNullOrBlank()) {
                    candidates.add("https://mc-heads.net/avatar/$uuid/256" to false)
                }

                // الأولوية 4: Steve
                candidates.add("https://mc-heads.net/avatar/MHF_Steve/256" to false)

                // ===== 4. جرّب كل مصدر =====
                for ((url, needsCrop) in candidates) {
                    try {
                        println("🎨 Trying: $url (crop=$needsCrop)")
                        val bytes = URI(url).toURL().openStream().use { it.readBytes() }

                        val bitmap = if (needsCrop) {
                            cropHeadFromFullSkin(bytes, scale = 8)
                        } else {
                            loadImageFromBytes(bytes)
                        }

                        if (bitmap != null) {
                            // ✅ احفظ في cache على القرص
                            saveBitmapToPng(bitmap, cacheFile)
                            // ✅ احفظ في cache الذاكرة
                            AvatarMemoryCache.put(username, bitmap)
                            imageBitmap = bitmap
                            println("✅ Loaded: ${bitmap.width}x${bitmap.height} from $url")
                            break
                        }
                    } catch (e: Exception) {
                        println("   ⚠️ Failed: ${e.message}")
                    }
                }

            } catch (e: Exception) {
                println("❌ Avatar error: ${e.message}")
            }
        }
    }

    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(VioraColors.BrandGradient),
        contentAlignment = Alignment.Center
    ) {
        imageBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = username,
                modifier = Modifier
                    .size(size.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        }
    }
}

// ============================================================
//  CROP HEAD FROM FULL SKIN + SCALE
// ============================================================
private fun cropHeadFromFullSkin(skinBytes: ByteArray, scale: Int = 8): ImageBitmap? {
    return try {
        val srcImage: BufferedImage = ImageIO.read(ByteArrayInputStream(skinBytes))
            ?: return null

        val srcWidth = srcImage.width
        println("   📐 Source: ${srcWidth}×${srcImage.height}")

        val headSize = 8
        val headStartX = 8
        val headStartY = 8
        val hatStartX = 40
        val hatStartY = 8

        val outSize = headSize * scale

        val smallImage = BufferedImage(headSize, headSize, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until headSize) {
            for (x in 0 until headSize) {
                val headPixel = srcImage.getRGB(headStartX + x, headStartY + y)
                val hatPixel = if (srcWidth >= 64) {
                    srcImage.getRGB(hatStartX + x, hatStartY + y)
                } else 0

                val hatAlpha = (hatPixel ushr 24) and 0xFF
                val finalPixel = if (hatAlpha > 0) hatPixel else headPixel
                smallImage.setRGB(x, y, finalPixel)
            }
        }

        val outImage = BufferedImage(outSize, outSize, BufferedImage.TYPE_INT_ARGB)
        val g = outImage.createGraphics()
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
        )
        g.drawImage(smallImage, 0, 0, outSize, outSize, null)
        g.dispose()

        println("   ✅ Cropped: ${outSize}×${outSize}")
        outImage.toComposeImageBitmap()

    } catch (e: Exception) {
        println("   ❌ Crop failed: ${e.message}")
        null
    }
}

// ============================================================
//  HELPERS
// ============================================================
private fun loadImageFromFile(file: File): ImageBitmap? {
    return try {
        Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
    } catch (e: Exception) {
        null
    }
}

private fun loadImageFromBytes(bytes: ByteArray): ImageBitmap? {
    return try {
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (e: Exception) {
        null
    }
}

private fun saveBitmapToPng(bitmap: ImageBitmap, file: File) {
    try {
        val bufferedImage = BufferedImage(
            bitmap.width,
            bitmap.height,
            BufferedImage.TYPE_INT_ARGB
        )
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.readPixels(
            buffer = pixels,
            startX = 0,
            startY = 0,
            width = bitmap.width,
            height = bitmap.height
        )
        bufferedImage.setRGB(
            0, 0,
            bitmap.width, bitmap.height,
            pixels, 0, bitmap.width
        )

        val baos = java.io.ByteArrayOutputStream()
        ImageIO.write(bufferedImage, "png", baos)
        file.writeBytes(baos.toByteArray())
        println("   💾 Cached: ${file.name} (${baos.size()} bytes)")
    } catch (e: Exception) {
        println("   ⚠️ Failed to cache: ${e.message}")
    }
}