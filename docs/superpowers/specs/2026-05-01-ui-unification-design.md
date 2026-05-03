# UI Unification Design

**Date:** 2026-05-01 | **Status:** Approved

## Summary

Standardize all UI interfaces of Daily Satori to achieve unified style, unified operations, and a beautiful, simple appearance. Keep Material 3 design language and current blue primary color.

## Design System (Unchanged)

- **Style:** Material 3
- **Primary color:** `#5E8BFF` (blue)
- **Font:** Lato
- **Theme:** Light/Dark auto with full color inversion
- **Spacing:** Existing tokens (xxs=2, xs=4, s=8, m=16, l=24, xl=32, xxl=48)

## 1. Operation Standardization

### Back/Close
| Scenario | Pattern |
|----------|---------|
| Sub-page (navigation) | Top-left back arrow → `popBackStack()` |
| Bottom sheet/drawer | Drag handle + swipe-down + top-right ✕ button |
| Dialog | Cancel/Confirm buttons + tap scrim to dismiss |

### List Item Operations
| Scenario | Pattern |
|----------|---------|
| Editable/deletable | Long-press context menu (edit/delete), delete requires confirm |
| Favoritable/shareable | Right-side icon buttons (☆ favorite, ↑ share) |
| Multi-select | Checkbox on left, batch action bar on top |

### Empty/Loading States
| State | Component |
|-------|-----------|
| Empty list | `EmptyState(icon, title, subtitle, action?)` centered |
| Loading | `LoadingIndicator` centered, optional semi-transparent overlay |
| Error | `EmptyState` variant with error message + retry button |

## 2. Visual Polish

### Spacing
- All pages: `horizontal = Spacing.m(16dp)`, `vertical = Spacing.s(8dp)`
- Card gap: `Spacing.s(8dp)`
- List items: `listItem` height (56dp)

### Typography Hierarchy
| Usage | Style |
|-------|-------|
| Page title | `headlineMedium` (24sp, bold) |
| Section header | `titleMedium` (16sp, medium) |
| Body text | `bodyMedium` (15sp) |
| Secondary text | `bodySmall` (13sp, `onSurfaceVariant`) |
| Labels/chips | `labelMedium` (12sp) |

### Card Standards
- Corner radius: `Radius.m` (12dp)
- Internal padding: `Spacing.m` (16dp)
- Divider: `outline` color, `BorderWidth.xs` (0.5dp)

### Animation
- Click feedback: opacity `animateFloatAsState`
- Page transition: 350ms slide+fade (existing, keep)
- Bottom sheet: 300ms expand

## 3. Screen Improvements

### HomeScreen
- Unified bottom nav icon sizing, selected uses `primary` fill
- Add light page switch transition

### Article
- ArticleListScreen: search bar unified to `OutlinedTextField` + `circular` radius
- ArticleDetailScreen: tab indicator → `primary`, normalize Markdown spacing

### Diary
- DiaryScreen: tag filter → inline chip row (replace ModalBottomSheet)
- DiaryEditorSheet: add drag handle + close button per standard

### Books
- BooksScreen: book selection → standard `ModalBottomSheet`
- ViewpointCard: unify style with other cards

### AI Chat
- MessageBubble: unify corner radius and color for user/AI bubbles
- ChatInputBar: unify input height/radius with other inputs

### Settings
- All rows → `SettingsRow` component
- Sub-page back → `BackHandler` + top back arrow

### Global
- All search bars → same style (radius, height, icon)
- All confirmations → `ConfirmDialog`
- Dark mode contrast audit

## Implementation Order

1. Standardize base components (EmptyState, LoadingIndicator, ConfirmDialog)
2. Standardize operation patterns (back/close, list interactions)
3. Visual polish across all screens
4. Screen-specific fixes
5. Dark mode audit
6. Compile and verify
