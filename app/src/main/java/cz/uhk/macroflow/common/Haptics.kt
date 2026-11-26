package cz.uhk.macroflow.common

import android.os.Build
import android.view.HapticFeedbackConstants

/** Haptika „potvrzeno“: CONFIRM je až od Androidu 11, starší (minSdk 26) dostanou běžné cvaknutí místo ničeho. */
object Haptics {
    val CONFIRM: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
}
