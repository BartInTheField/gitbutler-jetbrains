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
     * Parses `but status -f --json`. branches = all branches of all stacks, in order
     * (the same [VirtualBranch] instances that appear under [WorkspaceStatus.stacks]).
     * uncommittedChanges also includes every stack's assignedChanges entries; each stack's
     * assignedChanges are ADDITIONALLY exposed on [ButStack.assignedChanges] (the same
     * [UncommittedChange] instances appear in both the flat list and on the stack).
     *
     * Each branch carries its parsed commits and branchStatus (missing branchStatus -> "").
     * Fields like reviewId, ci, upstreamCommits, mergeBase and upstreamState are ignored.
     * Each commit carries its parsed changes (missing "changes" -> emptyList()); branch-level
     * "changes" fields, if any, are not parsed here.
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
     * Parses `but commit --json` output. Ok(commitId) on success.
     *
     * but 0.22 emits `{commitId, changeId, branch}` on success and signals failure via a
     * non-zero exit code plus a plain-text stderr message (handled by the caller), so this
     * only runs on exit 0. A shape without `commitId` returns Ok("") (commit succeeded,
     * id unknown); the caller logs the raw output. A defensive `error` field still maps to Err.
     */
    fun parseCommitResult(json: String): ButResult<String> {
        val root = JsonParser.parseString(json).asJsonObject

        if (root.has("error")) {
            return ButResult.Err(parseErrorMessage(json))
        }

        return ButResult.Ok(stringOrEmpty(root, "commitId"))
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
        val changes = mutableListOf<UncommittedChange>()
        val changesElement = obj.get("changes")
        if (changesElement != null && changesElement.isJsonArray) {
            changesElement.asJsonArray.forEach { element ->
                parseChange(element)?.let { changes.add(it) }
            }
        }
        return ButCommit(
            cliId = stringOrEmpty(obj, "cliId"),
            commitId = stringOrEmpty(obj, "commitId"),
            message = stringOrEmpty(obj, "message"),
            authorName = stringOrEmpty(obj, "authorName"),
            createdAt = stringOrEmpty(obj, "createdAt"),
            conflicted = optBoolean(obj, "conflicted"),
            changes = changes,
        )
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
