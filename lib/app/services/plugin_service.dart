import 'package:daily_satori/app/services/http_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:path/path.dart' as path;
import 'package:daily_satori/app/repositories/setting_repository.dart';
import 'package:flutter/services.dart' show rootBundle;
import 'package:yaml/yaml.dart';

/// API提供商预设模型
class ApiPreset {
  final String name;
  final String apiAddress;
  final List<String> models;

  ApiPreset({required this.name, required this.apiAddress, required this.models});

  /// 从JSON对象创建ApiPreset
  factory ApiPreset.fromJson(Map<String, dynamic> json) {
    return ApiPreset(
      name: json['name'] as String,
      apiAddress: json['apiAddress'] as String,
      models: (json['models'] as List).map((e) => e.toString()).toList(),
    );
  }

  /// 转换为JSON对象
  Map<String, dynamic> toJson() {
    return {'name': name, 'apiAddress': apiAddress, 'models': models};
  }
}

class PluginService {
  // 私有构造函数，确保外部无法直接创建实例
  PluginService._();

  static final PluginService _instance = PluginService._();
  static PluginService get i => _instance;

  // 插件配置内容
  late Map<String, String> _localPlugins = {};
  final String _pluginsYamlPath = 'assets/configs/plugins.yaml';
  final String _aiModelsYamlPath = 'assets/configs/ai_models.yaml';

  // AI模型预设
  List<ApiPreset> _apiPresets = [];

  // 初始化插件服务，日志记录并更新所有插件配置
  Future<void> init() async {
    logger.i("[初始化服务] PluginService");

    // 从YAML文件加载默认插件配置
    await _loadLocalPlugins();

    // 加载AI模型预设
    await _loadLocalAiModels();

    // 更新所有插件配置
    _updateAllPlugins();

    // 更新AI模型预设
    _updateAiModels();
  }

  // 从YAML文件加载默认插件配置
  Future<void> _loadLocalPlugins() async {
    try {
      // 加载YAML文件内容
      final String yamlString = await rootBundle.loadString(_pluginsYamlPath);
      final YamlMap yamlMap = loadYaml(yamlString) as YamlMap;

      // 转换为Map<String, String>
      _localPlugins = yamlMap.map((key, value) => MapEntry(key.toString(), value.toString()));

      logger.i('从YAML文件加载了${_localPlugins.length}个插件配置');
    } catch (e) {
      logger.e('加载插件配置文件失败: $e');
      // 如果加载失败，使用空Map，后续会从在线获取或使用空字符串
      _localPlugins = {};
    }
  }

  // 从YAML文件加载AI模型预设
  Future<void> _loadLocalAiModels() async {
    try {
      // 加载YAML文件内容
      final String yamlString = await rootBundle.loadString(_aiModelsYamlPath);
      final YamlMap yamlMap = loadYaml(yamlString) as YamlMap;

      // 解析API预设
      final YamlList presets = yamlMap['api_presets'] as YamlList;
      _apiPresets =
          presets.map((item) {
            final Map<String, dynamic> preset = {
              'name': item['name'],
              'apiAddress': item['apiAddress'],
              'models': item['models'],
            };
            return ApiPreset.fromJson(preset);
          }).toList();

      logger.i('从YAML文件加载了${_apiPresets.length}个AI模型预设');
    } catch (e, stackTrace) {
      logger.e('加载AI模型预设文件失败: $e', stackTrace: stackTrace);
      // 如果加载失败，使用空列表
      _apiPresets = [];
    }
  }

  // 公有方法：获取各插件的内容
  String getTranslateRole() => _getPluginContent('translate_role');

  String getTranslatePrompt() => _getPluginContent('translate_prompt');

  String getSummarizeOneLineRole() => _getPluginContent('summarize_oneline_role');

  String getSummarizeOneLinePrompt() => _getPluginContent('summarize_oneline_prompt');

  String getLongSummaryRole() => _getPluginContent('long_summary_role');

  String getShortSummaryRole() => _getPluginContent('short_summary_role');

  String getLongSummaryResult() => _getPluginContent('long_summary_result');

  String getCommonTags() => _getPluginContent('common_tags');

  String getHtmlToMarkdownRole() => _getPluginContent('html_to_markdown_role');

  // 公有方法：获取API提供商预设列表
  List<ApiPreset> getApiPresets() {
    final String yamlStr = _getPluginContent('ai_models');
    if (yamlStr.isNotEmpty) {
      try {
        final YamlMap yamlMap = loadYaml(yamlStr) as YamlMap;
        // 解析API预设
        if (yamlMap.containsKey('api_presets')) {
          final YamlList presets = yamlMap['api_presets'] as YamlList;
          return presets.map((item) {
            final Map<String, dynamic> preset = {
              'name': item['name'],
              'apiAddress': item['apiAddress'],
              'models': item['models'],
            };
            return ApiPreset.fromJson(preset);
          }).toList();
        }
      } catch (e) {
        logger.e('解析API预设列表失败: $e');
      }
    }

    // 如果从设置中获取失败，或者解析失败，则使用本地预设
    return _apiPresets;
  }

  // 内部方法：根据键名获取插件内容；若未设置则返回默认本地内容
  String _getPluginContent(String key) {
    String content = SettingRepository.getSetting(_pluginKey(key));
    if (content.isEmpty) {
      content = _localPlugins[key] ?? '';
    }
    return content;
  }

  // 内部方法：更新所有插件配置
  Future<void> _updateAllPlugins() async {
    final String baseUrl = SettingRepository.getSetting(SettingService.pluginKey);
    // 创建所有插件更新任务的列表
    final List<Future<void>> updateTasks = [];

    // 为每个插件创建更新任务
    for (final key in _localPlugins.keys) {
      final String url = path.join(baseUrl, key);
      updateTasks.add(_updatePlugin(key, url));
    }

    // 并行执行所有更新任务
    await Future.wait(updateTasks);
    logger.i('插件服务初更新完成');
  }

  // 更新AI模型预设
  Future<void> _updateAiModels() async {
    final String baseUrl = SettingRepository.getSetting(SettingService.pluginKey);
    final String url = path.join(baseUrl, 'ai_models.yaml');

    try {
      final String response = await HttpService.i.getTextContent(url);
      if (response.trim().isNotEmpty) {
        await SettingRepository.saveSetting(_pluginKey('ai_models'), response.trim());
        logger.i('AI模型预设更新成功');
      }
    } catch (e) {
      logger.e('更新AI模型预设失败: $e');
      // 如果远程获取失败，将本地预设保存到设置中
      try {
        // 将本地预设转换为YAML格式字符串
        final String yamlString = await rootBundle.loadString(_aiModelsYamlPath);
        await SettingRepository.saveSetting(_pluginKey('ai_models'), yamlString);
      } catch (yamlError) {
        logger.e('保存本地AI模型预设失败: $yamlError');
      }
    }
  }

  // 内部方法：更新单个插件配置
  Future<void> _updatePlugin(String key, String url) async {
    final String response = await HttpService.i.getTextContent(url);
    final String content = response.trim();
    if (content.isNotEmpty) {
      await SettingRepository.saveSetting(_pluginKey(key), content);
      // logger.i('插件 $key 更新成功');
    }
  }

  // 内部辅助方法：构造在 SettingService 中存储插件内容的键名
  String _pluginKey(String key) => 'plugin_$key';

  /// 设置插件内容
  Future<void> setPluginContent(String key, String content) async {
    await SettingRepository.saveSetting(_pluginKey(key), content);
  }
}
