import 'package:daily_satori/app/services/http_service.dart';
import 'package:daily_satori/app/utils/utils.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:path/path.dart' as path;
import 'package:daily_satori/app/repositories/setting_repository.dart';
import 'package:flutter/services.dart' show rootBundle;
import 'package:yaml/yaml.dart';

/// 插件服务类 - 负责管理AI提示词和API模型预设
///
/// 配置加载顺序：
/// 1. 优先从数据库加载
/// 2. 若数据库无配置，则加载本地文件并保存到数据库
class PluginService {
  // 单例模式实现
  PluginService._();
  static final PluginService _instance = PluginService._();
  static PluginService get i => _instance;

  // 最终使用的数据
  List<AiModel> _aiModels = [];
  final Map<String, String> _aiPrompts = {};

  // 配置文件信息
  final _aiPromptsFileName = 'ai_prompts.yaml';
  final _aiModelsFileName = 'ai_models.yaml';

  /// 初始化插件服务
  Future<void> init() async {
    logger.i("[初始化] PluginService");

    // 加载所有配置
    await _loadAiPromptsConfig();
    await _loadAiModelsConfig();

    // 尝试更新配置, 不需要 await , 这个本来就是异步处理
    if (AppInfoUtils.isProduction) {
      _updateAllConfigs();
    }
  }

  /// 加载提示词配置
  Future<void> _loadAiPromptsConfig() async {
    final configContent = await _loadConfigContent(_aiPromptsFileName);
    if (configContent.isEmpty) return;

    try {
      final yamlData = loadYaml(configContent) as Map;
      if (yamlData.containsKey('ai_prompts')) {
        final promptsList = yamlData['ai_prompts'] as YamlList? ?? [];

        // 清空旧缓存
        _aiPrompts.clear();

        // 直接添加到缓存
        for (final item in promptsList) {
          final key = item['key'] as String;
          final content = item['content'] as String;
          _aiPrompts[key] = content;
        }

        logger.i('解析YAML: ai_prompts - 加载了${promptsList.length}个提示词');
      }
    } catch (e) {
      logger.e('解析YAML: ai_prompts - 失败 | $e');
    }
  }

  /// 加载API预设配置
  Future<void> _loadAiModelsConfig() async {
    final configContent = await _loadConfigContent(_aiModelsFileName);
    if (configContent.isEmpty) return;

    try {
      final yamlData = loadYaml(configContent) as Map;
      if (yamlData.containsKey('ai_models')) {
        final modelsList = yamlData['ai_models'] as YamlList? ?? [];

        // 清空旧列表
        _aiModels = [];

        // 直接添加到列表
        for (final item in modelsList) {
          final aiModel = AiModel(
            name: item['name'] as String,
            apiAddress: item['apiAddress'] as String,
            models: (item['models'] as List).map((e) => e.toString()).toList(),
          );
          _aiModels.add(aiModel);
        }

        logger.i('解析YAML: ai_models - 加载了${_aiModels.length}个模型预设');
      }
    } catch (e) {
      logger.e('解析YAML: ai_models - 失败 | $e');
    }
  }

  /// 加载配置文件内容
  Future<String> _loadConfigContent(String fileName) async {
    final String settingKey = _makePluginKey(fileName);
    final String localPath = 'assets/configs/$fileName';

    // 1. 从数据库读取配置
    String configContent = SettingRepository.instance.getSetting(settingKey);

    // 2. 数据库无配置或者开发模式下,读取本地文件
    if (configContent.isEmpty || !AppInfoUtils.isProduction) {
      try {
        configContent = await rootBundle.loadString(localPath);
        if (configContent.isNotEmpty) {
          // 保存到数据库
          SettingRepository.instance.saveSetting(settingKey, configContent);
          logger.i('加载本地配置: $fileName - 成功并保存到数据库');
        }
      } catch (e) {
        logger.e('加载本地配置: $fileName - 失败 | $e');
      }
    }

    return configContent;
  }

  /// 更新所有配置
  Future<void> _updateAllConfigs() async {
    final String baseUrl = getPluginServerUrl();
    if (baseUrl.isEmpty) {
      logger.w('更新配置: 服务器地址未设置');
      return;
    }

    await Future.wait([_updateConfig(_aiPromptsFileName, baseUrl), _updateConfig(_aiModelsFileName, baseUrl)]);

    logger.i('配置更新完成');
  }

