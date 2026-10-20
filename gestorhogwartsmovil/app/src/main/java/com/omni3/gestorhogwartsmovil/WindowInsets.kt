package com.omni3.gestorhogwartsmovil

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Mantiene controles y textos fuera de las barras del sistema y de los recortes de pantalla.
 *
 * @author koldo
 * @description Mantiene controles y textos fuera de las barras del sistema y de los recortes de pantalla.
 */
fun View.applySystemInsets() {
    val initialLeft = paddingLeft
    val initialTop = paddingTop
    val initialRight = paddingRight
    val initialBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        view.setPadding(initialLeft + bars.left, initialTop + bars.top,
            initialRight + bars.right, initialBottom + bars.bottom)
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
