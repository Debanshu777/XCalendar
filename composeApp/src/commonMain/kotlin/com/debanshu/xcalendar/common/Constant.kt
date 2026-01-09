package com.debanshu.xcalendar.common

object Constant {
    const val SHADER_SRC = """
    // ============================================================================
    // Clean Glass Shader - Apple Fitness Style
    // Focus: Magnification + Color Fringing at edges (NO dark rim)
    // ============================================================================
    
    uniform float2 iResolution;
    uniform shader content;
    uniform float2 rectSize;
    uniform float2 center;
    uniform float  cornerRadius;
    uniform float  ior;
    uniform float  highlightStrength;
    uniform float  bevelWidth;
    uniform float  thickness;
    uniform float  shadowIntensity;
    uniform float  chromaticAberrationStrength;
    uniform float  frostedGlassBlurRadius;
    uniform float3 absorptionColor;
    uniform float  absorptionDensity;
    uniform float3 sourceColor;
    uniform float3 targetColor;
    uniform float  colorTolerance;
    uniform float  colorReplaceEnabled;
    
    const float PI = 3.14159265359;
    const float HALF_PI = 1.57079632679;
    const vec3 LIGHT_DIR = vec3(0.40824829, 0.40824829, 0.81649658);
    const vec3 VIEW_DIR = vec3(0.0, 0.0, -1.0);
    const vec3 NEG_VIEW_DIR = vec3(0.0, 0.0, 1.0);
    
    // Rounded Rectangle SDF
    float sdRoundedRect(float2 p, float2 halfSize, float r) {
        float maxR = min(halfSize.x, halfSize.y);
        r = min(r, maxR);
        float2 q = abs(p) - halfSize + r;
        return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
    }
    
    // Gradient of Rounded Rectangle SDF
    float2 gradRoundedRect(float2 p, float2 halfSize, float r) {
        float maxR = min(halfSize.x, halfSize.y);
        r = min(r, maxR);
        float2 q = abs(p) - halfSize + r;
        float2 grad;
        if (q.x > 0.0 && q.y > 0.0) {
            grad = normalize(q);
        } else if (q.x > q.y) {
            grad = float2(1.0, 0.0);
        } else {
            grad = float2(0.0, 1.0);
        }
        grad *= sign(p + 0.0001);
        return grad;
    }
    
    // Blur sampling
    half4 sampleBlurred(float2 coord, float blurRadius) {
        if (blurRadius <= 0.0) return content.eval(coord);
        
        const float GOLDEN_ANGLE = 2.39996323;
        half4 color = content.eval(coord) * 0.16;
        float totalWeight = 0.16;
        
        float r1 = blurRadius * 0.33;
        for (int i = 0; i < 4; i++) {
            float angle = float(i) * GOLDEN_ANGLE;
            float2 offset = float2(cos(angle), sin(angle)) * r1;
            color += content.eval(coord + offset) * 0.14;
            totalWeight += 0.14;
        }
        
        float r2 = blurRadius * 0.8;
        for (int i = 0; i < 8; i++) {
            float angle = float(i) * GOLDEN_ANGLE + 0.5;
            float2 offset = float2(cos(angle), sin(angle)) * r2;
            color += content.eval(coord + offset) * 0.07;
            totalWeight += 0.07;
        }
        
        return color / totalWeight;
    }
    
    // GGX Specular
    float ggxSpecular(vec3 normal, vec3 lightDir, vec3 viewDir, float roughness) {
        vec3 halfVec = normalize(lightDir - viewDir);
        float NdotH = max(dot(normal, halfVec), 0.0);
        float alpha = roughness * roughness;
        float alpha2 = alpha * alpha;
        float denom = NdotH * NdotH * (alpha2 - 1.0) + 1.0;
        return alpha2 / (PI * denom * denom + 0.0001);
    }
    
    // Fresnel
    float fresnel(float cosTheta, float ior) {
        float R0 = ((1.0 - ior) / (1.0 + ior));
        R0 *= R0;
        return R0 + (1.0 - R0) * pow(1.0 - cosTheta, 5.0);
    }
    
    // Color replacement - tints bright pixels toward target color
    // Uses brightness-based blending: white/bright pixels get tinted most
    half3 replaceColor(half3 color, vec3 source, vec3 target, float tolerance) {
        // Calculate brightness (luminance) of the pixel
        float brightness = dot(vec3(color), vec3(0.299, 0.587, 0.114));
        
        // Only tint bright pixels (above threshold)
        // tolerance controls the brightness threshold (higher = more selective)
        float threshold = 1.0 - tolerance;
        float tintStrength = smoothstep(threshold, 1.0, brightness);
        
        // Blend from original color toward target color based on brightness
        return mix(color, half3(target), tintStrength);
    }
    
    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / iResolution;
        float aspectRatio = iResolution.x / iResolution.y;
        float2 p = uv - center;
        
        float2 halfSize = rectSize * 0.5;
        halfSize.x *= aspectRatio;
        
        float2 pAspect = p;
        pAspect.x *= aspectRatio;
        float r = cornerRadius * aspectRatio;
        
        float sdf = sdRoundedRect(pAspect, halfSize, r);
        float2 grad = gradRoundedRect(pAspect, halfSize, r);
        
        float distFromEdge = -sdf;
        float maxInnerDist = min(halfSize.x, halfSize.y) - r;
        float normalizedDist = clamp(distFromEdge / max(maxInnerDist, 0.001), 0.0, 1.0);
        
        // Soft shadow behind glass
        float2 shadowDir = normalize(LIGHT_DIR.xy * float2(aspectRatio, 1.0));
        float2 shadowP = pAspect - shadowDir * 0.03;
        float shadowSdf = sdRoundedRect(shadowP, halfSize, r);
        float shadow = (1.0 - smoothstep(-0.05, 0.08, shadowSdf)) * shadowIntensity * 0.5;
        
        half4 bgColor = content.eval(fragCoord);
        half4 backgroundWithShadow = half4(bgColor.rgb * (1.0 - shadow), bgColor.a);
        
        // Early exit outside glass
        if (sdf > 0.015) return backgroundWithShadow;
        
        // Edge proximity (0 = center, 1 = edge)
        float edgeProximity = 1.0 - normalizedDist;
        
        // Bevel for 3D normal
        float bevelT = smoothstep(bevelWidth, 0.0, distFromEdge);
        float zComp = cos(bevelT * HALF_PI);
        float xyMag = sin(bevelT * HALF_PI);
        vec3 normal3d = normalize(vec3(grad * xyMag, zComp));
        float cosTheta = abs(dot(NEG_VIEW_DIR, normal3d));
        
        // Refraction
        float eta = 1.0 / ior;
        float k = 1.0 - eta * eta * (1.0 - cosTheta * cosTheta);
        vec3 refractedRay = (k >= 0.0) 
            ? eta * VIEW_DIR + (eta * cosTheta - sqrt(max(k, 0.0))) * normal3d
            : reflect(VIEW_DIR, normal3d);
        refractedRay = normalize(refractedRay);
        
        // ========================================
        // Distortion - Convex Lens Magnification
        // ========================================
        float distortionScale = thickness * iResolution.y * 2.5;
        
        // Smooth magnification curve
        float centerMag = pow(1.0 - edgeProximity, 2.0) * thickness * 12.0;
        float edgeCurve = pow(edgeProximity, 2.0) * smoothstep(0.0, 0.7, edgeProximity);
        float rimCurve = smoothstep(0.5, 1.0, edgeProximity) * edgeProximity;
        
        float distortMag = distortionScale * (bevelT * 0.6 + edgeCurve * 4.0 + rimCurve * 3.0);
        distortMag += centerMag * iResolution.y * 0.06;
        
        // Radial pull toward center
        float radialMag = (edgeCurve * 2.5 + rimCurve * 1.5) * distortionScale;
        float2 radialDistort = -grad * radialMag * 0.6;
        
        float2 baseRefractCoord = fragCoord + refractedRay.xy * distortMag + radialDistort;
        float2 reflectCoord = fragCoord + reflect(VIEW_DIR, normal3d).xy * distortMag * 0.4;
        
        // ========================================
        // Chromatic Aberration - Color Fringe at Edge
        // This is the key visual for the glass rim (NOT darkening)
        // ========================================
        float blurPx = frostedGlassBlurRadius * iResolution.y;
        half4 refractedColor;
        
        // Chromatic aberration increases toward edge
        float edgeCA = smoothstep(0.4, 1.0, edgeProximity);
        float caAmount = chromaticAberrationStrength * (1.0 + edgeCA * 6.0);
        
        if (caAmount > 0.0003) {
            float2 caDir = normalize(grad + 0.0001);
            float caMag = caAmount * iResolution.y * (0.4 + edgeCA * 2.0);
            
            // RGB channel separation for color fringe
            float2 rOffset = caDir * caMag;
            float2 bOffset = -caDir * caMag;
            
            half4 rSample = sampleBlurred(baseRefractCoord + rOffset, blurPx);
            half4 gSample = sampleBlurred(baseRefractCoord, blurPx);
            half4 bSample = sampleBlurred(baseRefractCoord + bOffset, blurPx);
            
            // Blend strength increases at edge
            float blend = 0.15 + edgeCA * 0.7;
            
            refractedColor.r = mix(gSample.r, rSample.r, blend);
            refractedColor.g = gSample.g;
            refractedColor.b = mix(gSample.b, bSample.b, blend);
            refractedColor.a = gSample.a;
        } else {
            refractedColor = sampleBlurred(baseRefractCoord, blurPx);
        }
        
        // Optional tinting (very subtle)
        if (absorptionDensity > 0.0) {
            vec3 absorption = vec3(1.0) - absorptionColor;
            float pathLen = thickness * 5.0;
            half3 transmittance = half3(exp(-absorption * pathLen * absorptionDensity));
            refractedColor.rgb *= transmittance;
        }
        
        half4 reflectedColor = content.eval(reflectCoord);
        
        // Color replacement - apply BEFORE mixing with reflections/highlights
        // Tints bright pixels (icons/text) toward the target color
        if (colorReplaceEnabled > 0.5) {
            refractedColor.rgb = replaceColor(refractedColor.rgb, sourceColor, targetColor, colorTolerance);
        }
        
        // Specular highlight
        float spec = ggxSpecular(normal3d, LIGHT_DIR, VIEW_DIR, 0.08);
        float fresnelVal = fresnel(cosTheta, ior);
        half3 highlight = half3(1.0, 0.99, 0.97) * spec * highlightStrength * bevelT;
        
        // Combine: refraction + reflection + highlight
        half3 glassColor = mix(refractedColor.rgb, reflectedColor.rgb, fresnelVal * 0.3);
        glassColor += highlight;
        
        // NO rim darkening - just clean glass
        half4 glassResult = half4(glassColor, refractedColor.a);
        
        // Smooth edge blend
        float edgeBlend = smoothstep(0.01, -0.005, sdf);
        
        return mix(backgroundWithShadow, glassResult, edgeBlend);
    }
"""
}
