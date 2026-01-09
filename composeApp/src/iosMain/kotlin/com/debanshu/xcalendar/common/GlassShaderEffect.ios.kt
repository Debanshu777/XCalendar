package com.debanshu.xcalendar.common

import androidx.compose.ui.graphics.asComposeRenderEffect
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect

/**
 * iOS (Skia) implementation of the glass shader effect.
 */
actual fun createGlassRenderEffect(
    width: Float,
    height: Float,
    params: GlassShaderParams,
): ComposeRenderEffect? {
    return try {
        val runtimeEffect = RuntimeEffect.makeForShader(Constant.SHADER_SRC)
        val shaderBuilder = RuntimeShaderBuilder(runtimeEffect)

        shaderBuilder.uniform("iResolution", width, height)
        shaderBuilder.uniform("rectSize", params.width, params.height)
        shaderBuilder.uniform("center", params.centerX, params.centerY)
        shaderBuilder.uniform("cornerRadius", params.cornerRadius)
        shaderBuilder.uniform("ior", params.ior)
        shaderBuilder.uniform("highlightStrength", params.highlightStrength)
        shaderBuilder.uniform("bevelWidth", params.bevelWidth)
        shaderBuilder.uniform("thickness", params.thickness)
        shaderBuilder.uniform("shadowIntensity", params.shadowIntensity)
        shaderBuilder.uniform("chromaticAberrationStrength", params.chromaticAberration)
        shaderBuilder.uniform("frostedGlassBlurRadius", params.frostedBlurRadius)
        shaderBuilder.uniform(
            "absorptionColor",
            params.absorptionColor.r,
            params.absorptionColor.g,
            params.absorptionColor.b,
        )
        shaderBuilder.uniform("absorptionDensity", params.absorptionDensity)

        // Color replacement uniforms
        shaderBuilder.uniform(
            "sourceColor",
            params.sourceColor?.r ?: 0f,
            params.sourceColor?.g ?: 0f,
            params.sourceColor?.b ?: 0f,
        )
        shaderBuilder.uniform(
            "targetColor",
            params.targetColor?.r ?: 0f,
            params.targetColor?.g ?: 0f,
            params.targetColor?.b ?: 0f,
        )
        shaderBuilder.uniform("colorTolerance", params.colorTolerance)
        shaderBuilder.uniform("colorReplaceEnabled", if (params.colorReplaceEnabled) 1f else 0f)

        ImageFilter.makeRuntimeShader(
            runtimeShaderBuilder = shaderBuilder,
            shaderNames = arrayOf("content"),
            inputs = arrayOf(null),
        ).asComposeRenderEffect()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
