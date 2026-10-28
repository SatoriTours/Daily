# 数据库结构重构实施计划

**规格**：[详细设计](../specs/2026-10-26-database-structure-redesign-design.md)。用户已批准实施；当前工作区 main，不创建分支/worktree，不提交或发布。

**执行**：当前代理连续实现、自测；每个完整功能切片一次独立验收。复用既有备份改动及其有效证据，不要求用户逐步审批。

## 全局约束

- 单个 SQLCipher 库，既有业务 ID、原文、附件、历史、隐私与恢复暂停保留。
- 默认完整备份，不清理外部内容，不读取真实用户库/凭据。
- 每片 TDD；只运行代码级测试和必要编译，不启动设备。
- 失败不推进版本、不清空原库、不把新结构查询用于旧库。
- 现有迁移为历史单步集合：安全基座先将历史升级作为一个可回滚事务组验证；39 起新增步骤独立验证，不伪称已经逐一重写旧 38 个迁移。
- 本机没有 apply_patch 命令/工具：新文件用 write，现有文件用精确 edit，禁止整文件覆盖未知成果。

## A：迁移安全基座（38 → 39）

### A1 安装提交前验证
- 修改 `shared/src/androidMain/kotlin/com/dailysatori/service/security/DatabaseInstallTransaction.kt`。
- 接口新增可选 `validateInstalled: () -> Unit`，放在 move 参数前以保持已有 trailing lambda 兼容；替换后、committed 前调用。
- 测试 `DatabaseInstallTransactionTest`：校验失败数据库/密钥/旧 WAL 回退、新 sidecar 清除；校验中进程中断下次回退；成功后的清理失败与重复启动不得回滚新写入。
- 最小 RED/GREEN：`:shared:testDebugUnitTest --tests '*DatabaseInstallTransactionTest'`。

### A2 版本与结构门禁
- 将 `AssistantSchemaPolicy` 的现有完整列清单提为公共迁移层 Schema 清单，AI 策略继续引用，不扩大可读字段。
- 修改 `DatabaseMigration.kt`：拒绝非法/较新版本；缺版本仅接受验证完整的当前结构；事务包裹历史升级组，目标结构及关联不通过时不推进版本。
- Config 目标版本 39，本片不改变业务表布局；新增 migrateV38ToV39 的安全检查。
- 新增 Schema/版本门禁测试，损坏/缺表/缺版本不完整/吞掉 DDL 失败时版本不变；完整新库和 v38 保留数据并幂等。

### A3 加密候选升级与启动接入
- 新增 Android `DatabaseSchemaUpgrade.kt`，由 DatabaseBootstrap 初始化锁唯一负责；先恢复已有 Schema 文件事务，再在加密候选运行迁移与核对，关闭连接后安装，提交前重新打开验证。
- 使用现有 SQLCipher 导出、一致性检查与包装密钥；保留原密钥，不写便携明文 key JSON。
- 不接受多个无法确定先后的 pending 安装；不让旧 Schema 候选覆盖新恢复库。
- 以可注入的文件/数据库操作边界测试原库不变、候选失败、空间/导出失败、重复启动、安装验证失败；关键原文/关系做内容核对。
- 恢复副本复用同一 Schema 门禁；同步检查旧备份迁移顺序，不触碰已加固的发布门禁。
- 最终一次组合相关 migration/security/backup/Schema 权限测试与 `:app:compileDebugKotlin`；独立验收后记录证据和剩余设备风险。

## A 已验证交付（2026-10-26）

- [x] A1 安装提交前验证、缺失载荷拒绝与回滚。
- [x] A2 版本/目标结构门禁，Schema 39，历史升级组事务化。
- [x] A3 加密候选升级接入 Bootstrap，原文/类型/NUL/序列保护及空间/并发变化拒绝。
- [x] 独立验收无未解决项；新增 20 项方法通过，真实主机 SQLCipher 用例未跳过。
- 验证：首轮 shared244（242通过/2失败）+app35全通过、App编译通过；两个失败是旧测试版本断言/物理fixture遗漏索引，修正后17项聚焦复测全通过。共279个唯一用例的有效组合证据，不冒称完整命令重跑全绿。
- 原计划历史升级组与新版本步骤的差异已在全局约束明示；目标结构检查覆盖列及命名对象存在性，不冒称逐条DDL语义等价证明。
- 尚未做真实设备/旧用户库/Keystore/断电验证；B—G尚未实施。未提交或发布。
- 证据：`~/.local/state/agent-artifacts/Daily/2026-10-26-database-redesign/`。

