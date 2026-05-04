# Backup Repair Design

## Goal

Make backup and restore usable and consistent. Users can choose a backup directory, set an encryption password, trigger an immediate backup, and rely on one scheduled backup per day. All backup creation and restoration paths use the same encrypted file format.

## Current Problems

- The settings screen stores `backup_directory`, but backup creation writes to the app-private `FileManager.getBackupDir()` path instead.
- The restore screen expects an old unencrypted folder layout, while the current backup service creates `.zip.enc` files.
- There is no active daily backup scheduling in the Kotlin app.
- Backup and restore fall back to the hardcoded password `daily_satori_backup`.
- The UI does not provide working directory selection or backup password management.

## Requirements

- Directory selection must use Android's system directory picker and persist read/write URI permission.
- Immediate backup and daily scheduled backup must call the same backup creation logic.
- Backups must be written to the selected user directory.
- Backups must be encrypted `.zip.enc` files.
- Backup creation must require a user-configured encryption password of at least 10 characters.
- There must be no default backup encryption password.
- Backup filenames must include a password hint containing the last three characters of the password used to create that backup.
- Restoring a backup must ask the user for the password for the selected file, because historical files may use older passwords.
- Restore UI must display the filename-derived password hint when available.
- Current backup password should be stored locally encrypted with Android Keystore so scheduled backup can run without user interaction.
- Password input fields should support Android Autofill so 1Password can fill them through the platform instead of the app directly reading 1Password data.

## Non-Goals

- Do not implement direct 1Password token/API secret retrieval on Android unless official app-to-app support is found later.
- Do not support unencrypted backups.
- Do not migrate old unencrypted folder backups in this change.
- Do not add cloud sync.

## Architecture

### Backup Directory Access

The settings screen launches `ActivityResultContracts.OpenDocumentTree`. On success, the app calls `takePersistableUriPermission` with read and write flags, stores the URI string in `backup_directory`, and displays it in the settings UI.

File operations that target the backup directory will use Android `DocumentFile`/content resolver APIs on Android. App-private source files remain read through existing local file APIs.

### Password Storage

Introduce a small Android-only secure password component backed by Android Keystore. It stores the current backup password encrypted in app storage and exposes:

- `saveBackupPassword(password)`
- `getBackupPassword()`
- `hasBackupPassword()`

The UI enforces `password.length >= 10`. The backup service refuses to create backups without a stored valid password.

### Backup Creation

`BackupService.backupNow()` becomes the single backup creation path for manual and scheduled backups. It validates selected directory URI and stored password, creates a temporary zip in app-private cache/files storage, encrypts it with the current password, and writes the final encrypted file into the selected directory.

The filename format is:

`daily_satori_backup_<timestamp>_hint_<last3>.zip.enc`

After the encrypted file is written, temporary files are deleted. Retention can keep the most recent 10 encrypted backups in the selected directory, matching existing behavior.

### Scheduled Backup

Add a `BackupWorker` using WorkManager. The app registers a unique periodic work request with a 24-hour interval. Registration happens on app startup and after backup settings are saved. The worker exits without failure if directory or password is missing, and otherwise calls the same backup service path.

### Restore Flow

The restore screen lists `.enc` files from the selected directory. It parses `_hint_<last3>` from the filename and displays it as a password hint. When the user selects a file and taps restore, the app prompts for that file's password. The entered password is passed to `BackupService.restore(name, password)` and is not replaced by the current configured password.

Restore decrypts the selected encrypted file to a temporary zip, extracts it, and restores the database plus image directories using the existing canonical backup content layout.

### 1Password Integration Position

The app will not attempt to read 1Password vault data directly. Current 1Password developer APIs are oriented around CLI, SDKs, service accounts, and automation environments; they do not provide a clearly supported Android third-party app token flow for reading a user's local vault item.

Instead, password fields will be configured for Android Autofill/password managers. Users can store the backup password in 1Password and fill it into the app through the Android system autofill UI.

## UI Changes

- Backup settings screen shows selected directory status and a button to choose/change directory.
- Backup settings screen adds backup password entry and save action.
- Backup button validates both selected directory and saved password before running.
- Restore screen lists encrypted backup files from the selected directory.
- Restore screen shows password hint from filename.
- Restore action opens a password prompt for the selected backup file.

## Error Handling

- Missing directory: show `请先选择备份目录`.
- Missing password: show `请先设置备份密码`.
- Password shorter than 10 characters: show `备份密码至少需要 10 位`.
- Lost directory permission: prompt user to choose the backup directory again.
- Wrong restore password or corrupted file: show restore failure without deleting the backup file.

## Testing And Verification

- Add or update unit tests for filename generation and password hint parsing.
- Add or update restore listing behavior tests where practical.
- Run `./gradlew :app:compileDebugKotlin` after implementation.
- Run install verification with `JAVA_HOME=/home/jimxl/.local/share/jdk-21.0.6 ./gradlew :app:installDebug` when a device is available, then launch the app.
