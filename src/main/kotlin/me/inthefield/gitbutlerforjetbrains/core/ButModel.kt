package me.inthefield.gitbutlerforjetbrains.core

data class VirtualBranch(val cliId: String, val name: String)

data class UncommittedChange(val cliId: String, val filePath: String, val changeType: String)

data class WorkspaceStatus(
    val uncommittedChanges: List<UncommittedChange>,
    val branches: List<VirtualBranch>,
)

sealed class ButResult<out T> {
    data class Ok<T>(val value: T) : ButResult<T>()
    data class Err(val message: String) : ButResult<Nothing>()
}
