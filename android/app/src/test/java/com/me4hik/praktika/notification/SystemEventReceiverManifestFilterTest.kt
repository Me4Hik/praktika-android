// 02.09.2026 Case1 reboot system-event filter fix cursor by Me4Hik START - manifest filter regression
package com.me4hik.praktika.notification

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Parses the real source AndroidManifest.xml so a combined BOOT+package: filter cannot regress silently.
 */
class SystemEventReceiverManifestFilterTest {
    @Test
    fun systemEventReceiver_filtersSplitByDataSemantics() {
        val manifest = parseManifest(locateMainManifest())
        val receiver = findSystemEventReceiver(manifest)
        assertNotNull("SystemEventReceiver must be declared", receiver)
        val exported = attr(receiver!!, "exported")
        assertEquals("false", exported)
        val directBootAware = attr(receiver, "directBootAware")
        assertTrue(
            "directBootAware must remain unset/false",
            directBootAware.isBlank() || directBootAware == "false",
        )

        val filters = childElements(receiver, "intent-filter")
        assertEquals("expected exactly two intent-filters after split", 2, filters.size)

        val noDataFilter = filters.single { filterSchemes(it).isEmpty() }
        val packageFilter = filters.single { filterSchemes(it).isNotEmpty() }

        val noDataActions = filterActions(noDataFilter)
        assertTrue(noDataActions.contains(ACTION_BOOT_COMPLETED))
        assertTrue(noDataActions.contains(ACTION_TIME_SET))
        assertTrue(noDataActions.contains(ACTION_TIME_CHANGED))
        assertTrue(noDataActions.contains(ACTION_TIMEZONE_CHANGED))
        assertFalse(noDataActions.contains(ACTION_MY_PACKAGE_REPLACED))
        assertTrue("no-data filter must not declare data scheme", filterSchemes(noDataFilter).isEmpty())

        val packageActions = filterActions(packageFilter)
        assertEquals(setOf(ACTION_MY_PACKAGE_REPLACED), packageActions)
        assertEquals(setOf("package"), filterSchemes(packageFilter))

        assertFalse(
            "BOOT/TIME actions must not share a data-constrained filter with MY_PACKAGE_REPLACED",
            filters.any { filter ->
                val actions = filterActions(filter)
                val schemes = filterSchemes(filter)
                schemes.isNotEmpty() &&
                    actions.any { it in NO_DATA_ACTIONS } &&
                    actions.contains(ACTION_MY_PACKAGE_REPLACED)
            },
        )

        assertTrue(
            "RECEIVE_BOOT_COMPLETED permission must remain present",
            permissionNames(manifest).contains("android.permission.RECEIVE_BOOT_COMPLETED"),
        )
    }

    private fun locateMainManifest(): File {
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
            File("../app/src/main/AndroidManifest.xml"),
            File("android/app/src/main/AndroidManifest.xml"),
        )
        val found = candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate android/app/src/main/AndroidManifest.xml from ${File(".").absolutePath}")
        return found.canonicalFile
    }

    private fun parseManifest(file: File): Element {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val document = factory.newDocumentBuilder().parse(file)
        return document.documentElement
    }

    private fun findSystemEventReceiver(manifest: Element): Element? {
        val application = childElements(manifest, "application").single()
        return childElements(application, "receiver").firstOrNull { receiver ->
            attr(receiver, "name").endsWith("SystemEventReceiver")
        }
    }

    private fun permissionNames(manifest: Element): Set<String> {
        return childElements(manifest, "uses-permission").mapNotNull { element ->
            attr(element, "name").takeIf { it.isNotBlank() }
        }.toSet()
    }

    private fun filterActions(filter: Element): Set<String> {
        return childElements(filter, "action").mapNotNull { element ->
            attr(element, "name").takeIf { it.isNotBlank() }
        }.toSet()
    }

    private fun filterSchemes(filter: Element): Set<String> {
        return childElements(filter, "data").mapNotNull { element ->
            attr(element, "scheme").takeIf { it.isNotBlank() }
        }.toSet()
    }

    private fun attr(element: Element, localName: String): String {
        return element.getAttributeNS(ANDROID_NS, localName).ifBlank {
            element.getAttribute("android:$localName")
        }
    }

    private fun childElements(parent: Element, localName: String): List<Element> {
        val nodes = parent.childNodes
        val result = ArrayList<Element>()
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            if (node is Element && node.localName == localName) {
                result.add(node)
            }
        }
        return result
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        const val ACTION_BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED"
        const val ACTION_TIME_SET = "android.intent.action.TIME_SET"
        const val ACTION_TIME_CHANGED = "android.intent.action.TIME_CHANGED"
        const val ACTION_TIMEZONE_CHANGED = "android.intent.action.TIMEZONE_CHANGED"
        const val ACTION_MY_PACKAGE_REPLACED = "android.intent.action.MY_PACKAGE_REPLACED"
        val NO_DATA_ACTIONS = setOf(
            ACTION_BOOT_COMPLETED,
            ACTION_TIME_SET,
            ACTION_TIME_CHANGED,
            ACTION_TIMEZONE_CHANGED,
        )
    }
}
// 02.09.2026 Case1 reboot system-event filter fix cursor by Me4Hik END