## B / 40 执行边界与状态

- [x] B1 附件 position/remote_url 与版本39→40迁移；旧ID、转写、状态、时间和文件不动。旧图片逐次关联保留，不按路径去重，照片组和原附件组分别稳定排序。
- [x] B2 仓库、编辑、续写、卡片/详情/API、旧JSON导入与备份恢复切换；新图片只写附件，images API字段只由附件投影。
- [x] B3 加密候选核对区分原有行与迁入图片，新增行逐位置/路径/时间核对；不以忽略整张附件表放行。
- [x] B4 最终组合shared506+app254全通过（0失败/错误/跳过），App编译通过，真实SQLCipher未跳过；独立验收发现P1，已集中修复并完成15+19项聚焦复验，原验收端关闭Important证据，代码级验收通过。未做设备或视觉验收。
- Ruling：38→39门禁冻结为39结构契约，不能拿未来40列要求去拒绝合法39中间态；最终仍检查完整40结构。
- Ruling：旧images暂留原文作为退休列，版本40备份不再把它当有效文件关联，业务读取来自附件；G阶段再移除。不做永久双写。
- Ruling：保留既有video及无输出录音/失败转写状态；不以新非空路径CHECK破坏已有录音流程。新非audio附件经仓库要求路径或remote_url，未知旧kind停止迁移。
- Ruling：特殊路径的images API/草稿传输用JSON转义，普通旧CSV保持兼容；只在传输使用，不新存一份图片JSON。缓存图片优先本地副本，保留远程地址。
- 运行期文件清理在关联提交后获取SQLite写锁，核对其他日记、文章图片和封面引用；相对路径定位到应用目录，不递归删除目录。不变更文章/书籍生命周期。
- 验证新增：迁移/双表示/顺序/重复路径/远程与缓存/特殊路径/真实文件共享清理/候选篡改拒绝/主机NULL语义；旧测试已按新的单一权威入口适配。
- Important关闭：历史JSON导入原来调用编辑入口会覆盖原更新时间，新增真实ZIP导入RED；改用同事务的初始化入口保留日记/附件原时间，图片失败整条回滚。另补真实附件名恰似旧null占位时的传输保护。三项新增RED→GREEN，最终有效证据共763个唯一用例；760完整组合+34受影响聚焦复测，不冒称完整命令重跑。
- 证据：同任务目录内stage-b-review.md保留原BLOCK发现，stage-b-review-closure.md记录最终关闭；完整及修复后JUnit分目录保存，源码hash分阶段留存。未启动模拟器/后台服务，未操作共享Gradle守护进程。
- C—G仍未实施；未提交或发布。

## C / 41 执行边界与状态

