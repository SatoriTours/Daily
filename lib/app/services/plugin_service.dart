import 'package:daily_satori/app/services/http_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:path/path.dart' as path;
import 'package:daily_satori/app/repositories/setting_repository.dart';
import 'package:flutter/services.dart' show rootBundle;
import 'package:yaml/yaml.dart';

/// 插件服务类 - 负责管理AI提示词和API模型预设
///
/// 主要功能:
/// 1. 加载和解析本地YAML配置文件
/// 2. 从远程服务器获取最新配置
/// 3. 管理配置缓存和持久化
/// 4. 提供公开方法获取各类配置内容
class PluginService {
  // 单例模式实现
  PluginService._();
  static final PluginService _instance = PluginService._();
  static PluginService get i => _instance;

  // 缓存
  final Map<String, String> _promptCache = {};
  final Map<String, dynamic> _configCache = {};

  // 本地配置数据
  List<PromptConfig> _localPrompts = [];
  List<ApiPreset> _apiPresets = [];

  // 配置文件解析器映射
  late final Map<String, ConfigParser> _configParsers = {
    'ai_prompts.yaml': ConfigParser(
      rootKey: 'prompts',
      parser: (item) => PromptConfig.fromYaml(item),
      resultHandler: (list) {
        _localPrompts = list.cast<PromptConfig>();
        // 提前缓存到内存
        for (final prompt in _localPrompts) {
          _promptCache[prompt.key] = prompt.content;
        }
        return '提示词';
      },
    ),
    'ai_models.yaml': ConfigParser(
      rootKey: 'api_presets',
      parser: (item) => ApiPreset.fromYaml(item),
      resultHandler: (list) {
        _apiPresets = list.cast<ApiPreset>();
        return 'AI模型预设';
      },
    ),
  };

  // 配置文件列表
  late final List<ConfigFile> _configFiles = _configParsers.keys.map((fileName) => ConfigFile(name: fileName)).toList();

  /// 初始化插件服务
  Future<void> init() async {
    logger.i("[初始化] PluginService");
    await _loadAllLocalConfigs();
    await _updateAllConfigs();
  }

  /// 加载所有本地配置文件
  Future<void> _loadAllLocalConfigs() async {
    for (final configFile in _configFiles) {
      await _loadLocalConfig(configFile);
    }
  }

  /// 加载单个本地配置文件
  Future<void> _loadLocalConfig(ConfigFile configFile) async {
    try {
      final String yamlString = await rootBundle.loadString(configFile.localPath);
      configFile.yamlContent = loadYaml(yamlString) as YamlMap;
      _configCache[configFile.name] = yamlString;

      logger.i('加载本地配置: ${configFile.name} - 成功');
      _parseConfigFile(configFile);
    } catch (e, stackTrace) {
      logger.e('加载本地配置: ${configFile.name} - 失败 | $e', stackTrace: stackTrace);
    }
  }

  /// 解析配置文件内容
  void _parseConfigFile(ConfigFile configFile) {
    if (configFile.yamlContent == null) return;

    final parser = _configParsers[configFile.name];
    if (parser == null) {
      logger.w('解析器缺失: ${configFile.name}');
      return;
    }

    _parseYamlConfig(
      yamlContent: configFile.yamlContent!,
      rootKey: parser.rootKey,
      parser: parser.parser,
      resultHandler: parser.resultHandler,
    );
  }

  /// 通用YAML配置解析方法
  void _parseYamlConfig<T>({
    required YamlMap yamlContent,
    required String rootKey,
    required T Function(dynamic) parser,
    required String Function(List<dynamic>) resultHandler,
  }) {
    try {
      if (yamlContent.containsKey(rootKey)) {
        final YamlList itemsList = yamlContent[rootKey] as YamlList;
        final parsedList = itemsList.map((item) => parser(item)).toList();
        final logPrefix = resultHandler(parsedList);

        logger.i('解析YAML: $rootKey - 加载了${parsedList.length}个$logPrefix');
      } else {
        logger.e('解析YAML: $rootKey - 节点不存在');
      }
    } catch (e) {
      logger.e('解析YAML: $rootKey - 解析失败 | $e');
    }
  }

