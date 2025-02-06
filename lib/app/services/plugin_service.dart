import 'package:daily_satori/app/services/http_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:path/path.dart' as path;

class PluginService {
  // 私有构造函数，确保外部无法直接创建实例
  PluginService._();

  static final PluginService _instance = PluginService._();
  static PluginService get i => _instance;

  // 初始化插件服务，日志记录并更新所有插件配置
  Future<void> init() async {
    logger.i("[初始化服务] PluginService");
    await _updateAllPlugins();
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

  // 内部方法：根据键名获取插件内容；若未设置则返回默认本地内容
  String _getPluginContent(String key) {
    String content = SettingService.i.getSetting(_pluginKey(key));
    if (content.isEmpty) {
      content = _localPlugins[key] ?? '';
    }
    return content;
  }

  // 内部方法：更新所有插件配置
  Future<void> _updateAllPlugins() async {
    final String baseUrl = SettingService.i.getSetting(SettingService.pluginKey);
    for (final key in _localPlugins.keys) {
      final String url = path.join(baseUrl, key);
      await _updatePlugin(key, url);
    }
  }

  // 内部方法：更新单个插件配置
  Future<void> _updatePlugin(String key, String url) async {
    final String response = await HttpService.i.getTextContent(url);
    final String content = response.trim();
    if (content.isNotEmpty) {
      await SettingService.i.saveSetting(_pluginKey(key), content);
      logger.i('插件 $key 更新成功');
    }
  }

  // 内部辅助方法：构造在 SettingService 中存储插件内容的键名
  String _pluginKey(String key) => 'plugin_$key';

  // 默认的本地插件配置
  final Map<String, String> _localPlugins = {
    'translate_role': '你是一个翻译助手, 能够将任何文本翻译成中文',
    'translate_prompt': '''
请将以下文本翻译成中文：{{text}}
```
注意事项：
1. 保持原文的意思和语气。
2. 确保翻译流畅自然。
3. 如果有专业术语，请尽量使用常见的翻译。
4. 请注意```内的内容是附加信息，不包括 ```, 翻译时要保持其完整性。
''',
    'summarize_oneline_role':
        '你是一个文章读者, 总结一个能表达文章核心内容并且能吸引别人阅读的标题,保持原文的意思，注意:不使用"文章提到"或类似的表达方式，一定不要添加个人观点, 标题不要加入引号等特殊字符',
    'summarize_oneline_prompt': '一句话总结一下内容：```{{text}}```',
    'long_summary_role': '''
用户将给出一段文章, 你将根据文章的内容, 按照如下要求并使用json格式输出。

总结要求：
1. 输出内容为纯文本,不包含任何markdown或其他排版格式.
2. 不要以"文章主要介绍"或类似的表达方式，直接输出内容就可以.
3. summary不超100字以内.
4. key_content 是文章中最关键要表达的内容, 总结的详细一点,最多5个.
5. case 是文章最关键的案例或数据, 总结的详细一点,最多3个.
6. tags 从 {{commonTags}} 中选择最合适的标签, 最多3个。
7. 所有的内容使用中文输出.

EXAMPLE JSON OUTPUT:
{
    "summary": "核心内容",
    "key_contents": ["关键内容1"],
    "cases": ["关键案例1"],
    "tags": ["标签1"]
}
''',
    'short_summary_role': '''
用户将给出一段文章, 你将根据文章的内容, 按照如下要求并使用json格式输出。

总结要求：
1. 输出内容为纯文本,不包含任何markdown或其他排版格式.
2. 不要以"文章主要介绍"或类似的表达方式，直接输出内容就可以.
3. summary不超100字以内.
6. tags 从 {{commonTags}} 中选择最合适的标签, 最多3个。
7. 所有的内容使用中文输出.

EXAMPLE JSON OUTPUT:
{
    "summary": "文章的核心内容",
    "tags": ["标签1"]
}
''',
    'long_summary_result': '''
概述:

{{summary}}

关键内容:

{{keyContents}}

关键案例:

{{cases}}
''',
    'common_tags': '软件,硬件,生活,效率,新闻,工具,成长,设计,健康,AI,互联网,云计算',
  };
}
