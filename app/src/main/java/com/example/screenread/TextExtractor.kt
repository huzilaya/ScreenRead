package com.example.screenread

import android.content.res.Resources
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object TextExtractor {

    private data class Item(
        val text: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    )

    private val screen: Rect by lazy {
        val dm = Resources.getSystem().displayMetrics
        Rect(0, 0, dm.widthPixels, dm.heightPixels)
    }

    fun extract(roots: List<AccessibilityNodeInfo>, selfPkg: String): String {
        val items = ArrayList<Item>(64)
        val seen = HashSet<String>(64)
        for (root in roots) walk(root, selfPkg, items, seen, 0)
        return toLines(items)
    }

    private fun walk(
        node: AccessibilityNodeInfo?,
        selfPkg: String,
        out: MutableList<Item>,
        seen: MutableSet<String>,
        depth: Int
    ) {
        if (node == null || depth > 60) return
        if (node.packageName?.toString() == selfPkg) return

        val text = pickText(node)
        if (text.isNotEmpty()) {
            val r = Rect()
            node.getBoundsInScreen(r)
            if (r.width() > 0 && r.height() > 0 && Rect.intersects(r, screen)) {
                val key = "$text#${r.left},${r.top}"
                if (seen.add(key)) out.add(Item(text, r.left, r.top, r.right, r.bottom))
            }
        }

        val count = try { node.childCount } catch (e: Exception) { 0 }
        for (i in 0 until count) {
            val child = try { node.getChild(i) } catch (e: Exception) { null }
            walk(child, selfPkg, out, seen, depth + 1)
        }
    }

    private fun pickText(node: AccessibilityNodeInfo): String {
        if (node.isPassword) return ""
        node.text?.toString()?.trim()?.let { if (it.isNotEmpty()) return it }
        node.contentDescription?.toString()?.trim()?.let { if (it.isNotEmpty()) return it }
        node.hintText?.toString()?.trim()?.let { if (it.isNotEmpty()) return it }
        return ""
    }

    private fun toLines(items: List<Item>): String {
        if (items.isEmpty()) return ""
        val sorted = items.sortedWith(compareBy({ it.top }, { it.left }))
        val sb = StringBuilder()

        var rowTop = Int.MIN_VALUE
        var rowBottom = Int.MIN_VALUE

        for (it in sorted) {
            if (rowTop == Int.MIN_VALUE) {
                sb.append(it.text)
                rowTop = it.top
                rowBottom = it.bottom
                continue
            }
            val center = (it.top + it.bottom) / 2
            val rowCenter = (rowTop + rowBottom) / 2
            val rowHeight = max(12, rowBottom - rowTop)

            if (abs(center - rowCenter) <= rowHeight / 2) {
                sb.append("  ").append(it.text)
                rowTop = min(rowTop, it.top)
                rowBottom = max(rowBottom, it.bottom)
            } else {
                sb.append('\n').append(it.text)
                rowTop = it.top
                rowBottom = it.bottom
            }
        }
        return sb.toString()
    }
}
