package me.inthefield.gitbutlerforjetbrains.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ButCommandsTest {

    @Test
    fun status_buildsFormatJsonArgs() {
        assertEquals(listOf("status", "--format", "json"), ButCommands.status())
    }

    @Test
    fun push_buildsBranchArgs() {
        assertEquals(listOf("push", "feature-a", "--format", "json"), ButCommands.push("feature-a"))
    }

    @Test
    fun apply_buildsBranchArgs() {
        assertEquals(listOf("apply", "feature-a", "--format", "json"), ButCommands.apply("feature-a"))
    }

    @Test
    fun unapply_buildsBranchArgs() {
        assertEquals(listOf("unapply", "feature-a", "--format", "json"), ButCommands.unapply("feature-a"))
    }

    @Test
    fun commit_joinsCliIdsWithCommas() {
        assertEquals(
            listOf("commit", "feature-a", "-m", "add hello", "--changes", "aa,bb", "--format", "json"),
            ButCommands.commit("feature-a", "add hello", listOf("aa", "bb")),
        )
    }

    @Test
    fun commit_singleCliId_hasNoTrailingComma() {
        assertEquals(
            listOf("commit", "feature-a", "-m", "msg", "--changes", "aa", "--format", "json"),
            ButCommands.commit("feature-a", "msg", listOf("aa")),
        )
    }
}
