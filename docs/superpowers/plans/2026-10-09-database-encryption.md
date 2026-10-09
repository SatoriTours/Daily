# 数据库整库加密与便携备份实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 将活动数据库统一改为 SQLCipher 加密，取消字段重复加密，并实现沿用数据库密钥的跨设备恢复。

**Architecture:** SQLDelight 查询层保持不变，Android 使用 SQLCipher 工厂。随机数据库密钥由 Keystore 包装；初始化转换与恢复将数据库和包装密钥作为同一文件事务提交。便携备份携带受用户密码保护的原数据库密钥，新设备仅重新包装，不整库换密钥。

**Tech Stack:** Kotlin Multiplatform、SQLDelight 2.1.0、Android Keystore、SQLCipher Android、kotlinx.serialization、现有 Gradle/JVM 单元测试。

**Spec:** `docs/superpowers/specs/2026-10-09-database-encryption-design.md`（用户已确认）。

## Global Constraints

- 当前工作区顺序实施；不创建 worktree、不覆盖其他会话改动、不自动提交/合并/推送。实现前确认最新 HEAD，计划核查基线为 `fad370da`，与规格初查基线之间的其他功能必须保留。
- 数据库密钥为 32 字节随机值；包装使用 Android Keystore AES-256/GCM，文件名 `database_key.sec`；缺失/损坏不得覆盖或静默建空库。
- 使用 `net.zetetic:sqlcipher-android` / `net.zetetic.database.sqlcipher.SupportOpenHelperFactory`，支持 minSdk 26 和 Android 16 KB 内存页；不使用旧 `android-database-sqlcipher`。
- 新格式清单版本 2，便携密钥文件 `database_key.json`；raw key 编码固定为 `raw-256-hex`、64 位十六进制字符串、SQLCipher compatibility 4。
- DSB3：PBKDF2-HMAC-SHA256 600,000 次，AES-CTR + 独立 HMAC-SHA256；DSB2 和历史 GCM 保持 10,000 次派生读取。
- 不修改数据库 Schema、历史列名或 API 请求协议；只有确实变更表/列才按项目规则增加 Schema 版本与迁移。
- 资料文件仍用真实 Cipher，附件本地保护方式不改变；禁止全局 Cipher 空操作实现。
- 日常不启动模拟器、不运行 UI/安装测试。原生依赖改动最终合并编译、APK 构建与模块测试为一次调用。
- 中间每个任务仅执行聚焦 RED/GREEN；最后一次集中审查与必要完整验证。用户未要求提交；若随后授权提交，必须先编译成功并检查 staged diff。

## Review Focus

1. 数据库已存在但密码文件/Keystore alias 丢失：不能生成替代密码或新空库；任务 1/2 覆盖。
2. 同时存在中断的转换事务和待恢复任务：先处理转换事务，再应用恢复，不能把恢复后的库还原成转换前旧库；任务 2/5 覆盖。
3. 明文旧库存在已提交 WAL，或 `user_version=0`：不能漏数据、创建重复表或丢失版本；任务 2/7 覆盖。
4. 便携密钥文件被缺失、调包、扩大、格式更改，或出现在普通附件目录：严格拒绝且不得导出其他本地密钥；任务 5 覆盖。
5. DSB3 头被改成巨大派生次数、未来算法或遭截断：有限解析并拒绝，不能无限消耗 CPU 或暴露未认证 ZIP；任务 4 覆盖。

---

## 文件与接口约定

新文件仅按明确职责拆分，不为任务数量机械拆分：

| 文件 | 职责 |
|---|---|
| `shared/src/commonMain/kotlin/com/dailysatori/service/security/DatabaseKey.kt` | 密钥值对象、便携格式、校验与安全显示 |
| `shared/src/commonMain/kotlin/com/dailysatori/service/security/DatabaseKeyStore.kt` | 平台密钥包装接口 |
| `shared/src/androidMain/kotlin/com/dailysatori/service/security/DatabaseKeyStore.android.kt` | Keystore 包装及私有文件读取 |
| `shared/src/androidMain/kotlin/com/dailysatori/service/security/DatabaseInstallTransaction.kt` | 仅切换数据库、边车和密钥的转换事务；不得操作附件 |
| `shared/src/androidMain/kotlin/com/dailysatori/service/security/DatabaseEncryptionMigration.kt` | 旧明文库导出、字段转换、验证与提交 |
| `shared/src/androidMain/kotlin/com/dailysatori/service/security/DatabaseBootstrap.kt` | 启动前锁、事务恢复、恢复应用、旧库转换顺序 |
| `shared/src/androidMain/kotlin/com/dailysatori/service/backup/BackupFileCipher.kt` | 无 Context 的流式 DSB3/DSB2/历史 GCM 编解码 |
| `scripts/test-database-encryption.py` | 主机 SQLCipher 实际文件加密/导出探针 |
| `scripts/check-sqlcipher-apk.py` | 检查 APK 内 SQLCipher ABI 与 ELF 对齐 |

