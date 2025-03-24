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

  // 插件配置内容
  late Map<String, String> _localPlugins = {};
  final String _pluginsYamlPath = 'assets/configs/plugins.yaml';

  // 初始化插件服务，日志记录并更新所有插件配置
  Future<void> init() async {
    logger.i("[初始化服务] PluginService");

    // 从YAML文件加载默认插件配置
    await _loadLocalPlugins();

    // 更新所有插件配置
    _updateAllPlugins();
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
