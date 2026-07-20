package me.inthefield.gitbutlerforjetbrains.core

import com.google.gson.JsonSyntaxException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ButJsonParserTest {

    private val fullStatusJson = """
        {
          "uncommittedChanges": [
            {"cliId": "zm", "filePath": "one.txt", "changeType": "added"},
            {"cliId": "pp", "filePath": "two.txt", "changeType": "added"}
          ],
          "stacks": [
            {"cliId": "i0", "assignedChanges": [], "branches": [
              {"cliId": "on", "name": "feature-one", "commits": [], "upstreamCommits": [], "branchStatus": "completelyUnpushed", "reviewId": null, "ci": null}
            ]},
            {"cliId": "j0", "assignedChanges": [], "branches": [
              {"cliId": "tw", "name": "feature-two", "commits": [], "upstreamCommits": [], "branchStatus": "completelyUnpushed", "reviewId": null, "ci": null}
            ]}
          ],
          "mergeBase": {"cliId": "", "commitId": "a58a", "message": "init\n"},
          "upstreamState": {"behind": 0}
        }
    """.trimIndent()

    @Test
    fun parseStatus_fullSample_yieldsBranchesAndChanges() {
        val status = ButJsonParser.parseStatus(fullStatusJson)

        assertEquals(2, status.branches.size)
        assertEquals(VirtualBranch("on", "feature-one"), status.branches[0])
        assertEquals(VirtualBranch("tw", "feature-two"), status.branches[1])

        assertEquals(2, status.uncommittedChanges.size)
        assertEquals(UncommittedChange("zm", "one.txt", "added"), status.uncommittedChanges[0])
        assertEquals(UncommittedChange("pp", "two.txt", "added"), status.uncommittedChanges[1])
    }

    @Test
    fun parseStatus_assignedChanges_appearInUncommittedChanges() {
        val json = """
            {
              "uncommittedChanges": [
                {"cliId": "zm", "filePath": "one.txt", "changeType": "added"}
              ],
              "stacks": [
                {"cliId": "i0", "assignedChanges": [
                  {"cliId": "aa", "filePath": "assigned.txt", "changeType": "modified"}
                ], "branches": [
                  {"cliId": "on", "name": "feature-one"}
                ]}
              ]
            }
        """.trimIndent()

        val status = ButJsonParser.parseStatus(json)

        assertEquals(2, status.uncommittedChanges.size)
        assertTrue(
            "expected assigned.txt in uncommittedChanges",
            status.uncommittedChanges.any { it.cliId == "aa" && it.filePath == "assigned.txt" },
        )
    }

    @Test
    fun parseStatus_emptyCollections_yieldEmptyLists() {
        val json = """
            {"uncommittedChanges": [], "stacks": []}
        """.trimIndent()

        val status = ButJsonParser.parseStatus(json)

        assertTrue(status.branches.isEmpty())
        assertTrue(status.uncommittedChanges.isEmpty())
    }

    @Test
    fun parseCommitResult_success_returnsOkWithCommitId() {
        val json = """
            {"result": {"commit_id": "059d24", "branch": "feature-one", "branch_tip": "059d24", "rejected": []}, "status": {}}
        """.trimIndent()

        val result = ButJsonParser.parseCommitResult(json)

        assertTrue(result is ButResult.Ok)
        assertEquals("059d24", (result as ButResult.Ok).value)
    }

    @Test
    fun parseCommitResult_nonEmptyRejected_returnsErrMentioningPath() {
        val json = """
            {"result": {"commit_id": "059d24", "branch": "feature-one", "rejected": [
              {"cliId": "zz", "filePath": "locked.txt", "changeType": "modified"}
            ]}, "status": {}}
        """.trimIndent()

        val result = ButJsonParser.parseCommitResult(json)

        assertTrue(result is ButResult.Err)
        assertTrue(
            "expected rejected path in message",
            (result as ButResult.Err).message.contains("locked.txt"),
        )
    }

    @Test
    fun parseCommitResult_missingResultField_isSuccessWithUnknownId() {
        // Exit code 0 is the success signal; an output shape without "result"
        // must not be reported as a failed commit.
        val json = """{"status": {"uncommittedChanges": [], "stacks": []}}"""

        val result = ButJsonParser.parseCommitResult(json)

        assertTrue(result is ButResult.Ok)
        assertEquals("", (result as ButResult.Ok).value)
    }

    @Test
    fun parseCommitResult_errorJson_returnsErrWithMessage() {
        val json = """
            {"error": "setup_required", "message": "No GitButler project found at .", "hint": "run `but setup` to configure the project"}
        """.trimIndent()

        val result = ButJsonParser.parseCommitResult(json)

        assertTrue(result is ButResult.Err)
        assertEquals("No GitButler project found at .", (result as ButResult.Err).message)
    }

    @Test
    fun parseErrorMessage_setupRequiredSample_returnsMessage() {
        val json = """
            {"error": "setup_required", "message": "No GitButler project found at .", "hint": "run `but setup` to configure the project"}
        """.trimIndent()

        assertEquals("No GitButler project found at .", ButJsonParser.parseErrorMessage(json))
    }

    @Test
    fun parseErrorMessage_onlyErrorField_fallsBackToError() {
        val json = """{"error": "boom"}"""
        assertEquals("boom", ButJsonParser.parseErrorMessage(json))
    }

    @Test
    fun parseErrorMessage_rawOnUnparseable_returnsInput() {
        val raw = "not json at all"
        assertEquals(raw, ButJsonParser.parseErrorMessage(raw))
    }

    @Test
    fun parseStatus_malformedJson_throws() {
        try {
            ButJsonParser.parseStatus("{ this is : not valid")
            fail("expected an exception on malformed JSON")
        } catch (e: JsonSyntaxException) {
            // documented behavior: parseStatus propagates Gson's exception; the service wraps it in Err
        } catch (e: IllegalStateException) {
            // also acceptable when the root is not a JSON object
        }
    }
}
