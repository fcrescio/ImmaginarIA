package com.immagineran.no

import androidx.annotation.StringRes

/**
 * Supported visual styles for image generation.
 *
 * @property labelRes String resource for the user-facing label.
 * @property prompt Token describing the style for the generator prompt.
 */
enum class ImageStyle(@StringRes val labelRes: Int, val prompt: String) {
    PHOTOREALISTIC(R.string.style_photorealistic, "photorealistic"),
    CARTOON(R.string.style_cartoon, "cartoon"),
    MANGA(R.string.style_manga, "manga")
}