既有 `DatabaseDriverFactory`、`SecretFieldProcessor`、`BackupManifest`、`BackupService`、`BackupRestoreTransaction`、`FileManager`、Repository 和启动/诊断文件按任务原位修改。

跨任务接口：

- `DatabaseKey.fromHex(value: String): DatabaseKey`：严格接受 64 位十六进制并规范为小写；`generate()` 调用同文件声明的 `internal expect fun secureDatabaseKeyBytes(): ByteArray`，Android actual 在 `DatabaseKeyStore.android.kt` 中使用 SecureRandom 返回 32 字节，禁止使用 Kotlin 普通 Random；`sqlCipherPassword(): ByteArray` 返回 SQLCipher raw-key 表达的 UTF-8 字节；`portableJson(): String` / `fromPortableJson(value: String): DatabaseKey` 处理版本 1、`raw-256-hex`、compatibility 4。`toString()` 永不显示密钥，不使用会默认显示密钥的 data class。
- `expect class DatabaseKeyStore(context: PlatformContext)`：`readExisting(): DatabaseKey?`、`wrap(key: DatabaseKey): ByteArray`、`storagePath(): String`（仅返回私有包装文件路径，供文件事务使用）。`readExisting()` 无文件返回 null，有文件但解密失败抛安全类型错误，绝不创建 alias；`wrap()` 可为新安装/恢复创建包装 alias，但不替换活动文件。
- `DatabaseDriverFactory.createDriver()`：仅打开已准备的活动加密库；保留 `createDriver(name: String)` 作为同一活动密钥的兼容入口，禁止用它打开外来恢复库。
- 新增 `createEncryptedDriver(name: String, key: DatabaseKey): SqlDriver`、`createLegacyDriver(name: String): SqlDriver`；后者要求目标已存在且确认为明文，使用 SQLCipher 无密码连接以支持显式加密导出。
- `createBackupDriver(name: String, key: DatabaseKey?): SqlDriver`：仅检查现有副本，不新建 Schema；null 仅表示已识别的历史明文备份。`createInMemoryDriver()` 保持测试/Schema 参照用途。
- Android `DatabaseBootstrap.prepare(context: PlatformContext): String?`：获取统一文件锁，恢复转换事务，应用 pending restore，保证活动库已加密；返回现有恢复提示，失败阻止业务初始化。
- `DatabaseEncryptionMigration.ensureEncrypted(): Unit`：只处理活动库转换；`convertLegacy(source: String, destination: String, key: DatabaseKey): Unit` 用于历史备份副本，无活动文件副作用。
- `SecretFieldProcessor.decryptLegacyFields(): SecretProcessingResult`：已存在表/列的严格、参数化转换，不清空失败字段；保留历史 Cipher 及独立资料使用。
- `BackupFileCipher.encrypt(input: File, output: File, password: String, progress: (Double) -> Unit)` / `decrypt(...)`：成功才发布输出，失败清理临时输出，密码和含密钥内容不进入异常文本。

接口增补与测试替身在同一任务更新，避免 common/actual 声明不一致。

### Task 1：密钥生命周期与显式 SQLCipher 驱动

**Files:** 创建上表前 3 个文件；修改 `shared/src/commonMain/kotlin/com/dailysatori/platform/DatabaseDriverFactory.kt`、对应 Android actual、`gradle/libs.versions.toml`、`shared/build.gradle.kts`。

**Tests:** 新增 `shared/src/commonTest/kotlin/com/dailysatori/service/security/DatabaseKeyTest.kt`、`shared/src/androidUnitTest/kotlin/com/dailysatori/service/security/DatabaseKeyStoreTest.kt`。

