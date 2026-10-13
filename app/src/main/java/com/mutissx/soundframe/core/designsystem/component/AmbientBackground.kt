package com.mutissx.soundframe.core.designsystem.component

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb
import com.mutissx.soundframe.core.designsystem.art.rememberArtClock
import com.mutissx.soundframe.core.designsystem.theme.LocalReduceMotion
import com.mutissx.soundframe.core.designsystem.theme.SfColors
import kotlin.math.cos
import kotlin.math.sin

/**
 * Living atmospheric backdrop: drifting fields of coloured light with fine film grain.
 * AGSL on Android 13+, layered radial gradients on older versions.
 */
@Composable
fun AmbientBackground(
    modifier: Modifier = Modifier.fillMaxSize(),
    colors: List<Color> = listOf(SfColors.Violet, SfColors.Cyan, SfColors.Rose),
    energy: () -> Float = { 0f },
) {
    val reduceMotion = LocalReduceMotion.current
    val time = rememberArtClock(running = !reduceMotion)
    val c1 by animateColorAsState(colors[0], tween(1200), label = "c1")
    val c2 by animateColorAsState(colors[1 % colors.size], tween(1200), label = "c2")
    val c3 by animateColorAsState(colors[2 % colors.size], tween(1200), label = "c3")
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ShaderBackground(modifier, time, c1, c2, c3, energy)
    } else {
        GradientBackground(modifier, time, c1, c2, c3, energy)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ShaderBackground(modifier: Modifier, time: State<Float>, c1: Color, c2: Color, c3: Color, energy: () -> Float) {
    val shader = remember { RuntimeShader(AMBIENT_SHADER) }
    val brush = remember(shader) { ShaderBrush(shader) }
    Canvas(modifier) {
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("time", time.value)
        shader.setFloatUniform("energy", energy())
        shader.setColorUniform("c1", c1.toArgb())
        shader.setColorUniform("c2", c2.toArgb())
        shader.setColorUniform("c3", c3.toArgb())
        shader.setColorUniform("base", SfColors.Ink0.toArgb())
        drawRect(brush)
    }
}

@Composable
private fun GradientBackground(modifier: Modifier, time: State<Float>, c1: Color, c2: Color, c3: Color, energy: () -> Float) {
    Canvas(modifier) {
        val t = time.value * 0.05f
        val e = 0.6f + energy() * 0.8f
        drawRect(SfColors.Ink0)
        fun blob(color: Color, x: Float, y: Float, radius: Float, strength: Float) {
            val center = Offset(x * size.width, y * size.height)
            drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = strength * e), Color.Transparent), center, radius * size.maxDimension),
                radius * size.maxDimension, center,
            )
        }
        blob(c1, 0.25f + 0.12f * sin(t * 2.1f), 0.18f + 0.10f * cos(t * 1.7f), 0.55f, 0.30f)
        blob(c2, 0.80f + 0.10f * cos(t * 1.3f), 0.55f + 0.12f * sin(t * 1.9f), 0.5f, 0.22f)
        blob(c3, 0.40f + 0.15f * sin(t * 1.1f + 2f), 0.95f + 0.06f * cos(t * 2.3f), 0.6f, 0.2f)
    }
}

private const val AMBIENT_SHADER = """
uniform float2 resolution;
uniform float time;
uniform float energy;
layout(color) uniform half4 c1;
layout(color) uniform half4 c2;
layout(color) uniform half4 c3;
layout(color) uniform half4 base;

float hash(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float blob(float2 p, float2 c, float r) {
    float d = length(p - c);
    return exp(-(d * d) / (r * r));
}

half4 main(float2 coord) {
    float2 uv = coord / resolution;
    float aspect = resolution.x / resolution.y;
    float2 p = float2(uv.x * aspect, uv.y);
    float t = time * 0.05;
    float e = 0.6 + energy * 0.8;
    float2 a = float2(aspect * (0.25 + 0.12 * sin(t * 2.1)), 0.18 + 0.10 * cos(t * 1.7));
    float2 b = float2(aspect * (0.80 + 0.10 * cos(t * 1.3)), 0.55 + 0.12 * sin(t * 1.9));
    float2 c = float2(aspect * (0.40 + 0.15 * sin(t * 1.1 + 2.0)), 0.95 + 0.06 * cos(t * 2.3));
    float3 col = float3(base.rgb);
    col += float3(c1.rgb) * blob(p, a, 0.55) * 0.34 * e;
    col += float3(c2.rgb) * blob(p, b, 0.50) * 0.26 * e;
    col += float3(c3.rgb) * blob(p, c, 0.60) * 0.22 * e;
    float v = smoothstep(1.25, 0.25, length(uv - float2(0.5, 0.45)));
    col *= mix(0.55, 1.0, v);
    col += (hash(coord + fract(time * 0.37) * 100.0) - 0.5) * 0.028;
    return half4(half3(col), 1.0);
}
"""
