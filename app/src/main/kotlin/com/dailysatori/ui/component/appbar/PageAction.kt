package com.dailysatori.ui.component.appbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.dailysatori.R
import com.dailysatori.ui.theme.*

enum class PageActionPriority { Primary, Secondary }

/** Named page actions share one right-hand slot; task-local tools belong in the editor body. */
data class PageAction(
    val label: String,
    val icon: ImageVector? = null, // Omit for a direct text action.
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val priority: PageActionPriority = PageActionPriority.Secondary,
    /**
     * Explicit opt-in: keep this secondary action visible in the top bar even when the page has several
     * actions. Default false keeps the existing "share one overflow" rule for every other page.
     */
    val alwaysVisible: Boolean = false,
    val menuContent: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
    val iconSize: Dp = if (icon == MoreActionsIcon) IconSize.l else IconSize.m,
    val onClick: () -> Unit,
) {
    fun perform() { if (enabled) onClick() }
}

data class PageActionLayout(val direct: List<PageAction>, val overflow: List<PageAction>)

fun arrangePageActions(actions: List<PageAction>): PageActionLayout {
    require(actions.none { it.priority == PageActionPriority.Primary } || actions.size == 1) {
        "An editor must expose only its primary action; move secondary tools into the task area"
    }
    require(actions.none { it.priority == PageActionPriority.Primary && it.menuContent != null }) {
        "A primary action must execute directly, never open an overflow menu"
    }
    return if (actions.size <= 1) {
        PageActionLayout(actions, emptyList())
    } else {
        val direct = actions.filter { it.alwaysVisible }
        PageActionLayout(direct, actions.filterNot { it.alwaysVisible })
    }
}

@Composable
fun PageActions(actions: List<PageAction>) {
    val layout = arrangePageActions(actions)
    layout.direct.forEach { action ->
        val icon = action.icon
        if (action.menuContent != null) CustomPageActionMenu(action)
        else if (action.priority == PageActionPriority.Primary || icon == null) {
            TextButton(onClick = action::perform, enabled = action.enabled,
                modifier = Modifier.sizeIn(minWidth = Height.touchTarget, minHeight = Height.touchTarget)) {
                Text(action.label)
            }
        } else IconButton(onClick = action::perform, enabled = action.enabled, modifier = Modifier.size(Height.touchTarget)) {
            Icon(icon, action.label, tint = actionColor(action), modifier = Modifier.size(action.iconSize))
        }
    }
    if (layout.overflow.isNotEmpty()) PageActionMenu(layout.overflow)
}

/** Existing task-specific popups keep their contents, but never own extra header buttons. */
@Composable
private fun CustomPageActionMenu(action: PageAction) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        val icon = action.icon ?: MoreActionsIcon
        IconButton(onClick = { expanded = true }, enabled = action.enabled, modifier = Modifier.size(Height.touchTarget)) {
            Icon(icon, action.label, tint = actionColor(action), modifier = Modifier.size(if (icon == MoreActionsIcon) IconSize.l else IconSize.m))
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            action.menuContent?.invoke { expanded = false }
        }
    }
}

@Composable
private fun PageActionMenu(actions: List<PageAction>) {
    var expanded by remember { mutableStateOf(false) }
    var submenu by remember { mutableStateOf<PageAction?>(null) }
    Box {
        IconButton(onClick = { submenu = null; expanded = true }, modifier = Modifier.size(Height.touchTarget)) {
            Icon(MoreActionsIcon, stringResource(R.string.page_actions_more),
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(IconSize.l))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            val nested = submenu?.menuContent
            if (nested != null) nested { expanded = false; submenu = null }
            else actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.label, color = actionColor(action)) },
                    leadingIcon = action.icon?.let { icon ->
                        { Icon(icon, null, tint = actionColor(action), modifier = Modifier.size(if (icon == MoreActionsIcon) IconSize.l else IconSize.m)) }
                    },
                    enabled = action.enabled,
                    modifier = Modifier.heightIn(min = Height.touchTarget),
                    onClick = {
                        if (action.menuContent != null) submenu = action
                        else { expanded = false; action.perform() }
                    },
                )
            }
        }
    }
}

@Composable
private fun actionColor(action: PageAction) = when {
    !action.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    action.destructive -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