**Interfaces:** 产出 `DatabaseKey`、`DatabaseKeyStore` 与显式加密/旧格式工厂接口，供任务 2/5/6 使用。

- [x] **1. 写失败测试。** 固定 `00..1f` 密钥的 raw-key 表达与便携往返一致；空、63/65 字符、非 hex、未来版本、compatibility 非 4 均拒绝；`toString()` 不包含 hex。可注入包装算法/文件后验证两个不同包装密钥、缺失文件、损坏文件、不存在 alias、包装失败；读取失败不调用生成 alias，wrap 无论成功失败均不修改活动密钥文件。
- [x] **2. RED。** `./gradlew :shared:testDebugUnitTest --tests '*DatabaseKeyTest' --tests '*DatabaseKeyStoreTest'`；预期新增接口/行为未实现失败。
- [x] **3. 实现密钥与驱动。** AndroidStore 的私有路径统一由自身常量提供，读取无副作用，密文版式为版本 1、12 字节 IV、GCM 128 位 tag；包裹过程只返回 bytes。`System.loadLibrary("sqlcipher")` 只加载一次，所有新连接给独立 raw-key 字节副本；SQL/key 日志关闭，外键行为不回归。官方查询显示候选版本 `4.19.1@aar`，在依赖解析阶段核对该稳定版 AAR/POM、minSdk 和构造签名后锁定；若不匹配停止并查官方源，不按搜索示例盲改 AndroidX。
- [x] **4. GREEN。** 同一聚焦命令通过，核对新增驱动无“打开失败就明文重试”分支；真实 JNI 不由 Mock 测试冒充验证，留任务 7。

### Task 2：启动前旧库转换与密码同事务安装

**Files:** 创建 `DatabaseInstallTransaction.kt`、`DatabaseEncryptionMigration.kt`、`DatabaseBootstrap.kt`；修改 `shared/src/commonMain/kotlin/com/dailysatori/service/security/SecretFieldProcessor.kt`、`app/src/main/kotlin/com/dailysatori/DailySatoriApplication.kt`。

**Tests:** 新增 `shared/src/androidUnitTest/kotlin/com/dailysatori/service/security/DatabaseInstallTransactionTest.kt`、`DatabaseEncryptionMigrationTest.kt`；扩展 `shared/src/commonTest/kotlin/com/dailysatori/service/security/SecretFieldProcessorTest.kt`。

**Interfaces:** 消费任务 1；产出 Bootstrap、`convertLegacy(...)`、`decryptLegacyFields()`；转换事务暴露 `stage(database: File, wrappedKey: ByteArray)`、`applyPending(): Unit`，只涉及数据库、密钥和边车。

- [x] **1. 写失败测试。** 各文件 move 阶段抛异常/模拟进程死亡，再启动必须得到完整旧库+旧密钥或完整新库+新密钥；恢复完成后清理失败不可重放回滚。无论转换成功/失败均不修改附件。旧字段全注册表转换、可选表/列缺失、任何不可解密字段中止；版本 0、旧业务版本、索引/触发器保留和 WAL 已提交数据由主机探针补证。初始化测试固定顺序：转换事务回收 → pending restore → ensureEncrypted → DI/Schema/Worker，含并存事务情况。
- [x] **2. RED。** `./gradlew :shared:testDebugUnitTest --tests '*DatabaseInstallTransactionTest' --tests '*DatabaseEncryptionMigrationTest' --tests '*SecretFieldProcessorTest'`。
- [x] **3. 实现转换与状态机。** 使用进程启动前 `FileChannel.lock()`，锁文件在私有 noBackup 目录。显式 `ATTACH ... KEY ...` + `sqlcipher_export()` 导出加密候选，不修改旧字段源；复制 `user_version`，候选严格转换后校验、关闭并重新打开。事务日志仅含文件名/阶段，不含密钥；flush 载荷后发布 ready，回滚保留可重试状态，commit 后先原子退休目录再清理。与恢复共享锁，不调用会删除附件的恢复事务来转换数据库。Bootstrap 接入早于 Koin；密钥/库缺一、密码错误或数据损坏一律中止，不自动建库。唯一新安装分支是在库、密钥、暂存/事务均不存在时，创建带 Schema 的加密候选库，并仍通过同一安装事务提交，不能先发布密码再建活动库。
- [x] **4. GREEN。** 同一聚焦测试通过；检查新安装允许生成密钥的前置条件排除了遗留数据库、密钥与事务载荷。

