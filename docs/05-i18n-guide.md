# 国际化 i18n 指南

## 概述

项目使用 YAML 维护中英文文案，并通过 Kotlin 共享层的 `I18nService` 提供翻译能力。新增或修改 UI 文案时，需要同步更新共享资源与 Android 打包资源。

## 资源路径

```text
shared/src/commonMain/resources/i18n/
├── zh.yaml
└── en.yaml

app/src/main/assets/i18n/
├── zh.yaml
└── en.yaml

shared/src/commonMain/kotlin/com/dailysatori/service/i18n/
└── I18nService.kt
```

## YAML 格式

YAML 配置采用分层结构，使用点分隔键访问嵌套文案。

```yaml
button:
  save: 保存
  cancel: 取消

title:
  settings: 设置
  books: 读书悟道

error:
  network: 网络连接错误，请检查网络后重试
```

## 开发规范

1. 使用有意义的分层键名，如 `button.save`、`title.settings`。
2. 新增文案必须同时更新 `zh.yaml` 和 `en.yaml`。
3. 需要随 App 打包的文案必须同步到 `app/src/main/assets/i18n/`。
4. 避免在 UI 中硬编码用户可见文案。
5. 修改后执行 `./gradlew :app:compileDebugKotlin --no-configuration-cache` 验证。

## 语言切换

语言设置由应用保存到本地。切换语言后，使用 `I18nService` 重新读取对应语言文案。