- [x] C1 严格转换器与原始指纹/未知时间历史基线；损坏JSON/别名循环拒绝，未知及孤立key不猜测删除；重复迁移不复制历史。最初3项RED后GREEN。
- [x] C2 当前标签和决定历史分离的单一写入端；仓库/导入切换，正文变化使当前抑制失效而保留旧历史，退休tags不参与展示/搜索。新增2项RED后GREEN；连同原标签用例27项通过。
- [x] C3 Schema41门禁、加密候选逐表/逐字段精确变换核对、物理旧结构/撤销/搜索/加密跨设备备份恢复回归；保留源库晚到修改保护，不忽略整个setting表。
- [x] C4 最终受影响模块组合与编译、一次独立验收及Important关闭复核通过；C代码级完成，设备及视觉未验证。
- Ruling：catalog保留导入变化检测的baseline JSON缓存（非用户标签事实）；旧JSON完整撤销载荷迁到版本化catalog，names/aliases只由关系表维护。未知/缺失diary的旧状态key保留，不能按前缀清空。
- Ruling：direct DiaryRepository.update原有可接收长旧标签的语义保留；UI新输入仍由DiaryTagRepository清洗。未改AI提示词或接口。
- Schema已接妥并升41；39/40中间态契约不要求未来标签表。退休tags列保留原文但不双写，列表/搜索/串快照/助手只读SQL均由当前关联投影，未改AI请求/提示词/返回解析。
- Ruling（2026-10-28修正）：旧saveDraft会把重新添加标签前的suppressed合并回来，因此可见激活项与抑制并存不能阻断升级；以旧diary.tags保留当前可见项，抑制决定和原指纹完整进入history，不再额外插入同标签的当前suppressed assignment。过期/无法映射原指纹只保留历史。合并也覆盖仅被抑制的标签，递增修改版本并拒绝旧AI结果，撤销可恢复。
- Ruling：真正pre-thread结构在捕获时不查询未来列/表；历史thread步骤确定将root初始化为1，候选核对证明实际结果一致。未知历史时间保持NULL，初始化导入不伪造标签编辑事件。
- 验证：首轮组合shared563（560通过/1个毫秒时钟fixture失败/2个主动关闭的KoogAssistantLiveTest跳过）+app254全通过，App编译实际通过。恢复旧测试原有显式时间边界后69项通过；再补手工冲突拒绝，最终受影响86项全通过（0失败/错误/跳过），再次编译检查有效复用。组合有效证据816个唯一代码用例，不冒称完整命令重跑全绿；2个真实AI用例未执行。
- 主机故障单列：初次真实SQLCipher探针240秒超时且执行器已退出；根因是测试驱动以CLI字面量/文本传输NUL会阻塞/截断。补独立RED，改用HEX绑定与类型/HEX结果传输，最终真实SQLCipher及NULL/空串/NUL/制表/换行探针均通过；未放宽生产校验。首次超时完整日志未单独保留，后续RED/GREEN与工具事件留作证据。
- 独立验收P1：被抑制来源合并到已激活目标时，候选afterState不等于实际状态，立即撤销失败。一次集中修复保存同事务实际state；新增真实RED→GREEN，56项聚焦全通过，覆盖立即撤销、后续编辑拒绝及旧AI拒绝。原验收端关闭P1，代码级验收通过；原BLOCK和closure报告均保留。
- 最终有效证据817个唯一代码用例通过，另2个真实AI用例未执行；不是修正后完整组合重跑。源码hash、完整组合/受影响/修复JUnit和日志已留存同任务artifact目录。用量未知，未估算账单；未启动设备/常驻服务，不停止共享构建守护进程。
- C代码级验收已完成；后续D进展见下节。代码未提交或发布；设备/真实用户库/Keystore/SAF/断电/视觉仍未验证。

## 后续依赖切片

- [x] B / 40：日记图片统一至附件；position/remote_url、删除/恢复/顺序切换。
- [x] C / 41：日记标签当前投影 + 决定历史，来源/抑制/撤销/搜索迁移。
- [x] D / 42：不可变提醒配置，草稿状态机和事务 outbox，重复确认/调度恢复；独立代码级验收及P1关闭复核通过。
- [x] E / 43：点子快照引用、v2 事件、合并与完整历史核对；独立代码级验收通过。
- [x] F / 44：助手关系、双键分页、记忆归属和隐私删除边界；独立代码级验收通过，原2项P1已关闭，详见下文。
- [x] G / 45：移除验证过的旧字段/key，全路径升级与备份往返；独立代码级验收通过，详见下文。

B—G 在 A 完整验收后按规格细化接口和聚焦测试，不提前混入未验证的破坏性结构改造。此计划不把 A 完成冒充整个重构完成。

## D / 42：实现及验证（独立代码级验收通过）