### Task 3：取消数据库字段重复加密

**Files:** 修改 `shared/src/commonMain/kotlin/com/dailysatori/data/repository/` 下 `AIConfigRepository.kt`、`McpServerRepository.kt`、`RemoteNewsSourceRepository.kt`、`ExternalFavoriteSourceRepository.kt`、`SkillConfigRepository.kt`、`SmsSourceRepository.kt`、`PhoneMessageRepository.kt`、`BookkeepingRepository.kt`；修改 `shared/src/commonMain/kotlin/com/dailysatori/service/diary/SpeechSettingsService.kt`、`service/book/WeReadSkillService.kt`、`service/migration/DatabaseMigration.kt`、`di/SharedModule.kt`、`app/src/main/kotlin/com/dailysatori/DailySatoriApplication.kt` 及受影响构造测试。

**Tests:** 修改 `shared/src/commonTest/kotlin/com/dailysatori/service/security/SecretStorageSourceTest.kt`、`di/SecretCipherDiTest.kt`、现有 Speech/WeRead/Skill 和短信测试；扩展 `shared/src/androidUnitTest/kotlin/com/dailysatori/service/bookkeeping/BookkeepingStorageTest.kt`，新增 `DatabaseCredentialStorageTest.kt`（同 security 测试目录）。

**Interfaces:** 消费任务 2 的一次性字段转换；Repository 公共业务方法不变，但构造不再依赖 Cipher。真实 `SecretCipher` 仍提供资料/旧格式兼容。

- [x] **1. 写失败测试。** JDBC 测试库中直接查询 Token、语音配置、短信/账目/手机消息 JSON，与调用方原值/模型一致；重新打开与业务 decode 往返不变。微信读书历史配置迁移不生成新 `enc:v1:`。确认资料文件仍通过真正 Cipher 加密，禁止将全局 `SecretValueCipher` 绑定到恒等实现。改源码约束测试为新安全不变量，不仅删除旧断言。
- [x] **2. RED。** `./gradlew :shared:testDebugUnitTest --tests '*DatabaseCredentialStorageTest' --tests '*BookkeepingStorageTest' --tests '*SecretStorageSourceTest' --tests '*SecretCipherDiTest' --tests '*SpeechSettingsTest' --tests '*WeReadSkillServiceTest' --tests '*SkillRegistryTest'`。
- [x] **3. 实现普通字段读写。** 删除数据库 Cipher 参数/函数回调及调用点，保留输入规范化、序列化和错误语义；去掉 `encryptStoredSecrets()` 启动扫描。旧设置转入新表前由任务 2 先严格解密，Schema 迁移不能重新加密。保留历史列名，仅更新语义注释。更新现有构造测试，不使用兼容空操作参数拖延迁移。
- [x] **4. GREEN。** 同一命令通过；按精确 `rg` 检查数据库层及 Schema 迁移不再调用 encrypt/decrypt，资料和历史转换不受影响。

### Task 4：DSB3 认证流式备份外层

**Files:** 创建 `shared/src/androidMain/kotlin/com/dailysatori/service/backup/BackupFileCipher.kt`，修改 `shared/src/androidMain/kotlin/com/dailysatori/platform/FileManager.android.kt` 的 encrypt/decrypt 委托；扩展 `shared/src/androidUnitTest/kotlin/com/dailysatori/platform/FileManagerEncryptionTest.kt`。

**Interfaces:** 产出已约定 `BackupFileCipher.encrypt/decrypt`；FileManager 公共签名与进度接口不变，任务 5 无须识别外层算法。

