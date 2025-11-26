package com.debanshu.xcalendar.common

import androidx.compose.ui.graphics.RenderEffect

/**
 * RGB color values for Beer-Lambert absorption.
 * Values range from 0.0 (fully absorbing) to 1.0 (fully transparent) per channel.
 */
data class AbsorptionColor(
    val r: Float = 1.0f,
    val g: Float = 1.0f,
    val b: Float = 1.0f,
) {
    companion object {
        val CLEAR = AbsorptionColor(1.0f, 1.0f, 1.0f)
        val BLUE_TINT = AbsorptionColor(0.92f, 0.96f, 1.0f)
        val SMOKY = AbsorptionColor(0.9f, 0.9f, 0.92f)
    }
}

/**
 * Parameters for the glass shader effect.
 *
 * @param width Normalized width of the glass rectangle (0..1)
 * @param height Normalized height of the glass rectangle (0..1)
 * @param centerX Normalized X position of center (0..1)
 * @param centerY Normalized Y position of center (0..1)
 * @param cornerRadius Corner radius for rounded rectangle (normalized)
 * @param ior Index of Refraction (1.0=air, 1.33=water, 1.52=glass)
 * @param highlightStrength Specular highlight intensity
 * @param bevelWidth Width of the rounded edge bevel
 * @param thickness Optical thickness affecting distortion magnitude
 * @param shadowIntensity Cast shadow darkness (0..1)
 * @param chromaticAberration Color fringing strength at edges
 * @param frostedBlurRadius Blur amount for frosted glass effect
 * @param absorptionColor Beer-Lambert absorption coefficients
 * @param absorptionDensity How strongly light is absorbed
 */
data class GlassShaderParams(
    val width: Float = 0.5f,
    val height: Float = 0.3f,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val cornerRadius: Float = 0.05f,
    val ior: Float = 1.33f,
    val highlightStrength: Float = 0.8f,
    val bevelWidth: Float = 0.02f,
    val thickness: Float = 0.02f,
    val shadowIntensity: Float = 0.3f,
    val chromaticAberration: Float = 0.001f,
    val frostedBlurRadius: Float = 0.0f,
    val absorptionColor: AbsorptionColor = AbsorptionColor.CLEAR,
    val absorptionDensity: Float = 0.0f,
) {
    companion object {
        /**
         * Apple Fitness-style magnifying lens effect.
         * Clean glass with strong magnification and color fringing at edges.
         */
        fun magnifyingLens() =
            GlassShaderParams(
                width = 0.4f,
                height = 0.4f,
                centerX = 0.5f,
                centerY = 0.5f,
                cornerRadius = 0.2f,
                ior = 1.5f,
                highlightStrength = 1.5f,
                bevelWidth = 0.12f,
                thickness = 0.18f,
                shadowIntensity = 0.15f,
                chromaticAberration = 0.006f,
                frostedBlurRadius = 0.0f,
                absorptionColor = AbsorptionColor.CLEAR,
                absorptionDensity = 0.0f,
            )

        /**
         * Water droplet effect with strong edge distortion.
         */
        fun waterDroplet() =
            GlassShaderParams(
                width = 0.4f,
                height = 0.4f,
                centerX = 0.5f,
                centerY = 0.5f,
                cornerRadius = 0.2f,
                ior = 1.45f,
                highlightStrength = 0f,
                bevelWidth = 0.2f,
                thickness = 0.15f,
                shadowIntensity = 0f,
                chromaticAberration = 0.1f,
                frostedBlurRadius = 0.0f,
                absorptionColor = AbsorptionColor.CLEAR,
                absorptionDensity = 0.0f,
            )

        /**
         * Pill-shaped glass for navigation bar indicators.
         */
        fun pill() =
            GlassShaderParams(
                width = 0.5f,
                height = 0.15f,
                centerX = 0.5f,
                centerY = 0.5f,
                cornerRadius = 0.075f,
                ior = 1.5f,
                highlightStrength = 1.2f,
                bevelWidth = 0.04f,
                thickness = 0.08f,
                shadowIntensity = 0.15f,
                chromaticAberration = 0.005f,
                frostedBlurRadius = 0.0f,
                absorptionColor = AbsorptionColor.CLEAR,
                absorptionDensity = 0.0f,
            )

        /**
         * Subtle glass overlay for UI elements.
         */
        fun subtle() =
            GlassShaderParams(
                width = 0.6f,
                height = 0.3f,
                centerX = 0.5f,
                centerY = 0.5f,
                cornerRadius = 0.02f,
                ior = 1.25f,
                highlightStrength = 0.4f,
                bevelWidth = 0.015f,
                thickness = 0.015f,
                shadowIntensity = 0.1f,
                chromaticAberration = 0.001f,
                frostedBlurRadius = 0.0f,
                absorptionColor = AbsorptionColor.CLEAR,
                absorptionDensity = 0.0f,
            )

        /**
         * Frosted glass effect with blur.
         */
        fun frostedGlass() =
            GlassShaderParams(
                width = 0.6f,
                height = 0.4f,
                centerX = 0.5f,
                centerY = 0.5f,
                cornerRadius = 0.03f,
                ior = 1.4f,
                highlightStrength = 0.6f,
                bevelWidth = 0.02f,
                thickness = 0.03f,
                shadowIntensity = 0.15f,
                chromaticAberration = 0.0005f,
                frostedBlurRadius = 0.012f,
                absorptionColor = AbsorptionColor.SMOKY,
                absorptionDensity = 0.2f,
            )

        /**
         * Rainbow prism with maximum color dispersion.
         */
        fun rainbowPrism() =
            GlassShaderParams(
                width = 0.45f,
                height = 0.45f,
                centerX = 0.5f,
                centerY = 0.5f,
                cornerRadius = 0.225f,
                ior = 1.8f,
                highlightStrength = 2.5f,
                bevelWidth = 0.2f,
                thickness = 0.25f,
                shadowIntensity = 0.2f,
                chromaticAberration = 0.015f,
                frostedBlurRadius = 0.0f,
                absorptionColor = AbsorptionColor.CLEAR,
                absorptionDensity = 0.0f,
            )
    }
}

/**
 * Creates a glass shader render effect for the given dimensions and parameters.
 */
expect fun createGlassRenderEffect(
    width: Float,
    height: Float,
    params: GlassShaderParams,
): RenderEffect?
