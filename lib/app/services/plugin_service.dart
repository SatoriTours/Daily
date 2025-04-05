import 'package:daily_satori/app/services/http_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:path/path.dart' as path;
import 'package:daily_satori/app/repositories/setting_repository.dart';
import 'package:flutter/services.dart' show rootBundle;
import 'package:yaml/yaml.dart';

class PluginService {
  // 私有构造函数，确保外部无法直接创建实例
  PluginService._();

  static final PluginService _instance = PluginService._();
  static PluginService get i => _instance;

  // 通用缓存
  final Map<String, String> _promptCache = {};
  final Map<String, dynamic> _configCache = {};

  // 本地配置
  List<_PromptConfig> _localPrompts = [];
  List<ApiPreset> _apiPresets = [];

  // 配置文件解析器映射表
  late final Map<String, _ConfigParser> _configParsers = {
    'ai_prompts.yaml': _ConfigParser(
      rootKey: 'prompts',
      parser: (item) => _PromptConfig.fromYaml(item),
      resultHandler: (list) {
        _localPrompts = list.cast<_PromptConfig>();
        // 提前缓存到内存
        for (final prompt in _localPrompts) {
          _promptCache[prompt.key] = prompt.content;
        }
        return '提示词';
      },
    ),
    'ai_models.yaml': _ConfigParser(
      rootKey: 'api_presets',
      parser: (item) => ApiPreset.fromYaml(item),
      resultHandler: (list) {
        _apiPresets = list.cast<ApiPreset>();
        return 'AI模型预设';
      },
    ),
  };

  // 配置文件列表，所有需要管理的配置文件
  late final List<_ConfigFile> _configFiles =
      _configParsers.keys.map((fileName) => _ConfigFile(name: fileName)).toList();

  // 初始化插件服务，日志记录并更新所有插件配置
  Future<void> init() async {
    logger.i("[初始化服务] PluginService");

    // 加载所有本地配置文件
    await _loadAllLocalConfigs();

    // 更新所有配置文件
    await _updateAllConfigs();
  }

  // 加载所有本地配置文件
  Future<void> _loadAllLocalConfigs() async {
    for (final configFile in _configFiles) {
      await _loadLocalConfig(configFile);
    }
  }

  // 加载单个本地配置文件
  Future<void> _loadLocalConfig(_ConfigFile configFile) async {
    try {
      // 加载YAML文件内容
      final String yamlString = await rootBundle.loadString(configFile.localPath);
      configFile.yamlContent = loadYaml(yamlString) as YamlMap;

      // 将原始内容缓存
      _configCache[configFile.name] = yamlString;

      logger.i('成功加载本地配置: ${configFile.name}');

      // 解析配置文件
      _parseConfigFile(configFile);
    } catch (e, stackTrace) {
      logger.e('加载本地配置失败 ${configFile.name}: $e', stackTrace: stackTrace);
    }
  }

  // 解析单个配置文件
  void _parseConfigFile(_ConfigFile configFile) {
    if (configFile.yamlContent == null) return;

    final parser = _configParsers[configFile.name];
    if (parser == null) {
      logger.w('未找到配置文件解析器: ${configFile.name}');
      return;
    }

    _parseYamlConfig(
      yamlContent: configFile.yamlContent!,
      rootKey: parser.rootKey,
      parser: parser.parser,
      resultHandler: parser.resultHandler,
    );
  }

  /// 通用YAML配置解析器
  ///
  /// [yamlContent] - YAML内容
  /// [rootKey] - 根键名称
  /// [parser] - 数据项解析器函数
  /// [resultHandler] - 结果处理函数，返回日志前缀
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

        // 处理结果并获取日志前缀
        final logPrefix = resultHandler(parsedList);

