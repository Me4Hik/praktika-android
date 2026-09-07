package com.me4hik.praktika.ui.tour

/** Counts About taps until tester tools unlock. Returns (nextCount, unlockedNow). */
object TesterUnlockGesture {
    const val REQUIRED_TAPS = 5

    fun onTap(currentCount: Int, alreadyUnlocked: Boolean): Pair<Int, Boolean> {
        if (alreadyUnlocked) return currentCount to false
        val next = currentCount + 1
        return if (next >= REQUIRED_TAPS) {
            0 to true
        } else {
            next to false
        }
    }
}
