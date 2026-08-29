# AI Reminder Notifications Design

## Goal

Let a user tell the AI assistant when and what to remember, review an editable reminder card, and receive Android notifications until the reminder is completed or its final active day ends.

## Product rules

- AI creates a draft only. Scheduling starts only after the user confirms the card.
- A single-day reminder expires permanently at 24:00 on its target date, even if incomplete.
- A multi-day reminder starts a fresh daily cycle on each configured active date and expires permanently after the final date.
- Completing a reminder ends the entire reminder, including remaining dates.
- Swiping a notification away never completes it.
- Default sleep quiet hours are 00:00–09:00 every day. No notification, sound, or vibration occurs in this interval. Missed repeats are not replayed; at 09:00 the scheduler emits at most one currently due reminder.
- Default work hours are Monday–Friday, 09:00–18:00. Notifications remain visible but sound and vibration are forcibly disabled during this interval.
- From 22:00 until 24:00, the strong preset overrides daytime backoff and reminds hourly. Nothing is scheduled at or after 24:00 for that daily cycle.
- All time-of-day rules use the device's current local timezone. A timezone change preserves local wall-clock intent.

## User flow

1. The user says, for example, “下周三下午六点提醒我还信用卡，连续三天”.
2. The AI assistant invokes a `create_reminder_draft` tool with structured fields. It cannot persist or schedule the reminder.
3. The app shows an editable confirmation card containing content, absolute dates, first reminder time, active-day rule, reminder profile, sound/vibration/privacy overrides, quiet-hour rules, and daily cutoff.
4. Missing or ambiguous required fields are highlighted and confirmation is disabled.
5. The user confirms, modifies, or cancels the draft.
6. Confirmation persists an immutable reminder/profile snapshot and schedules only the next occurrence.
7. The notification offers “已完成” and “查看”. Swiping triggers a delete callback that advances backoff without completing the reminder.

Natural-language dates such as “tomorrow” are resolved using the current device timezone, but the card always displays the resulting absolute dates and times before confirmation.

## Reminder profiles and settings

The app provides a unified Reminder Settings screen with:

- global default profile;
- sleep quiet hours (default every day 00:00–09:00);
- work days and work hours (default Monday–Friday 09:00–18:00);
- default sound, vibration, notification importance, lock-screen content visibility;
- daytime dismissal backoff;
- evening reinforcement start, interval, and daily cutoff;
- profile creation, editing, duplication, and deletion.

Built-in profiles:

| Profile | Daytime behavior after swipe | 22:00–24:00 | Sound |
|---|---|---|---|
| Strong | 2h, then 4h, then remain at 4h | hourly | enabled outside work/sleep rules |
| Standard | 2h, then 4h | at 22:00 and 23:00 | enabled outside work/sleep rules |
| Gentle | no backoff repeat | once at 22:00 | silent by default |
| Custom | user-defined | user-defined | user-defined |

Every confirmed reminder stores a profile snapshot. Later profile edits do not silently alter existing reminders. A reminder detail action may explicitly apply the latest profile. Card-level overrides affect only that reminder.

## Scheduling state machine

Each reminder has one persisted state owner. The scheduler computes and enqueues only the next occurrence.

States:

- `DRAFT`: not scheduled.
- `ACTIVE`: eligible for future notification.
- `NOTIFIED`: notification posted; if untouched, the next reminder is one hour later.
- `DISMISSED`: delete callback recorded; next daytime interval advances to 2h, then 4h, capped at 4h.
- `PAUSED`: no work scheduled until resumed.
- `COMPLETED`: terminal.
- `EXPIRED`: terminal after the final active date cutoff.

Transition rules:

- Untouched notification: schedule one hour later while inside an eligible window.
- Swipe: atomically record dismissal/backoff and replace the pending schedule.
- At 22:00 under Strong: ignore daytime backoff and schedule hourly until before 24:00.
- Entering sleep hours: schedule the next eligible wake at 09:00, never each missed interval.
- At a daily cutoff: move to the next configured active date and reset that day's backoff; after the final date, expire.
- Complete, pause, edit, delete, timezone change, reboot, notification-permission change, and exact-alarm-permission change all cancel/recompute the single pending occurrence.

All receiver and worker paths re-read state and perform an idempotent compare-and-update before posting, so duplicate broadcasts cannot produce duplicate notifications.

