package com.mutissx.soundframe.core.graphics

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Anti-aliased polylines and dots built as a triangle mesh and drawn with [Canvas.drawVertices].
 *
 * The GPU backend rasterises anti-aliased stroked paths, wide lines and round points on the CPU or
 * one by one, which dominated frame time for several styles. A mesh is GPU-native, and many shapes
 * share one draw. Every edge carries a 1px fringe that fades to transparent in place of
 * anti-aliasing. Unlike a stroked path, a polyline that folds over itself is blended twice where
 * it overlaps.
 *
 * Vertex colours are modulated with the paint's shader when it has one (sampled at the vertex
 * positions), so keep the paint's colour opaque white.
 */
internal class StrokeMesh(maxPoints: Int = 8192) {
    // Indices are 16-bit; staying within the signed range avoids relying on wraparound.
    private val maxVertices = (maxPoints * 4).coerceAtMost(32768)
    private val verts = FloatArray(maxVertices * 2)
    private val colors = IntArray(maxVertices)
    private val indices = ShortArray(maxVertices * 5)
    private var vertexCount = 0
    private var indexCount = 0
    private var connect = false

    /** Starts a new polyline of up to [count] points, drawing what's queued first if it wouldn't fit. */
    fun begin(canvas: Canvas, paint: Paint, count: Int) {
        reserve(canvas, paint, count * 4, count * 18)
        connect = false
    }

    /** Starts a new polyline of [count] points in a mesh that's built once, so it must fit. */
    fun begin(count: Int) {
        check(vertexCount + count * 4 <= maxVertices && indexCount + count * 18 <= indices.size) { "Mesh is full" }
        connect = false
    }

    /** Adds a point at ([x], [y]) with unit normal ([nx], [ny]), [halfWidth] in pixels and a non-premultiplied [color]. */
    fun point(x: Float, y: Float, nx: Float, ny: Float, halfWidth: Float, color: Int) {
        val inner = max(halfWidth - 0.5f, 0f)
        val outer = halfWidth + 0.5f
        val rgb = color and 0x00ffffff
        val core = coreColor(color, halfWidth)
        val c = vertexCount
        vertex(x - nx * outer, y - ny * outer, rgb)
        vertex(x - nx * inner, y - ny * inner, core)
        vertex(x + nx * inner, y + ny * inner, core)
        vertex(x + nx * outer, y + ny * outer, rgb)
        if (connect) {
            val prev = c - 4
            for (q in 0 until 3) quad(prev + q, prev + q + 1, c + q, c + q + 1)
        }
        connect = true
    }

    /** Adds a round dot of [radius] pixels, drawing what's queued first if it wouldn't fit. */
    fun dot(canvas: Canvas, paint: Paint, x: Float, y: Float, radius: Float, color: Int) {
        reserve(canvas, paint, 1 + DOT_SIDES * 2, DOT_SIDES * 9)
        connect = false
        val inner = max(radius - 0.5f, 0f)
        val outer = radius + 0.5f
        val rgb = color and 0x00ffffff
        val core = coreColor(color, radius)
        val centre = vertexCount
        vertex(x, y, core)
        for (s in 0 until DOT_SIDES) {
            vertex(x + RIM_COS[s] * inner, y + RIM_SIN[s] * inner, core)
            vertex(x + RIM_COS[s] * outer, y + RIM_SIN[s] * outer, rgb)
        }
        for (s in 0 until DOT_SIDES) {
            val a = centre + 1 + s * 2
            val b = centre + 1 + (s + 1) % DOT_SIDES * 2
            triangle(centre, a, b)
            quad(a, a + 1, b, b + 1)
        }
    }

    /** Draws everything queued with [paint] and clears the mesh. */
    fun flush(canvas: Canvas, paint: Paint) {
        draw(canvas, paint)
        vertexCount = 0
        indexCount = 0
        connect = false
    }

    /** Draws everything queued with [paint] and keeps it, for meshes built once and drawn every frame. */
    fun draw(canvas: Canvas, paint: Paint) {
        if (indexCount == 0) return
        canvas.drawVertices(
            Canvas.VertexMode.TRIANGLES, vertexCount * 2, verts, 0, verts, 0,
            colors, 0, indices, 0, indexCount, paint,
        )
    }

    private fun reserve(canvas: Canvas, paint: Paint, vertices: Int, indexes: Int) {
        require(vertices <= maxVertices) { "Shape too large for this mesh" }
        if (vertexCount + vertices > maxVertices || indexCount + indexes > indices.size) flush(canvas, paint)
    }

    /** Shapes thinner than a pixel keep their coverage by fading the core instead. */
    private fun coreColor(color: Int, halfWidth: Float): Int =
        if (halfWidth >= 0.5f) color else (((color ushr 24) * halfWidth * 2f).toInt() shl 24) or (color and 0x00ffffff)

    private fun vertex(x: Float, y: Float, color: Int) {
        verts[vertexCount * 2] = x
        verts[vertexCount * 2 + 1] = y
        colors[vertexCount] = color
        vertexCount++
    }

    private fun triangle(a: Int, b: Int, c: Int) {
        indices[indexCount++] = a.toShort()
        indices[indexCount++] = b.toShort()
        indices[indexCount++] = c.toShort()
    }

    /** Two triangles covering a-b on one side and c-d on the other. */
    private fun quad(a: Int, b: Int, c: Int, d: Int) {
        triangle(a, b, c)
        triangle(b, d, c)
    }

    private companion object {
        const val DOT_SIDES = 10
        val RIM_COS = FloatArray(DOT_SIDES) { cos(it * 2.0 * PI / DOT_SIDES).toFloat() }
        val RIM_SIN = FloatArray(DOT_SIDES) { sin(it * 2.0 * PI / DOT_SIDES).toFloat() }
    }
}
