package com.hapticks.app.core.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath

class RoundedPolygonShape(
    private val polygon: RoundedPolygon,
    private val matrix: Matrix = Matrix()
) : Shape {
    private val path = Path()

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        path.rewind()
        path.addPath(polygon.toPath().asComposePath())
        matrix.reset()
        val bounds = polygon.calculateBounds()
        val width = bounds[2] - bounds[0]
        val height = bounds[3] - bounds[1]
        matrix.scale(size.width / width, size.height / height)
        matrix.translate(-bounds[0], -bounds[1])
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