- [x] **1. 写失败测试。** 新写入 magic 为 DSB3，空文件/跨 buffer/大文件往返正确；用固定密文 fixture 验证 DSB2 与历史 GCM，而不是用新 writer 生成所谓旧数据。覆盖错误密码、每段头/密文/tag 篡改、截断、目标已有文件、取消/IO 异常；未认证输出不发布，已有目标保持原样。
- [x] **2. RED。** `./gradlew :shared:testDebugUnitTest --tests '*FileManagerEncryptionTest'`。
- [x] **3. 实现固定格式。** DSB3 头为 ASCII `DSB3`、1 字节 KDF id=1、4 字节 big-endian iterations=600000、16 字节 salt、16 字节 CTR IV；尾部 HMAC-SHA256 为 32 字节。PBKDF2 输出 512 位，前/后 32 字节分别为 AES/MAC key，头全部纳入 HMAC。读 DSB3 仅接受该 KDF 与 600000 次，未来参数拒绝，检查最小长度后派生；不得按攻击者次数直接执行。认证后原子发布临时 ZIP，使用常量时间 tag 比较，失败清理。保留 DSB2 和 GCM 的历史派生，不改变旧 magic 语义；无 Context 以便 JVM 真 crypto 测试。
- [x] **4. GREEN。** 同一命令通过，测试不通过放大堆或一次性读取整个文件绕开流式处理；确认 Cancelled 异常仍传播。

### Task 5：加密快照、便携密钥与数据库/密码联合恢复

**Files:** 修改 `shared/src/commonMain/kotlin/com/dailysatori/service/backup/BackupManifest.kt`、`BackupService.kt`、`BackupDatabaseData.kt`、`SqliteBackupSnapshot.kt`；修改 `shared/src/androidMain/kotlin/com/dailysatori/service/backup/BackupRestoreTransaction.kt`、`shared/src/androidMain/kotlin/com/dailysatori/platform/FileManager.android.kt` 与 common expect FileManager、相关 DI。

**Tests:** 扩展 common `BackupServiceTest.kt`，Android unit 的 `BackupRestoreTransactionTest.kt`、`BackupRoundTripTest.kt`、`BackupDatabaseDataTest.kt`、`DiaryThreadBackupTest.kt`、`BackupVerificationDiagnosticsTest.kt`；新增 `BackupPortableKeyTest.kt`（同 Android backup 测试目录）。

**Interfaces:** 消费任务 1/2/4；备份服务公共 API 与 UI 不变。内部 `BackupSecrets` 重命名为 `BackupDatabaseAccess`，方法统一为 `exportSnapshot(destination: String): DatabaseKey`、`openPortableDatabase(path: String, key: DatabaseKey?): SqlDriver`、`convertLegacyDatabase(path: String): DatabaseKey`、`wrapRestoredKey(key: DatabaseKey): ByteArray`；现有 summary/relocate/requiredFiles 方法显式携带 `DatabaseKey`，禁止暗用活动密钥。FileManager 去掉隐式 snapshot 职责，保留文件 IO。

- [x] **1. 写失败测试。** 新 backup 内容包括加密库、密钥、资料/附件、manifest v2；无设备包装密钥文件。两个不同设备 wrapping key 恢复：数据库密钥一致，包装密文不同，恢复路径不调用 `convertLegacy`/整库导出。缺密钥、密钥格式错误、错误密钥、manifest SHA 不匹配、额外文件、路径穿越、用户目录中的密钥文件均拒绝且不触及活动状态。旧清单/无清单恢复必须调用一次转换，新格式不调用。verify 只改私有副本，无 ready、活动 key 或密码写入。
- [x] **2. RED。** `./gradlew :shared:testDebugUnitTest --tests '*BackupServiceTest' --tests '*BackupPortableKeyTest' --tests '*BackupRestoreTransactionTest' --tests '*BackupRoundTripTest' --tests '*BackupDatabaseDataTest' --tests '*DiaryThreadBackupTest' --tests '*BackupVerificationDiagnosticsTest'`。
- [x] **3. 实现快照与格式分支。** 用显式 keyed ATTACH/export 导出一致加密快照，保留版本，检查快照后写便携 key/manifest。JSON key 文件限制 ≤1024 bytes、hex 固定长度/固定兼容参数；清单 v2 必需且登记全部内容，v1 保持原规则。附件分类排除 `database_key.json`、`database_key.sec`、备份密码文件、密钥/事务目录，不能由旧无清单备份把它们当普通文件安装。
- [x] **4. 实现联合恢复提交。** 新格式用 portable key 打开并校验，仅做 Schema/路径修正；旧格式用任务 2 `convertLegacy` 生成加密副本。`BackupRestoreTransaction` 增加必需数据库密钥目标；同时兼容升级前已暂存、不带新 key 的 legacy pending restore：在锁内先转换其 incoming 库并补 wrapped key，同步完成后才重新发布 ready，成功才应用，失败保留旧活动数据。升级前已开始且带旧 journal 的恢复先按原 journal 安全回滚，不在执行中的 journal 上添加新目标；结束后才处理新的待恢复数据。转换事务回收先于该处理。恢复 stage/apply 各阶段异常和进程死亡测试覆盖 key 目标，新旧库/key 全部回滚；备份密码与资料保持原行为。
- [x] **5. GREEN。** 同一聚焦命令通过；现有录音冲突保护、保留旧资料提示、附件移动不二次复制、恢复手动重启提示不得回归。

