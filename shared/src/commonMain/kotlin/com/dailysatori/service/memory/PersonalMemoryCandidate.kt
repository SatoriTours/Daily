package com.dailysatori.service.memory

/** Untrusted model output; the assistant indexer validates original evidence and source version. */
data class PersonalMemoryCandidate(val kind: String, val content: String, val quote: String)