        logger.i('从YAML文件加载了${parsedList.length}个$logPrefix');
      } else {
        logger.e('加载配置失败: $rootKey节点不存在');
      }
    } catch (e) {
      logger.e('解析配置失败: $e');
    }
  }

  // 内部方法：根据键名获取插件内容；若未设置则返回默认本地内容
  String _getAiPromptContent(String key) {
    // 先从缓存读取
    if (_promptCache.containsKey(key)) {
      return _promptCache[key]!;
    }

    // 从设置读取
    String content = SettingRepository.getSetting(_pluginKey(key));
    if (content.isNotEmpty) {
      // 添加到缓存
      _promptCache[key] = content;
      return content;
    }

    // 从本地配置读取
    final localPrompt = _localPrompts.firstWhere(
      (prompt) => prompt.key == key,
      orElse: () => _PromptConfig(key: key, content: ''),
    );

    // 添加到缓存
    _promptCache[key] = localPrompt.content;
    return localPrompt.content;
  }

  // 更新所有配置文件
  Future<void> _updateAllConfigs() async {
    final String baseUrl = SettingRepository.getSetting(SettingService.pluginKey);
    if (baseUrl.isEmpty) {
      logger.w('插件服务器地址未设置，跳过更新');
      return;
    }

    // 创建所有更新任务
    final List<Future<void>> updateTasks = [];
    for (final configFile in _configFiles) {
      updateTasks.add(_updateConfig(configFile, baseUrl));
    }

    // 并行执行更新任务
    await Future.wait(updateTasks);
    logger.i('所有配置更新完成');
  }

  // 更新单个配置文件
  Future<void> _updateConfig(_ConfigFile configFile, String baseUrl) async {
    try {
      final String url = path.join(baseUrl, configFile.name);
      final String response = await HttpService.i.getTextContent(url);

      if (response.trim().isNotEmpty) {
        // 保存到设置表
        await SettingRepository.saveSetting(_pluginKey(configFile.name), response.trim());

        // 更新到内存
        configFile.yamlContent = loadYaml(response.trim()) as YamlMap;
        _configCache[configFile.name] = response.trim();

        logger.i('成功从服务器更新配置: ${configFile.name}');

        // 重新解析配置
        _parseConfigFile(configFile);
      }
    } catch (e) {
      logger.e('更新配置失败 ${configFile.name}: $e');

      // 更新失败时，使用本地配置备份
      final localContent = _configCache[configFile.name];
      if (localContent != null) {
        await SettingRepository.saveSetting(_pluginKey(configFile.name), localContent);
        logger.i('使用本地备份更新 ${configFile.name}');
      }
    }
  }

  // 内部辅助方法：构造在 SettingService 中存储插件内容的键名
  String _pluginKey(String key) => 'plugin_$key';

  // 公有方法：获取各插件的内容
  String getTranslateRole() => _getAiPromptContent('translate_role');

  String getTranslatePrompt() => _getAiPromptContent('translate_prompt');

  String getSummarizeOneLineRole() => _getAiPromptContent('summarize_oneline_role');

  String getSummarizeOneLinePrompt() => _getAiPromptContent('summarize_oneline_prompt');

  String getLongSummaryRole() => _getAiPromptContent('long_summary_role');

  String getShortSummaryRole() => _getAiPromptContent('short_summary_role');

  String getLongSummaryResult() => _getAiPromptContent('long_summary_result');

  String getCommonTags() => _getAiPromptContent('common_tags');

  String getHtmlToMarkdownRole() => _getAiPromptContent('html_to_markdown_role');

  // 公有方法：获取API提供商预设列表
  List<ApiPreset> getApiPresets() {
    // 尝试从设置获取
    final configName = 'ai_models.yaml';
    final storedContent = SettingRepository.getSetting(_pluginKey(configName));

    if (storedContent.isNotEmpty) {
      try {
        final YamlMap yamlMap = loadYaml(storedContent) as YamlMap;
        if (yamlMap.containsKey('api_presets')) {
          final YamlList presets = yamlMap['api_presets'] as YamlList;
          return presets.map((item) => ApiPreset.fromYaml(item)).toList();
        }
      } catch (e) {
        logger.e('解析API预设列表失败: $e');
      }
    }

    // 如果从设置中获取失败，或者解析失败，则使用本地预设
    return _apiPresets;
  }
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

/// 配置解析器
class _ConfigParser<T> {
  final String rootKey; // YAML根键名
  final T Function(dynamic) parser; // 解析函数
  final String Function(List<dynamic>) resultHandler; // 结果处理函数，返回日志前缀

  _ConfigParser({required this.rootKey, required this.parser, required this.resultHandler});
}

/// 提示词配置
class _PromptConfig {
  final String key;
  final String content;

  _PromptConfig({required this.key, required this.content});

  /// 从YAML对象创建PromptConfig
  factory _PromptConfig.fromYaml(dynamic yaml) {
    return _PromptConfig(key: yaml['key'] as String, content: yaml['content'] as String);
  }
}

/// 配置文件定义
class _ConfigFile {
  final String name; // 配置文件名，如 ai_prompts.yaml
  final String localPath; // 本地文件路径
  YamlMap? yamlContent; // 解析后的YAML内容

  _ConfigFile({required this.name, String? localPath}) : localPath = localPath ?? 'assets/configs/$name';
}