### Task 6：只读诊断与启动失败保护

**Files:** 修改 `app/src/main/kotlin/com/dailysatori/core/diagnostics/RecoveryUpdateConfiguration.kt`、必要的 `DailySatoriApplication.kt`；仅增补任务 1 Android factory 的只读辅助，不启用业务 DI。

**Tests:** 扩展 `app/src/test/kotlin/com/dailysatori/core/diagnostics/RecoveryUpdateControllerTest.kt`，新增 `RecoveryEncryptedDatabaseTest.kt`（同目录）；按需新增 `DatabaseStartupSafetyTest.kt`。

**Interfaces:** 消费 `DatabaseKeyStore.readExisting()`。Android 工厂新增 `readRecoverySettings(context: PlatformContext): Map<String, String?>`：既有加密库用 SQLCipher `OPEN_READONLY`，明文旧库只读兼容；只取 update_channel/schemaVersion，不创建密钥/Schema。

- [x] **1. 写失败测试。** 正确密钥读取更新配置；KeyStore 异常/JNI 不可用/错误密码/无库时使用安装包默认配置并标注 unavailable；fake 记录无写操作/无 alias 创建。启动初始化失败不得进入 Koin/Schema/Worker，独立诊断进程不依赖 Bootstrap 成功。
- [x] **2. RED。** `./gradlew :app:testDebugUnitTest --tests '*RecoveryEncryptedDatabaseTest' --tests '*RecoveryUpdateControllerTest' --tests '*DatabaseStartupSafetyTest'`。
- [x] **3. 实现只读入口。** 用 Android SQLCipher 直接只读连接，不用会隐式迁移的业务 Driver；保持 CancellationException 传播，安全错误仅用于诊断类别，不附含密钥 SQL/异常文本。继续使用已有独立诊断/恢复入口，不新增普通 UI 流程。
- [x] **4. GREEN。** 同一命令通过；人工核对诊断进程的 Application 分支依然早于业务初始化。

### Task 7：实际加密探针、APK 检查与集中验收

**Files:** 创建 `scripts/test-database-encryption.py`、`scripts/check-sqlcipher-apk.py`；最终更新本计划复选项，只记录实际结果，不另写报告台账。

**Interfaces:** `test-database-encryption.py --sqlcipher <path>` 支持从 `SQLCIPHER_BIN` 读取；`check-sqlcipher-apk.py <apk>` 直接解析实际 ELF program headers，并调用 Android SDK zipalign 检查，返回非零失败（本机无 LLVM readelf）。测试 fixture 的 portable key 格式必须与任务 1 相同，不假设 SQLCipher CLI version 等于 Android SDK version。

