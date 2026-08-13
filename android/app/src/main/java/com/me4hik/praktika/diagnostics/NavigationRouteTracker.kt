// 10.08.2026 Post-release fixes cursor by Me4Hik START - Production diagnostic flight recorder
package com.me4hik.praktika.diagnostics

object NavigationRouteTracker {
    @Volatile
    var currentRoute: String? = null
        private set

    @Volatile
    var previousRoute: String? = null
        private set

    fun onRouteChanged(route: String?) {
        if (route == currentRoute) {
            return
        }
        previousRoute = currentRoute
        currentRoute = route
    }

    internal fun resetForTests() {
        currentRoute = null
        previousRoute = null
    }
}
// 10.08.2026 Post-release fixes cursor by Me4Hik END
