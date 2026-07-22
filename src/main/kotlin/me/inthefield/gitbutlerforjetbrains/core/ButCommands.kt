package me.inthefield.gitbutlerforjetbrains.core

/**
 * Builds the exact `but` argument lists the plugin runs. Pure Kotlin — shared with the
 * integration tests so they execute the same commands the plugin does.
 */
object ButCommands {
    fun status(): List<String> = listOf("status", "--format", "json")

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
}