- 不可变原始配置快照：仅格式、hash和完整载荷一致时共享，保留未知JSON字节；提醒只引用快照，模板修改不改变既有定义。旧事件、提醒状态、ID、时间及序列值保留。
- 草稿统一state/revision；原始输入、AI建议与用户覆盖独立保留，selected不充当确认标志。2026-10-28修正：旧状态无法证明或关联冲突时，原样保留在reminder_ai_draft_legacy，不阻断升级、不参与活动草稿或自动调度；稳定ID及创建事件可证明的中断创建转SCHEDULE_PENDING，不把创建当调度成功。
- 原子确认校验revision和批次状态，单事务创建提醒、关联草稿及outbox；稳定任务键、执行租约、当前提醒版本保护重试，不承诺外部exactly-once。接入Worker恢复、注册和UI；已有提醒不能隐式移除/重编辑。
- 精确候选核对覆盖配置载荷、草稿转换和晚到修改；真实主机SQLCipher同密钥升级覆盖物理pre42结构及NUL载荷。跨设备密码归档恢复后待调度任务可恢复且不重复创建提醒，未知配置字段保留。
- 最终目标组合首次实际FAIL：shared686（683通过/3旧fixture失败）+app452全通过、0跳过。修正三处旧profile_json fixture后shared24聚焦全通过，App编译检查成功；有效组合1138个唯一用例PASS，非修复后完整重跑。新AI接口调用/提示词/解析未改，真实AI用例未执行。
- 首审P1：合法旧失败草稿selected=0时无法勾选重试。新增独立重试事务与按钮，按关联提醒当前版本调度，不读旧草稿/配置或selection；原始载荷、选择状态及不可编辑/隐式删除边界保留。旧父批次已确认时仍显示待调度恢复入口。四项新增真实RED→GREEN，含写outbox失败回滚。
- 修复验证：shared19+app33共52项全通过、0失败/错误/跳过，App编译实际执行通过。最终有效组合1142个唯一代码用例PASS，不是修复后完整组合重跑。原stage-d-review.md BLOCK保留，stage-d-review-closure.md关闭原P1，No issues found / OK with notes；reviewer只读核源码、日志及XML，未自行执行命令。
- 证据：统一artifact根的stage-d-junit-combination/、stage-d-junit-focused/、stage-d-junit-fix/、daily-reminder-final.log（原FAIL）、daily-reminder-final-fixtures-green.log、daily-reminder-review-{red,green}.log、stage-d-final-source-sha256.txt及校验日志。设备/真实用户库/实际Android闹钟/Keystore/SAF/断电及实际UI视觉未验证；真实AI未执行，E—G未实施；未提交或发布。

## E / 43：实现及验证（独立代码级验收通过）

- 来源切换不可变快照引用；仅格式、hash及完整原始JSON一致时共享，碰撞隔离，保留未知字段。captured/merged转v2有序引用，其他事件保原始v1；旧ID、rowid、原始归属、时间、上下文revision、用户确认/合并历史和完整消息保留。
- 当前归属FK及合并环门禁不补回已删除的历史实体；重复来源合并引用原快照，同域删除先清引用。事件和引用由单SELECT联接、内存还原，避免旧事件DTO再查询已删除引用及额外跨线程读事务。
- 候选逐字段/类型/字节核来源、事件、快照及有序引用，只允许专项转换，拒绝额外快照、篡改及晚到原库变化。真实主机SQLCipher同密钥升级及实际joined reader通过，物理pre43 fixture保NUL与未知字段。
- 真实密码归档跨设备恢复核未知字段、已删来源、历史owner、用户确认历史、快照引用及完整NUL消息；实际归档/storage使用JDBC，不冒充设备SQLCipher/Keystore/SAF。
- 首轮组合实际FAIL：shared696（3失败）+app480（2失败），0跳过；补旧备份fixture及v2语义断言，并修复观察读取回归。随后shared34+app17=51聚焦通过、App编译实际执行成功；非live点子/快照/SchemaUpgrade回归74通过，真实SQLCipher未跳过，最后App编译UP-TO-DATE。
- 有效组合1176个唯一代码用例PASS、0 FAIL/SKIP，不是修复后完整组合重跑。RED确有失败，但包含SQLite/状态检查异常，不统称断言失败。
- 原验收端只读核源码、日志/XML，未自行运行命令；stage-e-review.md结论No issues found / OK with notes，代码级PASS。stage-e-evidence.md为最新证据索引，stage-e-final-source-sha256.txt及校验日志为后续增量基线。
- 未做真实旧用户库、Android设备/闹钟/UI视觉、Keystore、SAF、断电/强杀验证；真实AI未调用，本片未改AI请求/提示词/返回解析。F/G未实施，未提交或发布。

## F / 44：实现及验证（独立代码级验收通过）

