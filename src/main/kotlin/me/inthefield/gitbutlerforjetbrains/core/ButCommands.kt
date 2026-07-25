package me.inthefield.gitbutlerforjetbrains.core

/**
 * Builds the exact `but` argument lists the plugin runs. Pure Kotlin — shared with the
 * integration tests so they execute the same commands the plugin does.
 */
object ButCommands {
    fun status(): List<String> = listOf("status", "-f", "--format", "json")

    fun push(branchName: String): List<String> = listOf("push", branchName, "--format", "json")

    fun apply(branchName: String): List<String> = listOf("apply", branchName, "--format", "json")

    fun unapply(branchName: String): List<String> = listOf("unapply", branchName, "--format", "json")

    fun commit(branchName: String, message: String, cliIds: List<String>): List<String> =
        listOf(
            "commit",
            branchName,
            "-m",
            message,
            "--changes",
            cliIds.joinToString(","),
            "--format",
            "json",
        )

    fun amend(commitId: String, cliIds: List<String>): List<String> =
        listOf("amend", commitId, "--changes", cliIds.joinToString(","), "--format", "json")

    /** [id] is a commit id or a file-in-commit id (e.g. `tp:x`) to uncommit a single file. */
    fun uncommit(id: String): List<String> = listOf("uncommit", id, "--format", "json")

    fun reword(commitId: String, message: String): List<String> =
        listOf("reword", commitId, "-m", message, "--format", "json")
}
