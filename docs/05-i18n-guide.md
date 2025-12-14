# 🌐 国际化(i18n)使用指南

## 📋 概述

项目已成功从 JSON 格式迁移到 YAML 格式的国际化配置，并提供了统一的 `.t` 扩展方法调用方式。所有硬编码的中文文字都已迁移到 i18n 配置中，支持多语言切换。

## 🚀 使用方式

### 唯一推荐用法 - 使用.t扩展方法

```dart
import 'package:daily_satori/app/utils/i18n_extension.dart';

// 基本翻译 - 所有国际化调用都使用这种方式
'button.save'.t           // 获取"保存"
'title.settings'.t        // 获取"设置"
'error.network'.t         // 获取"网络连接错误"

// 按钮文字
Text('button.save'.t)
Text('button.cancel'.t)
Text('button.delete'.t)

// 页面标题
Text('title.settings'.t)
Text('title.books'.t)
Text('title.ai_config'.t)

// 导航标签
Text('nav.articles'.t)
Text('nav.diary'.t)
Text('nav.books'.t)

// Tooltip
tooltip: 'tooltip.select_book'.t
tooltip: 'tooltip.add_book'.t
tooltip: 'tooltip.search'.t

// 提示文字
hintText: 'hint.search_articles'.t
hintText: 'hint.search_content'.t

// 空状态
Text('empty.no_viewpoint'.t)
Text('empty.no_plugins'.t)
```

## 📁 文件结构

```
assets/i18n/
├── zh.yaml          # 中文翻译（YAML格式）
└── en.yaml          # 英文翻译（YAML格式）

lib/app/
├── services/i18n/
│   ├── i18n_service.dart       # 国际化服务（仅支持YAML）
│   └── translation_map.dart    # 翻译映射类
└── extensions/
    └── i18n_extension.dart     # 国际化扩展方法（仅.t方法）
```

## 🔧 配置文件格式

YAML配置采用分层结构，支持嵌套键访问：

```yaml
# zh.yaml
button:
  save: 保存
  cancel: 取消
  delete: 删除

title:
  settings: 设置
  books: 读书悟道

tooltip:
  select_book: 选择书籍
  add_book: 添加书籍

error:
  network: 网络连接错误，请检查网络后重试
  server: 服务器错误，请稍后重试
```

## ✅ 已完成的工作

1. **YAML格式迁移**: 将JSON配置文件转换为YAML格式，更易读易维护
2. **硬编码文字迁移**: 将所有UI中的硬编码中文文字替换为i18n调用
3. **统一扩展方法**: 简化为唯一的`.t`扩展方法，避免与GetX冲突
4. **服务更新**: 更新I18nService支持YAML格式，移除JSON支持
5. **清理旧文件**: 删除原有的JSON配置文件
6. **代码质量检查**: 通过`flutter analyze`检查，无错误警告

## 🎯 最佳实践

1. **统一使用.t方法**: 所有国际化调用都使用`'key'.t`格式
2. **使用有意义的键名**: 采用分层结构，如`button.save`、`title.settings`
3. **保持一致性**: 使用相同的命名约定和结构
4. **添加注释**: 在YAML文件中添加分组注释，便于维护
5. **及时更新**: 添加新的UI文本时，及时更新到i18n配置中

## 🔄 语言切换

应用支持中文和英文切换，语言设置会保存到本地，重启应用后生效。

## 📱 示例对比

### 迁移前：
```dart
Text('设置')
tooltip: '选择书籍'
hintText: '搜索文章...'
```

### 迁移后：
```dart
Text('title.settings'.t)
tooltip: 'tooltip.select_book'.t
hintText: 'hint.search_articles'.t
```

这样不仅支持多语言，还提高了代码的可维护性和一致性。

## ⚠️ 重要注意事项

1. **避免与GetX冲突**: 不要使用`.tr`，始终使用`.t`
2. **导入必要**: 使用时需要导入`import 'package:daily_satori/app/utils/i18n_extension.dart';`（或通过 `app_exports.dart` 自动获得）
3. **键名规范**: 使用小写字母和下划线，采用分层结构
4. **const限制**: 包含`.t`调用的列表不能使用`const`关键字