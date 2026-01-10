import 'package:daily_satori/app/data/data.dart';
import 'package:daily_satori/app/services/http_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/service_base.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:daily_satori/app/utils/app_info_utils.dart';
import 'package:flutter/services.dart' show rootBundle;
import 'package:path/path.dart' as path;
import 'package:yaml/yaml.dart';

/// 插件服务 - 管理AI提示词和模型预设
class PluginService extends AppService {
  PluginService._();
  static final PluginService i = PluginService._();

  @override
  final ServicePriority priority = ServicePriority.normal;

  final Map<String, String> _prompts = {};
  final List<AiModel> _models = [];

  final _promptsKey = 'plugin_ai_prompts.yaml';
  final _modelsKey = 'plugin_ai_models.yaml';

  @override
  Future<void> init() async {
    await _loadConfigs();
    if (AppInfoUtils.isProduction) _updateAll();
  }

  Future<void> _loadConfigs() async {
    await Future.wait([
      _loadPrompts(),
      _loadModels(),
    ]);
  }

  Future<void> _loadPrompts() async {
    final content = await _loadContent(_promptsKey, 'ai_prompts.yaml');
    if (content.isEmpty) return;

    try {
      final yaml = loadYaml(content) as Map;
      final prompts = yaml['ai_prompts'] as YamlList? ?? [];
      _prompts.clear();
      for (final item in prompts) {
        _prompts[item['key'] as String] = item['content'] as String;
      }
    } catch (e) {
      logger.e('加载提示词失败: $e');
    }
  }

  Future<void> _loadModels() async {
    final content = await _loadContent(_modelsKey, 'ai_models.yaml');
    if (content.isEmpty) return;

    try {
      final yaml = loadYaml(content) as Map;
      final models = yaml['ai_models'] as YamlList? ?? [];
      _models.clear();
      for (final item in models) {
        _models.add(AiModel(
          name: item['name'] as String,
          apiAddress: item['apiAddress'] as String,
          models: (item['models'] as List).map((e) => e.toString()).toList(),
        ));
      }
    } catch (e) {
      logger.e('加载模型配置失败: $e');
    }
  }

  Future<String> _loadContent(String dbKey, String assetPath) async {
    var content = SettingRepository.i.getSetting(dbKey);
    if (content.isEmpty || !AppInfoUtils.isProduction) {
      try {
        content = await rootBundle.loadString('assets/configs/$assetPath');
        if (content.isNotEmpty) {
          SettingRepository.i.saveSetting(dbKey, content);
        }
      } catch (e) {
        logger.e('加载本地配置失败: $assetPath - $e');
      }
    }
    return content;
  }

  Future<void> _updateAll() async {
    final baseUrl = SettingRepository.i.getSetting(SettingService.pluginKey);
    if (baseUrl.isEmpty) return;

    await Future.wait([
      _updateConfig(_promptsKey, baseUrl, 'ai_prompts.yaml'),
      _updateConfig(_modelsKey, baseUrl, 'ai_models.yaml'),
    ]);
  }

  Future<void> _updateConfig(String dbKey, String baseUrl, String fileName) async {
    try {
      final url = path.join(baseUrl, fileName);
      final content = await HttpService.i.getTextContent(url);
      if (content.trim().isNotEmpty) {
        SettingRepository.i.saveSetting(dbKey, content.trim());
        _saveTime(dbKey);
        logger.i('更新配置成功: $fileName');
        await _loadConfigs();
      }
    } catch (e) {
      logger.e('更新配置失败: $fileName - $e');
    }
  }

  void _saveTime(String key) {
    SettingRepository.i.saveSetting('${key}_last_update', DateTime.now().toIso8601String());
  }

  DateTime? _getTime(String key) {
    final timeStr = SettingRepository.i.getSetting('${key}_last_update');
    if (timeStr.isEmpty) return null;
    try {
      return DateTime.parse(timeStr);
    } catch (_) {
      return null;
    }
  }

  // ========== 公开 API ==========

  String get serverUrl => SettingRepository.i.getSetting(SettingService.pluginKey);

  Future<void> setServerUrl(String url) async {
    SettingRepository.i.saveSetting(SettingService.pluginKey, url);
  }

  Future<bool> forceUpdate(String fileName) async {
    final url = serverUrl;
    if (url.isEmpty) return false;
    try {
      if (fileName == 'ai_prompts.yaml') {
        await _updateConfig(_promptsKey, url, fileName);
      } else if (fileName == 'ai_models.yaml') {
        await _updateConfig(_modelsKey, url, fileName);
      }
      return true;
    } catch (e) {
      logger.e('强制更新失败: $e');
      return false;
    }
  }

  List<PluginInfo> getPlugins() => [
        PluginInfo(fileName: 'ai_prompts.yaml', description: '提示词配置', lastUpdateTime: _getTime(_promptsKey)),
        PluginInfo(fileName: 'ai_models.yaml', description: 'AI模型配置', lastUpdateTime: _getTime(_modelsKey)),
      ];

  // 提示词
  String get translateRole => _prompts['translate_role'] ?? '';
  String get translatePrompt => _prompts['translate_prompt'] ?? '';
  String get summarizeOneLineRole => _prompts['summarize_oneline_role'] ?? '';
  String get summarizeOneLinePrompt => _prompts['summarize_oneline_prompt'] ?? '';
  String get longSummaryRole => _prompts['long_summary_role'] ?? '';
  String get shortSummaryRole => _prompts['short_summary_role'] ?? '';
  String get longSummaryResult => _prompts['long_summary_result'] ?? '';
  String get commonTags => _prompts['common_tags'] ?? '';
  String get htmlToMarkdownRole => _prompts['html_to_markdown_role'] ?? '';
  String get bookRecommendByCategory => _prompts['book_recommend_by_category'] ?? '';
  String get bookViewpoint => _prompts['book_viewpoint'] ?? '';
  String get bookInfo => _prompts['book_info'] ?? '';
  String get bookSearch => _prompts['book_search'] ?? '';
  String get weeklySummaryTemplate => _prompts['weekly_summary_template'] ?? '';

  List<AiModel> get aiModels => _models;
}

/// API提供商预设模型
class AiModel {
  final String name, apiAddress;
  final List<String> models;
  AiModel({required this.name, required this.apiAddress, required this.models});
}

/// 插件信息
class PluginInfo {
  final String fileName, description;
  final DateTime? lastUpdateTime;
  PluginInfo({required this.fileName, required this.description, this.lastUpdateTime});
}
