package com.immagineran.no

import androidx.annotation.StringRes

/**
 * Supported backends for image generation.
 */
enum class ImageProvider(@StringRes val labelRes: Int) {
    OPENROUTER(R.string.image_provider_openrouter),
    FAL(R.string.image_provider_fal),
}
