package com.enapi.gitbutler.core

import java.io.File
import java.io.IOException

/** Maps IDE-selected file paths to GitButler cliIds. Pure Kotlin + java.io — no IntelliJ APIs. */
object ButPathMapper {
    data class MapResult(val cliIds: List<String>, val missing: List<String>)

    /** filePaths may be absolute or repo-relative. Matching must survive macOS symlinked
     *  roots (/tmp -> /private/tmp): canonicalize both repoRoot and absolute inputs
     *  (File.canonicalPath, falling back to absolutePath on IOException) before relativizing.
     *  Separators normalized to '/'. Order of cliIds follows filePaths. */
    fun map(repoRoot: String, filePaths: List<String>, changes: List<UncommittedChange>): MapResult {
        // One filePath can carry several cliIds (e.g. hunks of a file split between the
        // unassigned area and a stack's assigned changes) — commit all of them, or the
        // result silently contains less than the user selected.
        val byPath = changes.groupBy { it.filePath }

        val cliIds = mutableListOf<String>()
        val missing = mutableListOf<String>()
        for (path in filePaths) {
            val relative = toRepoRelative(repoRoot, path)
            val matches = byPath[relative]
            if (matches.isNullOrEmpty()) {
                missing.add(path)
            } else {
                matches.mapTo(cliIds) { it.cliId }
            }
        }
        return MapResult(cliIds = cliIds, missing = missing)
    }

    private fun toRepoRelative(repoRoot: String, path: String): String {
        val file = File(path)
        if (!file.isAbsolute) {
            return normalizeSeparators(path)
        }
        val rootPath = canonical(File(repoRoot))
        val absolute = canonical(file)
        val relative = when {
            absolute == rootPath -> ""
            absolute.startsWith(rootPath + File.separator) -> absolute.substring(rootPath.length + 1)
            else -> absolute
        }
        return normalizeSeparators(relative)
    }

    private fun canonical(file: File): String =
        try {
            file.canonicalPath
        } catch (_: IOException) {
            file.absolutePath
        }

    private fun normalizeSeparators(path: String): String =
        if (File.separatorChar == '/') path else path.replace(File.separatorChar, '/')
}