## Android delivery architecture

Use a hybrid next-occurrence scheduler:

1. If exact alarms are supported and `AlarmManager.canScheduleExactAlarms()` is true, schedule the next occurrence with an exact alarm.
2. Otherwise use a one-time WorkManager request as a graceful fallback and display in settings that timing may be delayed by battery optimizations.
3. Never require a permanent foreground service. A user who allows the app to remain resident benefits naturally, but correctness does not depend on residency.
4. A boot/time/timezone receiver rehydrates active reminders and schedules their next occurrence.
5. A notification `deleteIntent` records swipes. Action intents use immutable, reminder-specific PendingIntents.
6. Notification permission, exact-alarm access, and channel availability are visible in Reminder Settings with direct recovery actions.

Android's exact-alarm special access is optional: the app checks on return from system settings and degrades to an inexact/windowed or WorkManager path when unavailable. Notification channels are separated by behavior where Android channel immutability requires distinct sound/importance settings.

## Data model

Persist dedicated reminder tables rather than overloading generic async tasks:

- `reminder`: content, status, local start/end dates, local first time, active-day rule, timezone behavior, profile snapshot, current day, backoff level, last notification/dismissal/completion timestamps, next occurrence, created/updated timestamps.
- `reminder_profile`: name, built-in/custom type, backoff sequence, evening reinforcement configuration, sound/vibration/importance/visibility, created/updated timestamps.
- `reminder_event`: reminder ID, event type, scheduled/actual time, metadata without private notification content. Retain a bounded history for diagnostics.

Scheduling identity uses the reminder ID. Notification identity also uses the reminder ID so repeats update one notification instead of flooding the tray.

## AI tool contract

`create_reminder_draft` accepts only structured, bounded fields:

- content;
- local start/end date;
- local first reminder time;
- active-day rule (`daily`, `weekdays`, selected weekdays, or consecutive date range);
- profile ID or suggested built-in profile;
- optional per-reminder overrides.

The tool returns a draft ID and display model. It performs no database write to active reminders and no scheduling. Invalid ranges, past cutoffs, missing time/date, unsupported recurrence, and ambiguous timezone input remain editable validation errors. Repeated delivery never calls AI, so hourly reminders create no AI cost.

## Error handling and safety

- If notification permission is denied, preserve the reminder, show an in-app warning, and do not claim it will notify.
- If exact-alarm access is denied, use fallback scheduling without repeatedly prompting.
- If a notification channel is disabled, show the affected profile in settings.
- If a receiver/worker runs late, post at most one notification if the reminder is still eligible; otherwise compute the next valid occurrence.
- Editing a reminder uses optimistic state/version checks so a concurrent Complete action wins over a stale reschedule.
- Notification content follows the configured lock-screen visibility; sensitive content can show a generic “你有一项待办提醒”.
- Logs contain reminder IDs and state transitions, never full reminder content.

## UI surfaces

- AI chat confirmation card with editable fields and validation.
- Reminder list with Active, Paused, Completed, and Expired filters.
- Reminder detail/edit screen with pause, resume, apply latest profile, complete, and delete.
- Unified Reminder Settings and profile editor.
- Notification actions: Complete and View; swipe is Dismiss only.

## Testing and acceptance

Unit tests cover:

- local date/time resolution, DST gaps/overlaps, timezone changes;
- daily cutoff, multi-day rollover, work-hour silence, sleep suppression, 09:00 recovery;
- untouched hourly repeats, swipe 2h→4h backoff, 22:00 override, completion/expiry terminal behavior;
- profile snapshot semantics and per-reminder overrides;
- idempotent duplicate delivery and stale-generation rejection;
- AI draft validation and confirmation-only persistence.

Android tests cover AlarmManager selection, WorkManager fallback, PendingIntent identity, delete/action receivers, notification permission/channel states, reboot/time-change restoration, and exact-alarm access changes.

Device acceptance covers a real notification swipe, Complete action, locked-screen privacy, sound/vibration inside and outside work hours, sleep suppression, exact-alarm denied fallback, reboot restoration, and a compressed-clock end-to-end run of the backoff/evening state machine.

## Non-goals for the first release

- Cloud/server push reminders.
- Cross-device reminder synchronization.
- Location-based reminders.
- Permanent foreground service.
- AI silently creating or modifying active reminders without confirmation.
