package me.inthefield.gitbutlerforjetbrains.core

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.PROJECT)
class GitButlerService(private val project: Project) {

    @Volatile
    private var cachedExecutable: String? = null

    /**
     * True while a workspace mutation started from the branch menu is running; callers
     * gate on it (compareAndSet before starting, set(false) when finished) so `but`
     * mutations never overlap.
     */
    val mutationInFlight = AtomicBoolean(false)

    /**
     * True iff some git repository of this project has current branch named exactly
     * "gitbutler/workspace". Cheap — no CLI call.
     */
    fun isGitButlerWorkspace(): Boolean {
        return repositories().any { it.currentBranchName == WORKSPACE_BRANCH }
    }

    /**
     * Absolute path to the `but` binary or null. Search order: PATH entries, then
     * ~/.local/bin/but, /opt/homebrew/bin/but, /usr/local/bin/but. Cached after first success.
     */
    fun butExecutable(): String? {
        cachedExecutable?.let { return it }

        val candidates = mutableListOf<File>()
        System.getenv("PATH")
            ?.split(File.pathSeparatorChar)
            ?.filter { it.isNotBlank() }
            ?.forEach { candidates.add(File(it, BINARY_NAME)) }

        val home = System.getProperty("user.home")
        if (!home.isNullOrBlank()) {
            candidates.add(File(home, ".local/bin/$BINARY_NAME"))
        }
        candidates.add(File("/opt/homebrew/bin/$BINARY_NAME"))
        candidates.add(File("/usr/local/bin/$BINARY_NAME"))

        val found = candidates.firstOrNull { it.isFile && it.canExecute() }?.absolutePath
        if (found != null) {
            cachedExecutable = found
        }
        return found
    }

    /**
     * Runs `but status --format json`. Must NOT be called on the EDT. Err with a readable
     * message when binary missing, exit != 0, or parse failure.
     */
    fun status(): ButResult<WorkspaceStatus> {
        assertBackgroundThread()

        val repoRoot = repoRoot() ?: return ButResult.Err("No git repository found for this project")
        val exe = butExecutable() ?: return ButResult.Err(BINARY_MISSING_MESSAGE)

        val output = when (val r = runBut(exe, repoRoot, ButCommands.status())) {
            is ButResult.Ok -> r.value
            is ButResult.Err -> return r
        }

        if (output.exitCode != 0) {
            return ButResult.Err(ButJsonParser.parseErrorMessage(output.stdout.ifBlank { output.stderr }))
        }

        return try {
            ButResult.Ok(ButJsonParser.parseStatus(output.stdout))
        } catch (e: Exception) {
            ButResult.Err("Failed to parse `but status` output: ${e.message}")
        }
    }

    /** Runs `but push <branchName> --format json`. Ok on exit 0. Not on EDT. */
    fun push(branchName: String): ButResult<Unit> {
        assertBackgroundThread()

        val repoRoot = repoRoot() ?: return ButResult.Err("No git repository found for this project")
        val exe = butExecutable() ?: return ButResult.Err(BINARY_MISSING_MESSAGE)

        val output = when (
            val r = runBut(exe, repoRoot, ButCommands.push(branchName), PUSH_TIMEOUT_MS)
        ) {
            is ButResult.Ok -> r.value
            is ButResult.Err -> return r
        }

        if (output.exitCode != 0) {
            return ButResult.Err(ButJsonParser.parseErrorMessage(output.stdout.ifBlank { output.stderr }))
        }

        return ButResult.Ok(Unit)
    }

    /**
     * Runs `but pull --format json`. Fetches the remote and rebases all applied branches onto
     * the updated target — a network operation, so uses [PUSH_TIMEOUT_MS]. Ok on exit 0. Not on EDT.
     */
    fun pull(): ButResult<Unit> {
        assertBackgroundThread()

        val repoRoot = repoRoot() ?: return ButResult.Err("No git repository found for this project")
        val exe = butExecutable() ?: return ButResult.Err(BINARY_MISSING_MESSAGE)

        val output = when (
            val r = runBut(exe, repoRoot, listOf("pull", "--format", "json"), PUSH_TIMEOUT_MS)
        ) {
            is ButResult.Ok -> r.value
            is ButResult.Err -> return r
        }

        if (output.exitCode != 0) {
            return ButResult.Err(ButJsonParser.parseErrorMessage(output.stdout.ifBlank { output.stderr }))
        }

        return ButResult.Ok(Unit)
    }

    /** Runs `but apply <branchName> --format json`. Ok on exit 0. Not on EDT. */
    fun apply(branchName: String): ButResult<Unit> {
        assertBackgroundThread()

        val repoRoot = repoRoot() ?: return ButResult.Err("No git repository found for this project")
        val exe = butExecutable() ?: return ButResult.Err(BINARY_MISSING_MESSAGE)

        val output = when (
            val r = runBut(exe, repoRoot, ButCommands.apply(branchName))
        ) {
            is ButResult.Ok -> r.value
            is ButResult.Err -> return r
        }

        if (output.exitCode != 0) {
            return ButResult.Err(ButJsonParser.parseErrorMessage(output.stdout.ifBlank { output.stderr }))
        }

        return ButResult.Ok(Unit)
    }