- 助手会话/执行/动作/引用与任务的RESTRICT关联，缺失助手会话仅从现存消息补身份；消息原文仍可按隐私规则删除，不补回正文，角色/会话逻辑检查及tombstone保护保留。双键分页按(created_at,id)，重复消息不猜合并。
- 旧记忆标legacy_unknown，人工/用户接受/未知归属不被后台覆盖或清理；生成记忆来源/隐私/ownership/CAS保护。根日记来源包含完整续写和完成转写，重建每根一次。
- 隐私删除统一入口，清原文及引用副本并保留执行身份、恢复暂停；跨设备归档恢复不复活原文，不自动重放动作。
- 首轮组合shared706（4失败）+app546（2失败）；旧fixture修复后shared36+app2通过，2项App未改路径间歇失败仅原样复测通过，未声称根因修复。native/domain129通过，首阶段有效1253；首次App编译实际执行成功。
- 首审两P1：canonical日记门禁与调用载荷不一致；声明43缺表在直接/恢复迁移路径被当空表重建。4新增真实RED→GREEN；权威根来源及版本感知源结构门禁修复后shared335全通过，App编译实际执行成功。
- 最终有效1257唯一代码用例PASS，0FAIL/SKIP，非修复后完整组合重跑。真实主机SQLCipher升级、实际密码归档跨设备恢复通过；MockEngine验证真实抽取服务保存/重建入口，不冒充真实AI。
- stage-f-review.md保留原BLOCK；stage-f-review-closure.md关闭两P1，No issues found / OK with notes。证据stage-f-evidence.md，后续基线stage-f-final-source-sha256.txt及校验日志。设备、真实用户库、Keystore、SAF、UI视觉、断电/强杀及真实AI未验；未提交或发布。

## G / 45：实现及验证（独立代码级验收通过）

- 只删除已由40/41单写端取代的diary.tags/images物理列，创建非物化diary_record兼容投影；图片仍由附件投影，不新增存储副本。物理写入不再带旧参数，三条content/images触发器改为content-only，保持FK启用与其他历史/未知列不变。
- 38—44库存仍要求原两列；45要求已退役列不存在且view完整，错误声明45但仍存旧列的混合结构拒绝。新view默认DENIED，不扩大模型字段权限。旧tag key继续仅由41精确消费退休，不扫删未知key。
- 候选仅对两已退役列采用预计算的剩余字段typed/HEX摘要；正文、附件、原始历史及其他表核对不放宽，晚到源变化仍核完整源表示。
- 原组合shared818（1旧fixture失败）+app546全部通过，无skip；补最后物理旧样本后shared12聚焦全通过，App编译本次实际执行成功。最终1365唯一代码用例有效组合PASS，非修复后完整组合重跑，非全项目测试。module/fixtures/physical中间失败日志及XML保留，不按green文件名冒充通过。
- 真实主机SQLCipher旧助手/点子/提醒/日记组合升级至45、同密钥加密候选及晚到/原文保护通过；B—F完整历史、隐私及恢复暂停回归通过。
- 新增物理44来源→加密新归档→删除本任务临时源设备目录→仅凭归档跨设备恢复；保NUL正文、active标签、重复/remote图片顺序、未知含NUL key、音频/照片字节，活动源库字节不变，目标无旧物理列。实际archive/storage配合JDBC/fake平台，不冒充设备SQLCipher。
- stage-g-review.md：No issues found / OK with notes，G代码级PASS；reviewer只读核源码/日志/XML，未执行命令。stage-g-evidence.md为最新索引，stage-g-final-source-sha256.txt及校验日志为最终基线。
- A—G代码级切片均通过独立验收；设备、真实旧用户库、Keystore、SAF、实际UI视觉、断电/强杀及真实AI仍未验证，不等于设备或真实用户库验收完成。既有App间歇用例本轮546项通过，不宣称根因修复；未提交或发布。

## Review Focus

1. 安装验证失败或进程中断必须恢复原 DB、key 和 sidecar。
2. committed 后清理失败不能回滚新用户写入。
3. 缺版本不等于新建；吞掉迁移错误不能标记成功。
4. 活动 WAL 不能被不一致复制；旧候选不能覆盖后来恢复的数据。
5. 旧备份在副本迁移，关联/权限/隐私边界不得放宽。