  /// 获取AI提示词内容
  ///
  /// 优先级: 缓存 > 设置持久化 > 本地配置
  String _getAiPromptContent(String key) {
    // 1. 先从内存缓存读取
    if (_promptCache.containsKey(key)) {
      return _promptCache[key]!;
    }

    // 2. 从设置存储读取
    final String content = SettingRepository.getSetting(_makePluginKey(key));
    if (content.isNotEmpty) {
      _promptCache[key] = content; // 添加到缓存
      return content;
    }

    // 3. 从本地配置读取
    final localPrompt = _localPrompts.firstWhere(
      (prompt) => prompt.key == key,
      orElse: () => PromptConfig(key: key, content: ''),
    );

    _promptCache[key] = localPrompt.content; // 添加到缓存
    return localPrompt.content;
  }

  /// 更新所有配置文件
  Future<void> _updateAllConfigs() async {
    final String baseUrl = getPluginServerUrl();
    if (baseUrl.isEmpty) {
      logger.w('更新配置: 服务器地址未设置 - 已跳过');
      return;
    }

    // 并行执行所有配置更新
    await Future.wait(_configFiles.map((file) => _updateConfig(file, baseUrl)));

    logger.i('更新配置: 所有文件更新完成');
  }

  /// 更新单个配置文件
  Future<void> _updateConfig(ConfigFile configFile, String baseUrl) async {
    final fileName = configFile.name;
    try {
      final String url = path.join(baseUrl, fileName);
      final String response = await HttpService.i.getTextContent(url);

      if (response.trim().isNotEmpty) {
        // 保存到设置并更新内存
        await SettingRepository.saveSetting(_makePluginKey(fileName), response.trim());
        configFile.yamlContent = loadYaml(response.trim()) as YamlMap;
        _configCache[fileName] = response.trim();

        // 更新最后更新时间
        await _updateLastUpdateTime(fileName);

        logger.i('更新配置: $fileName - 从服务器成功更新');
        _parseConfigFile(configFile);
      }
    } catch (e) {
      logger.e('更新配置: $fileName - 服务器更新失败 | $e');

      // 更新失败时使用本地配置备份
      final localContent = _configCache[fileName];
      if (localContent != null) {
        await SettingRepository.saveSetting(_makePluginKey(fileName), localContent);
        logger.i('更新配置: $fileName - 已使用本地备份');
      }
    }
  }

  /// 获取插件服务器URL
  String getPluginServerUrl() {
    return SettingRepository.getSetting(SettingService.pluginKey);
  }

  /// 设置插件服务器URL
  Future<void> setPluginServerUrl(String url) async {
    await SettingRepository.saveSetting(SettingService.pluginKey, url);
  }

  /// 更新最后更新时间
  Future<void> _updateLastUpdateTime(String fileName) async {
    final timeKey = '${_makePluginKey(fileName)}_last_update';
    final now = DateTime.now().toIso8601String();
    await SettingRepository.saveSetting(timeKey, now);
  }

  /// 获取最后更新时间
  DateTime? getLastUpdateTime(String fileName) {
    final timeKey = '${_makePluginKey(fileName)}_last_update';
    final timeStr = SettingRepository.getSetting(timeKey);
    if (timeStr.isEmpty) return null;

    try {
      return DateTime.parse(timeStr);
    } catch (e) {
      return null;
    }
  }

  /// 获取所有插件信息
  List<PluginInfo> getAllPlugins() {
    return _configFiles.map((file) {
      final lastUpdate = getLastUpdateTime(file.name);

      // 尝试获取描述
      String description = '';
      if (file.yamlContent != null && file.yamlContent!.containsKey('description')) {
        description = file.yamlContent!['description'] as String? ?? '';
      }

      return PluginInfo(
        fileName: file.name,
        description: description.isNotEmpty ? description : _getDefaultDescription(file.name),
        lastUpdateTime: lastUpdate,
      );
    }).toList();
  }

  /// 获取插件默认描述
  String _getDefaultDescription(String fileName) {
    switch (fileName) {
      case 'ai_prompts.yaml':
        return 'AI提示词配置，包含翻译、摘要等角色提示';
      case 'ai_models.yaml':
        return 'AI模型配置，包含各种AI服务提供商的预设';
      default:
        return '插件配置文件';
    }
  }

