package ru.stasoft.mynotes.data

/** Слова запроса: все должны встретиться (в названии или тексте), регистр не важен. */
fun searchWords(q: String): List<String> = q.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }

fun searchNotes(all: List<NoteEntity>, q: String, type: Int, age: Int): List<NoteEntity> {
    val words = searchWords(q)
    if (words.isEmpty()) return emptyList()
    val day = 24L * 3600 * 1000
    val since = when (age) {
        1 -> System.currentTimeMillis() - 7 * day
        2 -> System.currentTimeMillis() - 30 * day
        3 -> System.currentTimeMillis() - 365 * day
        else -> 0L
    }
    return all.asSequence()
        .filter { type == 0 || (type == 1 && !it.isFolder) || (type == 2 && it.isFolder) }
        .filter { it.updatedAt >= since }
        .filter { n ->
            val hay = (n.title + "\n" + n.body).lowercase()
            words.all { it in hay }
        }
        .take(300)
        .toList()
}

/** Фрагмент текста вокруг первого найденного слова. */
fun searchSnippet(body: String, q: String, radius: Int = 40): String? {
    val words = searchWords(q)
    val low = body.lowercase()
    val pos = words.map { low.indexOf(it) }.filter { it >= 0 }.minOrNull() ?: return null
    val from = (pos - radius).coerceAtLeast(0)
    val to = (pos + radius * 2).coerceAtMost(body.length)
    val piece = body.substring(from, to).replace(Regex("\\s+"), " ")
    return (if (from > 0) "…" else "") + piece + (if (to < body.length) "…" else "")
}