- [x] **1. 编写真实文件探针。** 创建旧库及全部注册字段的代表样本、版本 0/非零、索引/触发器、WAL 已提交数据；SQLCipher 明文→加密 export 保留语义/版本，sqlite3 无密码读取失败、错误 key 失败、正确 key 成功；加密→同 key 快照→恢复读取成功，检查不含 marker 明文、边车保护及源文件未修改。复用项目转换逻辑可复用的 SQL 序列，明确主机探针不是 Android JNI 实测。
- [x] **2. 运行原生基础探针。** 当前 `sqlcipher` 未配置。实施时允许在 `.local/tools` 使用官方 SQLCipher 源码构建主机 CLI（目录 700，固定上游 tag/commit，OpenSSL 依赖）；或使用已有显式 `SQLCIPHER_BIN`。缺工具/网络/编译环境就报告未完成，不用 Python sqlite3/Mock 假装成功。运行 `python3 scripts/test-database-encryption.py --sqlcipher "$SQLCIPHER_BIN"`，预期所有实际加密探针通过。
- [x] **3. 一次集中审查与修复。** 对照规格审查 key 泄漏、初始化/恢复状态所有权、历史字段、两种密码生命周期和代码级兼容。发现 Important/Critical 一次性修复并重跑受影响测试；不派发重复读 diff/全量跑测试的代理。
- [x] **4. 最终合并验证。** `./gradlew :shared:testDebugUnitTest :app:testDebugUnitTest :app:compileDebugKotlin :app:assembleDebug`；结果要求 BUILD SUCCESSFUL，报告不把被跳过的真实 AI/原生测试算作通过。仅凭 UI 源码约束或 Mock 不能证明整库加密。
- [x] **5. APK 静态检查。** `python3 scripts/check-sqlcipher-apk.py app/build/outputs/apk/debug/app-debug.apk`；实际打包 ABI 都包含 SQLCipher `.so`，ELF LOAD alignment ≥16384，zipalign `-P 16` 检查通过，未把 `.local`/密钥/测试数据打进 APK。未运行 Android JNI/Keystore 端到端必须在交付风险中明确。
- [x] **6. 交付。** 列出关键文件和上述实际测试结果，说明首次旧库转换/旧备份恢复仍有一次整库 IO、新格式恢复不轮换密钥、数据库外附件不属于保护范围；初始实施不执行提交/推送/设备测试；后续经用户授权补充模拟器验收，仍未提交或推送。

## 执行方式与验收门槛

建议当前会话直接按任务顺序实施，任务 1→2→3→4→5→6→7；字段/密钥/恢复接口紧密关联，不为任务 4 的可并行性另开编码代理。遵守项目默认直接实现和一次集中审查；无需选择高消耗代理或每任务独立审查。

本计划需用户审阅确认后实施。任何所需依赖/API 不可用、历史数据无法严格解密、现有并行任务占用相同核心文件时，停止该阶段并报告具体证据，不扩大重构或覆盖其他会话成果。

## 实施验收记录

- 已在当前工作区完成任务 1–7 的代码实现，没有提交、推送、创建 worktree、启动模拟器或执行 UI 测试。实施期间 HEAD 从 `3a983380` 变化为 `533209d5`，保留其他会话修改及原有未跟踪文件。
- SQLCipher Android 锁定 `4.19.1`，实际 AAR 构造接口及连接池密码引用通过字节码/配置对象核对。修复连接池借用密码字节数组的生命周期：只在连接池关闭后清零，避免后续/WAL 连接无法解锁。
- 主机 CLI 从官方 `v4.19.0` 源码构建于私有 `.local/tools/sqlcipher-src/sqlite3`（目录 700），报告 `4.19.0 community`，不将其当作 Android 4.19.1 JNI 实测。`test-database-encryption.py` 通过；3 个 CLI-backed Kotlin 用例直接执行生产 export/严格字段转换 SQL，覆盖全部 11 个注册字段、版本 0/7、索引/触发器、WAL、原密钥快照、错误/无密钥拒绝及无 marker 明文。字段 fixture 使用主机 AES-GCM，不声称验证 Android 历史 Keystore envelope。
- shared 首次全量运行 1308 个用例：一个旧“Skill Token 必须字段加密”源码断言失败、13 个真实接口用例跳过。修正断言后重跑受影响测试，并增补连接池密码生命周期、固定 DSB2/GCM 密文兼容、DSB3 全段认证及空文件用例；相关聚焦运行全部通过，未重复无关全量测试。app 全量 1348 个用例无失败/跳过；`:app:compileDebugKotlin :app:assembleDebug` BUILD SUCCESSFUL。证据在私有 `.local/database-encryption/final-validation*.log` 与相关聚焦日志。
- 另以 `DAILY_AI_LIVE_TEST=1` 运行 `AiPurposeLiveTest`，使用既有 `.local/ai-test.json`，真实请求验证通过；同次 FileManagerEncryptionTest 8 个用例通过。其余 12 个真实接口用例未执行，不计为通过。
- APK 静态检查通过：arm64-v8a、armeabi-v7a、x86、x86_64 均有 SQLCipher，实际 ELF LOAD ≥16 KB、SDK zipalign `-P 16` 通过，无私有密钥/测试配置文件。
- 集中审查修复：旧 pending restore 准备失败不会替换活动数据、清理失败不重放；恢复发布前校验全部应用表/列；中断备份私有明文 ZIP/便携 key 在启动时清理；导出异常不携带 raw key；加密数据库不进行无效 ZIP 重压缩。
- 上述三项原先缺 Android 集成证据；用户后续授权模拟器测试后已补证，详见下方“模拟器验收补证”。主机 CLI、静态 APK 和设备原生实测仍分别报告。
- 新格式恢复沿用原数据库密钥、目标设备重新包装，无整库 rekey；旧明文库/旧备份首次转换仍需整库 IO。数据库外图片/录音等附件保护没有改变，资料文件继续独立加密。本轮不新增手动密钥轮换入口。