  /// 更新单个配置
  Future<void> _updateConfig(String fileName, String baseUrl) async {
    try {
      final String url = path.join(baseUrl, fileName);
      final String response = await HttpService.i.getTextContent(url);

      if (response.trim().isNotEmpty) {
        // 保存到数据库
        final String settingKey = _makePluginKey(fileName);
        SettingRepository.instance.saveSetting(settingKey, response.trim());

        // 更新最后更新时间
        _updateLastUpdateTime(fileName);

        logger.i('更新配置: $fileName - 成功');

        // 重新加载配置
        if (fileName == _aiPromptsFileName) {
          await _loadAiPromptsConfig();
        } else if (fileName == _aiModelsFileName) {
          await _loadAiModelsConfig();
        }
      }
    } catch (e) {
      logger.e('更新配置: $fileName - 失败 | $e');
    }
  }

  /// 更新最后更新时间
  void _updateLastUpdateTime(String fileName) {
    final timeKey = '${_makePluginKey(fileName)}_last_update';
    final now = DateTime.now().toIso8601String();
    SettingRepository.instance.saveSetting(timeKey, now);
  }

  /// 获取最后更新时间
  DateTime? getLastUpdateTime(String fileName) {
    final timeKey = '${_makePluginKey(fileName)}_last_update';
    final timeStr = SettingRepository.instance.getSetting(timeKey);
    if (timeStr.isEmpty) return null;

    try {
      return DateTime.parse(timeStr);
    } catch (e) {
      return null;
    }
  }

  /// 构造用于SettingService的键名
  String _makePluginKey(String key) => 'plugin_$key';

  /// 获取插件服务器URL
  String getPluginServerUrl() {
    return SettingRepository.instance.getSetting(SettingService.pluginKey);
  }

  /// 设置插件服务器URL
  Future<void> setPluginServerUrl(String url) async {
    SettingRepository.instance.saveSetting(SettingService.pluginKey, url);
  }

  /// 强制更新单个插件
  Future<bool> forceUpdatePlugin(String fileName) async {
    try {
      final String baseUrl = getPluginServerUrl();
      if (baseUrl.isEmpty) {
        throw Exception('插件服务器地址未设置');
      }

      await _updateConfig(fileName, baseUrl);
      return true;
    } catch (e) {
      logger.e('强制更新插件失败: $fileName | $e');
      return false;
    }
  }

  /// 获取所有插件信息
  List<PluginInfo> getAllPlugins() {
    final plugins = [
      PluginInfo(
        fileName: _aiPromptsFileName,
        description: '提示词配置，包含翻译、摘要等角色提示',
        lastUpdateTime: getLastUpdateTime(_aiPromptsFileName),
      ),
      PluginInfo(
        fileName: _aiModelsFileName,
        description: 'AI模型配置，包含各种AI服务提供商的预设',
        lastUpdateTime: getLastUpdateTime(_aiModelsFileName),
      ),
    ];

    return plugins;
  }

  // 公开API - 获取提示词
  String getTranslateRole() => _aiPrompts['translate_role'] ?? '';
  String getTranslatePrompt() => _aiPrompts['translate_prompt'] ?? '';
  String getSummarizeOneLineRole() => _aiPrompts['summarize_oneline_role'] ?? '';
  String getSummarizeOneLinePrompt() => _aiPrompts['summarize_oneline_prompt'] ?? '';
  String getLongSummaryRole() => _aiPrompts['long_summary_role'] ?? '';
  String getShortSummaryRole() => _aiPrompts['short_summary_role'] ?? '';
  String getLongSummaryResult() => _aiPrompts['long_summary_result'] ?? '';
  String getCommonTags() => _aiPrompts['common_tags'] ?? '';
  String getHtmlToMarkdownRole() => _aiPrompts['html_to_markdown_role'] ?? '';

  // 书籍相关提示词
  String getBookRecommendByCategory() => _aiPrompts['book_recommend_by_category'] ?? '';
  String getBookViewpoint() => _aiPrompts['book_viewpoint'] ?? '';
  String getBookInfo() => _aiPrompts['book_info'] ?? '';

  /// 获取API提供商预设列表
  List<AiModel> getAiModels() => _aiModels;
}

/// API提供商预设模型
class AiModel {
  final String name;
  final String apiAddress;
  final List<String> models;

  AiModel({required this.name, required this.apiAddress, required this.models});
}

/// 插件信息类
class PluginInfo {
  final String fileName;
  final String description;
  final DateTime? lastUpdateTime;

  PluginInfo({required this.fileName, required this.description, this.lastUpdateTime});
}
