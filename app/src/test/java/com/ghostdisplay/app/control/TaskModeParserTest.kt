package com.ghostdisplay.app.control

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TaskModeParserTest {

    @Test
    fun parsesOpenSiteAndClickPlay() {
        val steps = TaskModeParser.parse("open site example.com and click play")
        assertThat(steps.map { it.kind }).contains(TaskModeParser.Kind.OPEN_URL_HINT)
        assertThat(steps.any { it.kind == TaskModeParser.Kind.OPEN_URL_HINT && it.argument.contains("example.com") }).isTrue()
        assertThat(steps.any { it.kind == TaskModeParser.Kind.CLICK_TEXT && it.argument.contains("play") }).isTrue()
    }

    @Test
    fun parsesType() {
        val steps = TaskModeParser.parse("type hello world")
        assertThat(steps).hasSize(1)
        assertThat(steps[0].kind).isEqualTo(TaskModeParser.Kind.SET_TEXT)
        assertThat(steps[0].argument).isEqualTo("hello world")
    }

    @Test
    fun parsesScrollDown() {
        val steps = TaskModeParser.parse("scroll down")
        assertThat(steps[0].kind).isEqualTo(TaskModeParser.Kind.SCROLL_FORWARD)
    }

    @Test
    fun emptyIsEmpty() {
        assertThat(TaskModeParser.parse("  ")).isEmpty()
    }

    @Test
    fun fallbackClickWholePhrase() {
        val steps = TaskModeParser.parse("Login")
        assertThat(steps[0].kind).isEqualTo(TaskModeParser.Kind.CLICK_TEXT)
        assertThat(steps[0].argument).isEqualTo("Login")
    }
}