### 模拟器验收补证（用户后续授权）

- 新增 `app/src/androidTest/` 原生验收和 `scripts/test-database-encryption-emulator.py`。只使用 `.local/database-encryption/device/avd` 中两台一次性独立 API 36、x86_64 AVD，不改动原 AVD或真实用户数据；脚本最终关闭自己启动的模拟器，并拒绝占用中的端口。
- 首轮发现真实生产缺陷：Android 只以 OPEN_READWRITE 打开的旧库不能 ATTACH 不存在的导出文件；主机 CLI 带 CREATE，未暴露差异。新增 `EncryptedSnapshotFile.kt` 在 ATTACH 前排他创建私有空候选，并拒绝覆盖既有目标及 sidecar；保留源库无 CREATE 的失败保护。Android 修复前失败、修复后真实导出/迁移成功；失败时旧库和附件保留。
- 最终完整脚本 `--old-apk .local/database-encryption/legacy-app/app/build/outputs/apk/debug/app-debug.apk` 通过：11 个核心测试 + 旧 APK 播种 + 覆盖升级/便携导出 + 独立设备恢复 + 恢复后冷启动数据检查，共 15 个 instrumentation 用例，无失败。旧版、覆盖升级后及恢复后均启动真正 MainActivity，检查前台 Activity、截图及崩溃日志，不把恢复界面当作正常启动成功。
- 对 512 条日记逐条比对完整内容，检查父子关系、FTS 查询、11 类历史字段、附件路径以及图片/音频 SHA256；实际旧 Android Keystore 字段密文成功转换。跨设备验证源设备信封不能用于接收端、正确备份密码恢复可用、数据库 key fingerprint 不变、包装信封改变、SQLCipher 文件 salt 保留；生活资料和备份密码可在接收端读取。
- 核心异常验证包括：坏历史密文、缺失/损坏/错误密钥、错误恢复密码、损坏 incoming DB、失败恢复不重放、带有效密钥的只读设置读取无库/密钥/alias 写入，以及两个 pending 事务并存的真实 Bootstrap。12 个数据库安装 move 边界和 16 个恢复 move 边界注入 Error 中断后重新 Bootstrap，检查 DB/key 成对，恢复外部文件、资料目录 opaque 文件和备份密码同属旧或新版本。此处是持久化边界故障注入，不冒充真实断电；真实加密生活资料跨设备读取另由完整恢复用例验证。
- SAF 使用测试 APK 中独立 UID 的 DocumentsProvider 和真实 URI 权限授权，生产加解密、快照、ZIP、备份、验证、stage/Bootstrap 均实际执行。仅测试适配器延后 `restartApp()` 的进程退出，由脚本显式 force-stop/冷启动；不能声称已自动化验证系统退出动作。业务 receiver 仅在预 DI instrumentation 阶段由自身 debug UID 暂停，真正 App 启动前恢复默认状态。
- 最终代码级验证：shared 1313 tests，0 failures/errors，13 skipped；app 1348 tests，0 failures/errors/skipped；compileDebugKotlin、assembleDebug、assembleDebugAndroidTest 通过。此前 shared 的旧源码断言失败已由本次完整绿色结果取代。主机加密探针、APK 四 ABI/16 KB 对齐检查、git diff --check 通过。
- 最终设备证据在 `.local/database-encryption/device/final-acceptance/`，控制日志为 `.local/database-encryption/device/final-acceptance.log`，单元测试/构建日志为 `.local/database-encryption/device-final-build-unit.log`。结束后 adb 无连接设备，无本轮模拟器进程。未提交、推送或发布。
- 边界：未验证物理设备/OEM Keystore、ARM JNI 的运行、真实电源中断、超大数据库性能及任意真实用户旧库样本；13 个跳过用例不算通过。模拟器与样本测试不能证明任何环境下绝不丢失数据。
