package com.debanshu.xcalendar.common

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect

/**
 * Android implementation of the glass shader effect.
 * Requires API 33+ (Tiramisu) for RuntimeShader support.
 */
actual fun createGlassRenderEffect(
    width: Float,
    height: Float,
    params: GlassShaderParams,
): ComposeRenderEffect? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        createGlassRenderEffectApi33(width, height, params)
    } else {
        null
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun createGlassRenderEffectApi33(
    width: Float,
    height: Float,
    params: GlassShaderParams,
): ComposeRenderEffect {
    val shader = RuntimeShader(Constant.SHADER_SRC)

    shader.setFloatUniform("iResolution", width, height)
    shader.setFloatUniform("rectSize", params.width, params.height)
    shader.setFloatUniform("center", params.centerX, params.centerY)
    shader.setFloatUniform("cornerRadius", params.cornerRadius)
    shader.setFloatUniform("ior", params.ior)
    shader.setFloatUniform("highlightStrength", params.highlightStrength)
    shader.setFloatUniform("bevelWidth", params.bevelWidth)
    shader.setFloatUniform("thickness", params.thickness)
    shader.setFloatUniform("shadowIntensity", params.shadowIntensity)
    shader.setFloatUniform("chromaticAberrationStrength", params.chromaticAberration)
    shader.setFloatUniform("frostedGlassBlurRadius", params.frostedBlurRadius)
    shader.setFloatUniform(
        "absorptionColor",
        params.absorptionColor.r,
        params.absorptionColor.g,
        params.absorptionColor.b,
    )
    shader.setFloatUniform("absorptionDensity", params.absorptionDensity)

    // Color replacement uniforms
    shader.setFloatUniform(
        "sourceColor",
        params.sourceColor?.r ?: 0f,
        params.sourceColor?.g ?: 0f,
        params.sourceColor?.b ?: 0f,
    )
    shader.setFloatUniform(
        "targetColor",
        params.targetColor?.r ?: 0f,
        params.targetColor?.g ?: 0f,
        params.targetColor?.b ?: 0f,
    )
    shader.setFloatUniform("colorTolerance", params.colorTolerance)
    shader.setFloatUniform("colorReplaceEnabled", if (params.colorReplaceEnabled) 1f else 0f)

    return RenderEffect
        .createRuntimeShaderEffect(shader, "content")
        .asComposeRenderEffect()
}
