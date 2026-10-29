package com.dailysatori.core.service

import kotlin.test.*

class WebEditVersionTest {
    @Test fun separatesNullEmptyAndDelimiters() {
        assertNotEquals(webEditVersion(null), webEditVersion(""))
        assertNotEquals(webEditVersion("a", "b:c"), webEditVersion("a:b", "c"))
        assertEquals(webEditVersion(1L, "note"), webEditVersion(1L, "note"))
        assertNotEquals(webEditVersion(1L, "phone"), webEditVersion(1L, "web"))
    }
}
