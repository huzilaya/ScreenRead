package com.example.screenread

object KeywordEngine {

    enum class Action { UP, DOWN, STOP, COPY }

    data class Rule(val keyword: String, val action: Action)

    private val actionMap = mapOf(
        "上滑" to Action.UP, "up" to Action.UP, "next" to Action.UP,
        "下滑" to Action.DOWN, "down" to Action.DOWN,
        "停止" to Action.STOP, "stop" to Action.STOP, "pause" to Action.STOP,
        "复制" to Action.COPY, "copy" to Action.COPY
    )

    fun parse(raw: String): List<Rule> =
        raw.split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val parts = line.split(',', '，', ':', '：', '|')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                val kw = parts.getOrNull(0) ?: return@mapNotNull null
                if (kw.isEmpty()) return@mapNotNull null
                val action = actionMap[parts.getOrNull(1)?.lowercase() ?: ""] ?: Action.UP
                Rule(kw, action)
            }

    fun match(rules: List<Rule>, text: String): Rule? =
        rules.sortedByDescending { it.keyword.length }
            .firstOrNull { text.contains(it.keyword, ignoreCase = true) }

    fun label(action: Action): String = when (action) {
        Action.UP -> "上滑"
        Action.DOWN -> "下滑"
        Action.STOP -> "停止"
        Action.COPY -> "复制"
    }
}
