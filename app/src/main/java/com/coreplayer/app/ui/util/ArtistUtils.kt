package com.coreplayer.app.ui.util

private val PATRONYMIC_REGEX = Regex(".*(ович|евич|ич|овна|евна|ична|инична)$", RegexOption.IGNORE_CASE)
private val CYRILLIC_WORD = Regex("^[А-Яа-яЁё\\-]+$")

/**
 * Checks whether a given string is a Russian legal full name (ФИО: Фамилия Имя Отчество).
 */
fun isFio(part: String): Boolean {
    val words = part.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (words.size in 2..4) {
        val hasPatronymic = words.any { PATRONYMIC_REGEX.matches(it) }
        val allCyrillic = words.all { CYRILLIC_WORD.matches(it) }
        if (hasPatronymic && allCyrillic) return true
    }
    return false
}

/**
 * Strips out legal patronymic full names (ФИО) that APIs or ID3 tags sometimes attach
 * to musical artist credits.
 * E.g. "madk1d • Серков Марк Робертович" -> "madk1d"
 */
fun cleanArtistName(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    val trimmed = raw.trim()
    if (isFio(trimmed)) return ""

    // Check delimiter •
    if (trimmed.contains("•")) {
        val parts = trimmed.split("•").map { it.trim() }.filter { it.isNotBlank() }
        val filtered = parts.filterNot { isFio(it) }
        if (filtered.isNotEmpty()) {
            return filtered.joinToString(" • ")
        }
    }

    // Check delimiter /
    if (trimmed.contains("/")) {
        val parts = trimmed.split("/").map { it.trim() }.filter { it.isNotBlank() }
        val filtered = parts.filterNot { isFio(it) }
        if (filtered.isNotEmpty()) {
            return filtered.joinToString(", ")
        }
    }

    // Check delimiter ,
    if (trimmed.contains(",")) {
        val parts = trimmed.split(",").map { it.trim() }.filter { it.isNotBlank() }
        val filtered = parts.filterNot { isFio(it) }
        if (filtered.isNotEmpty()) {
            return filtered.joinToString(", ")
        }
    }

    // Check delimiter ;
    if (trimmed.contains(";")) {
        val parts = trimmed.split(";").map { it.trim() }.filter { it.isNotBlank() }
        val filtered = parts.filterNot { isFio(it) }
        if (filtered.isNotEmpty()) {
            return filtered.joinToString(", ")
        }
    }

    return trimmed
}
