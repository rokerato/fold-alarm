package dev.rokerato.foldprobe

import android.content.Context
import android.graphics.Typeface
import android.util.Log

/**
 * Jost, the cover screen's face.
 *
 * The settings screen uses the phone's own font so it sits naturally in One UI; the
 * clock is the one place with room for a voice of its own. Jost is a geometric sans
 * in the Futura line -- calm, even figures that stay legible in a hairline weight --
 * and it ships as a single variable font, so any weight is one file (OFL, bundled
 * under assets/fonts with its licence).
 *
 * Weights are built once and cached: a variation instance costs a font parse.
 */
object CoverFont {

    const val THIN = 200
    const val LIGHT = 300
    const val REGULAR = 400
    const val MEDIUM = 500

    /** Tabular figures, so the clock does not shift sideways as its digits change. */
    const val FIGURES = "tnum"

    private const val PATH = "fonts/Jost.ttf"
    private val cache = HashMap<Int, Typeface>()

    fun weight(context: Context, weight: Int): Typeface = cache.getOrPut(weight) {
        runCatching {
            Typeface.Builder(context.assets, PATH)
                .setFontVariationSettings("'wght' $weight")
                .build()
        }.getOrElse {
            Log.w(StandbyActivity.TAG, "cover font unavailable, using the system's", it)
            Typeface.SANS_SERIF
        }
    }

    /** The settings screen's three clock weights, in Jost. */
    fun forPreference(step: Int): Int = when (step) {
        0 -> THIN
        1 -> LIGHT
        else -> REGULAR
    }
}
