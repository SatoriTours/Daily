# AI Chat Icon Polish Design

## Scope

Polish three existing visual details without changing navigation, chat behavior, persistence, or data flow.

## Design

- AI chat messages reverse their current alignment: user messages align left, assistant messages align right.
- Chat bubble corner emphasis follows the new side: the user bubble gets a left-side tail, and the assistant bubble gets a right-side tail.
- The bottom AI tab uses a sparkle-style AI icon (`AutoAwesome`) instead of the robot icon to better communicate intelligent assistant behavior.
- The launcher clock outer circle stroke is reduced from `8` to `6` for a lighter icon while preserving visibility at launcher size.

## Implementation Notes

- Update only the existing Compose UI and vector drawable files.
- Continue using the existing theme tokens and Material icon set.
- Do not alter database schema, chat state, or message ordering.

## Verification

- Run `./gradlew :app:compileDebugKotlin` after edits.
- Install and launch with the project Android commands if a device is available.
