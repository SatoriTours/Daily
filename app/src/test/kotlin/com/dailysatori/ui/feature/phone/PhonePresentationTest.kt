package com.dailysatori.ui.feature.phone

import com.dailysatori.core.bookkeeping.BookkeepingCaptureStatus
import com.dailysatori.service.phone.*
import com.dailysatori.ui.feature.bookkeeping.BookkeepingUiState
import kotlin.test.Test
import kotlin.test.assertEquals

class PhonePresentationTest {
    @Test fun smsNeedsBothReceptionAndSystemPermission() {
        val state = PhoneUiState(preferences = PhonePreferences(PhoneOptions(enabled = true), PhoneOptions(), emptySet()))
        assertEquals("permission", phoneChannelStatus(state, PhoneChannel.SMS))
        assertEquals("connected", phoneChannelStatus(state.copy(smsGranted = true), PhoneChannel.SMS))
        assertEquals("off", phoneChannelStatus(PhoneUiState(smsGranted = true), PhoneChannel.SMS))
    }

    @Test fun authorizedSmsWithoutPurposeIsNotConnected() {
        val state = PhoneUiState(smsGranted = true, preferences = PhonePreferences(
            PhoneOptions(enabled = true, todos = false, ledger = false), PhoneOptions(), emptySet()))
        assertEquals("purpose", phoneChannelStatus(state, PhoneChannel.SMS))
    }

    @Test fun notificationsNeedAccessSourcesAndAConnectedListener() {
        val state = PhoneUiState(preferences = PhonePreferences(PhoneOptions(), PhoneOptions(enabled = true), emptySet()))
        assertEquals("permission", phoneChannelStatus(state, PhoneChannel.NOTIFICATION))
        val authorized = state.copy(bookkeeping = BookkeepingUiState(granted = true))
        assertEquals("sources", phoneChannelStatus(authorized, PhoneChannel.NOTIFICATION))
        val selected = authorized.copy(preferences = authorized.preferences.copy(sources = setOf("bank")))
        assertEquals("waiting", phoneChannelStatus(selected, PhoneChannel.NOTIFICATION))
        val connected = selected.copy(bookkeeping = selected.bookkeeping.copy(capture = BookkeepingCaptureStatus(connected = true)))
        assertEquals("connected", phoneChannelStatus(connected, PhoneChannel.NOTIFICATION))
    }
}