  /// 强制更新单个插件
  Future<bool> forceUpdatePlugin(String fileName) async {
    try {
      // 查找配置文件
      final configFile = _configFiles.firstWhere(
        (file) => file.name == fileName,
        orElse: () => throw Exception('找不到插件配置: $fileName'),
      );

      // 获取服务器URL
      final baseUrl = getPluginServerUrl();
      if (baseUrl.isEmpty) {
        throw Exception('插件服务器地址未设置');
      }

      // 执行更新
      await _updateConfig(configFile, baseUrl);
      return true;
    } catch (e) {
      logger.e('强制更新插件失败: $fileName | $e');
      return false;
    }
  }

  /// 构造用于SettingService的键名
  String _makePluginKey(String key) => 'plugin_$key';

  // 公开的API方法 - 获取各类提示词内容
  String getTranslateRole() => _getAiPromptContent('translate_role');
  String getTranslatePrompt() => _getAiPromptContent('translate_prompt');
  String getSummarizeOneLineRole() => _getAiPromptContent('summarize_oneline_role');
  String getSummarizeOneLinePrompt() => _getAiPromptContent('summarize_oneline_prompt');
  String getLongSummaryRole() => _getAiPromptContent('long_summary_role');
  String getShortSummaryRole() => _getAiPromptContent('short_summary_role');
  String getLongSummaryResult() => _getAiPromptContent('long_summary_result');
  String getCommonTags() => _getAiPromptContent('common_tags');
  String getHtmlToMarkdownRole() => _getAiPromptContent('html_to_markdown_role');

  /// 获取API提供商预设列表
  List<ApiPreset> getApiPresets() {
    // 尝试从设置获取
    final configName = 'ai_models.yaml';
    final storedContent = SettingRepository.getSetting(_makePluginKey(configName));

    if (storedContent.isNotEmpty) {
      try {
        final YamlMap yamlMap = loadYaml(storedContent) as YamlMap;
        if (yamlMap.containsKey('api_presets')) {
          final YamlList presets = yamlMap['api_presets'] as YamlList;
          return presets.map((item) => ApiPreset.fromYaml(item)).toList();
        }
      } catch (e) {
        logger.e('获取API预设: 解析失败 | $e');
      }
    }

    // 如果从设置获取失败，使用本地预设
    return _apiPresets;
  }
}

/// 插件信息类 - 表示单个插件的信息
class PluginInfo {
  final String fileName; // 文件名
  final String description; // 描述
  final DateTime? lastUpdateTime; // 最后更新时间

  PluginInfo({required this.fileName, required this.description, this.lastUpdateTime});
}

/// API提供商预设模型
class ApiPreset {
  final String name;
  final String apiAddress;
  final List<String> models;

  ApiPreset({required this.name, required this.apiAddress, required this.models});

  /// 从YAML对象创建ApiPreset
  factory ApiPreset.fromYaml(dynamic yaml) {
    return ApiPreset(
      name: yaml['name'] as String,
      apiAddress: yaml['apiAddress'] as String,
      models: (yaml['models'] as List).map((e) => e.toString()).toList(),
    );
  }

  /// 转换为Map对象
  Map<String, dynamic> toMap() {
    return {'name': name, 'apiAddress': apiAddress, 'models': models};
  }
}

/// 配置解析器类 - 负责解析特定类型的配置
class ConfigParser<T> {
  final String rootKey; // YAML根键名
  final T Function(dynamic) parser; // 解析函数
  final String Function(List<dynamic>) resultHandler; // 结果处理函数

  ConfigParser({required this.rootKey, required this.parser, required this.resultHandler});
}

/// 提示词配置类
class PromptConfig {
  final String key;
  final String content;

  PromptConfig({required this.key, required this.content});

  /// 从YAML对象创建PromptConfig
  factory PromptConfig.fromYaml(dynamic yaml) {
    return PromptConfig(key: yaml['key'] as String, content: yaml['content'] as String);
  }
}

/// 配置文件类 - 表示一个配置文件及其内容
class ConfigFile {
  final String name; // 配置文件名，如 ai_prompts.yaml
  final String localPath; // 本地资源路径
  YamlMap? yamlContent; // 解析后的YAML内容

  ConfigFile({required this.name, String? localPath}) : localPath = localPath ?? 'assets/configs/$name';
}
