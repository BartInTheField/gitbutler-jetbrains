package me.inthefield.gitbutlerforjetbrains.core

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Pure JSON parsing for the GitButler `but` CLI output. No IntelliJ APIs, so these
 * functions are directly unit-testable.
 *
 * Malformed / unexpected JSON: [parseStatus] and [parseCommitResult] let the underlying
 * Gson exception (e.g. [com.google.gson.JsonSyntaxException] / [IllegalStateException])
 * propagate. Callers (the service) are responsible for turning that into an Err.
 * [parseErrorMessage] never throws — it falls back to the raw input.
 */
object ButJsonParser {

    /**
     * Parses `but status --format json`. branches = all branches of all stacks, in order
     * (the same [VirtualBranch] instances that appear under [WorkspaceStatus.stacks]).
     * uncommittedChanges also includes every stack's assignedChanges entries; each stack's
     * assignedChanges are ADDITIONALLY exposed on [ButStack.assignedChanges] (the same
     * [UncommittedChange] instances appear in both the flat list and on the stack).
     *
     * Each branch carries its parsed commits and branchStatus (missing branchStatus -> "").
     * Fields like changes, reviewId, ci, upstreamCommits, mergeBase and upstreamState are ignored.
     */
    fun parseStatus(json: String): WorkspaceStatus {
        val root = JsonParser.parseString(json).asJsonObject

        val uncommitted = mutableListOf<UncommittedChange>()
        root.getAsJsonArray("uncommittedChanges")?.forEach { element ->
            parseChange(element)?.let { uncommitted.add(it) }
        }

        val branches = mutableListOf<VirtualBranch>()
        val stacks = mutableListOf<ButStack>()
        root.getAsJsonArray("stacks")?.forEach { stackElement ->
            val stack = stackElement.asJsonObject
            val assigned = mutableListOf<UncommittedChange>()
            stack.getAsJsonArray("assignedChanges")?.forEach { element ->
                parseChange(element)?.let {
                    uncommitted.add(it)
                    assigned.add(it)
                }
            }
            val stackBranches = mutableListOf<VirtualBranch>()
            stack.getAsJsonArray("branches")?.forEach { branchElement ->
                val branch = branchElement.asJsonObject
                stackBranches.add(parseBranch(branch))
            }
            branches.addAll(stackBranches)
            stacks.add(ButStack(cliId = stringOrEmpty(stack, "cliId"), branches = stackBranches, assignedChanges = assigned))
        }

        return WorkspaceStatus(uncommittedChanges = uncommitted, branches = branches, stacks = stacks)
    }

    /**
     * Parses `but commit --format json` output. Ok(commitId) on success.
     * Err if the JSON has an "error" field or result.rejected is non-empty.
     *
     * The CLI signals failure via a JSON "error" field plus a non-zero exit code,
     * so a shape without "result"/"commit_id" is NOT treated as failure — the
     * output shape has been observed to vary between workspaces. Such cases
     * return Ok("") (commit succeeded, id unknown); the caller logs the raw output.
     */
    fun parseCommitResult(json: String): ButResult<String> {
        val root = JsonParser.parseString(json).asJsonObject

        if (root.has("error")) {
            return ButResult.Err(parseErrorMessage(json))
        }

        val result = root.getAsJsonObject("result") ?: return ButResult.Ok("")

        val rejected = result.getAsJsonArray("rejected")
        if (rejected != null && rejected.size() > 0) {
            val paths = rejected.joinToString(", ") { renderRejected(it) }
            return ButResult.Err("Commit rejected changes: $paths")
        }

        return ButResult.Ok(stringOrEmpty(result, "commit_id"))
    }

    /** Extracts "message" (fallback "error", fallback raw) from an error JSON. Never throws. */
    fun parseErrorMessage(json: String): String {
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            val message = optString(root, "message")
            if (message != null) {
                return message
            }
            val error = optString(root, "error")
            error ?: json
        } catch (_: Exception) {
            json
        }
    }

    private fun parseChange(element: JsonElement): UncommittedChange? {
        if (!element.isJsonObject) {
            return null
        }
        val obj = element.asJsonObject
        return UncommittedChange(
            cliId = stringOrEmpty(obj, "cliId"),
            filePath = stringOrEmpty(obj, "filePath"),
            changeType = stringOrEmpty(obj, "changeType"),
        )
    }

    private fun parseBranch(obj: JsonObject): VirtualBranch {
        val commits = mutableListOf<ButCommit>()
        obj.getAsJsonArray("commits")?.forEach { element ->
            if (element.isJsonObject) {
                commits.add(parseCommit(element.asJsonObject))
            }
        }
        return VirtualBranch(
            cliId = stringOrEmpty(obj, "cliId"),
            name = stringOrEmpty(obj, "name"),
            commits = commits,
            branchStatus = stringOrEmpty(obj, "branchStatus"),
        )
    }

    private fun parseCommit(obj: JsonObject): ButCommit {
        return ButCommit(
            cliId = stringOrEmpty(obj, "cliId"),
            commitId = stringOrEmpty(obj, "commitId"),
            message = stringOrEmpty(obj, "message"),
            authorName = stringOrEmpty(obj, "authorName"),
            createdAt = stringOrEmpty(obj, "createdAt"),
            conflicted = optBoolean(obj, "conflicted"),
        )
    }

    private fun renderRejected(element: JsonElement): String {
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            optString(obj, "filePath")?.let { return it }
        }
        if (element.isJsonPrimitive) {
            return element.asString
        }
        return element.toString()
    }

    /** Reads a boolean; null / missing / non-boolean -> false. */
    private fun optBoolean(obj: JsonObject, key: String): Boolean {
        val element = obj.get(key) ?: return false
        if (element.isJsonNull) {
            return false
        }
        return element.isJsonPrimitive && element.asJsonPrimitive.isBoolean && element.asBoolean
    }

    private fun stringOrEmpty(obj: JsonObject, key: String): String = optString(obj, key) ?: ""

    private fun optString(obj: JsonObject, key: String): String? {
        val element = obj.get(key) ?: return null
        if (element.isJsonNull) {
            return null
        }
        return element.asString
    }
}