    /** Runs `but unapply <branchName> --format json`. Ok on exit 0. Not on EDT. */
    fun unapply(branchName: String): ButResult<Unit> {
        assertBackgroundThread()

        val repoRoot = repoRoot() ?: return ButResult.Err("No git repository found for this project")
        val exe = butExecutable() ?: return ButResult.Err(BINARY_MISSING_MESSAGE)

        val output = when (
            val r = runBut(exe, repoRoot, ButCommands.unapply(branchName))
        ) {
            is ButResult.Ok -> r.value
            is ButResult.Err -> return r
        }

        if (output.exitCode != 0) {
            return ButResult.Err(ButJsonParser.parseErrorMessage(output.stdout.ifBlank { output.stderr }))
        }

        return ButResult.Ok(Unit)
    }

    /**
     * Commits the given files to the named virtual branch:
     * 1. run status(); map each path to its cliId via uncommittedChanges
     * 2. Err listing the offending paths if any path has no cliId
     * 3. run `but commit <branchName> -m <message> --changes <ids>` and parse. Not on EDT.
     */
    fun commit(branchName: String, message: String, filePaths: List<String>): ButResult<String> {
        assertBackgroundThread()

        if (filePaths.isEmpty()) {
            return ButResult.Err("No files selected to commit")
        }

        val repoRoot = repoRoot() ?: return ButResult.Err("No git repository found for this project")
        val exe = butExecutable() ?: return ButResult.Err(BINARY_MISSING_MESSAGE)

        val statusResult = status()
        val workspace = when (statusResult) {
            is ButResult.Ok -> statusResult.value
            is ButResult.Err -> return statusResult
        }

        val mapResult = ButPathMapper.map(repoRoot, filePaths, workspace.uncommittedChanges)

        if (mapResult.missing.isNotEmpty()) {
            return ButResult.Err("No uncommitted change found for: ${mapResult.missing.joinToString(", ")}")
        }

        val output = when (
            val r = runBut(exe, repoRoot, ButCommands.commit(branchName, message, mapResult.cliIds))
        ) {
            is ButResult.Ok -> r.value
            is ButResult.Err -> return r
        }

        if (output.exitCode != 0) {
            return ButResult.Err(ButJsonParser.parseErrorMessage(output.stdout.ifBlank { output.stderr }))
        }

        // Exit code 0 means the commit succeeded; the JSON is only enrichment
        // (commit id, rejected changes). Its shape varies between workspaces,
        // so parse failures must not surface as commit failures.
        return try {
            val parsed = ButJsonParser.parseCommitResult(output.stdout)
            if (parsed is ButResult.Ok && parsed.value.isBlank()) {
                LOG.warn("`but commit` exited 0 but output had no result/commit_id. Raw output: ${output.stdout}")
            }
            parsed
        } catch (e: Exception) {
            LOG.warn("`but commit` exited 0 but output was unparseable: ${e.message}. Raw output: ${output.stdout}")
            ButResult.Ok("")
        }
    }

    private fun runBut(
        exe: String,
        repoRoot: String,
        args: List<String>,
        timeoutMs: Int = PROCESS_TIMEOUT_MS,
    ): ButResult<ProcessOutput> {
        val output = try {
            val cmd = GeneralCommandLine(exe)
            cmd.addParameters(args)
            cmd.workDirectory = File(repoRoot)
            cmd.environment["BUT_OUTPUT_FORMAT"] = "json"
            CapturingProcessHandler(cmd).runProcess(timeoutMs)
        } catch (e: Exception) {
            return ButResult.Err("Failed to run GitButler CLI: ${e.message}")
        }
        if (output.isTimeout) {
            return ButResult.Err("`but ${args.firstOrNull().orEmpty()}` timed out after ${timeoutMs / 1000}s")
        }
        return ButResult.Ok(output)
    }

    private fun repositories(): List<GitRepository> =
        GitRepositoryManager.getInstance(project).repositories

    /** The repository this plugin operates on: prefer the one on gitbutler/workspace, else the first. */
    fun workspaceRepository(): GitRepository? {
        val repos = repositories()
        return repos.firstOrNull { it.currentBranchName == WORKSPACE_BRANCH } ?: repos.firstOrNull()
    }

    private fun repoRoot(): String? = workspaceRepository()?.root?.path

    private fun assertBackgroundThread() {
        ApplicationManager.getApplication().assertIsNonDispatchThread()
    }

    companion object {
        private val LOG = Logger.getInstance(GitButlerService::class.java)
        private const val WORKSPACE_BRANCH = "gitbutler/workspace"
        private const val BINARY_NAME = "but"
        private const val PROCESS_TIMEOUT_MS = 30_000
        private const val PUSH_TIMEOUT_MS = 120_000
        private const val BINARY_MISSING_MESSAGE =
            "GitButler CLI (`but`) not found on PATH, in ~/.local/bin, /opt/homebrew/bin, or /usr/local/bin"

        fun getInstance(project: Project): GitButlerService = project.service()
    }
}
