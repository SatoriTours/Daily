import 'package:daily_satori/app/services/http_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';
import 'package:path/path.dart' as path;

class PluginService {
  PluginService._();

  static final PluginService _instance = PluginService._();
  static PluginService get i => _instance;

  Future<void> init() async {
    logger.i("[初始化服务] PluginService");
    updateAllPlugins();
  }

  String getTranslateRole() {
    return getPluginContentByKey('translate_role');
  }

  String getTranslatePrompt() {
    return getPluginContentByKey('translate_prompt');
  }

  String getSummarizeOneLineRole() {
    return getPluginContentByKey('summarize_oneline_role');
  }

  String getSummarizeOneLinePrompt() {
    return getPluginContentByKey('summarize_oneline_prompt');
  }

  String getLongSummaryRole() {
    return getPluginContentByKey('long_summary_role');
  }

  String getShortSummaryRole() {
    return getPluginContentByKey('short_summary_role');
  }

  String getLongSummaryResult() {
    return getPluginContentByKey('long_summary_result');
  }

  String getCommonTags() {
    return getPluginContentByKey('common_tags');
  }

  String getPluginContentByKey(String key) {
    var content = SettingService.i.getSetting(_pluginKey(key));

    // 如果没有则返回本地内容
    if (content.isEmpty) {
      content = _localPlugins[key] ?? '';
    }
    return content;
  }

  Future<void> updateAllPlugins() async {
    for (final key in _localPlugins.keys) {
      final url = path.join(SettingService.i.getSetting(SettingService.pluginKey), key);
      await updatePlugin(key, url);
    }
  }

  Future<void> updatePlugin(String key, String url) async {
    final response = await HttpService.i.getTextContent(url);
    final content = response.trim();
    if (content.isNotEmpty) {
      // 保存到 ObjectBox
      await SettingService.i.saveSetting(_pluginKey(key), content);

      logger.i('插件 $key 更新成功');
    }
  }

  String _pluginKey(String key) {
    return 'plugin_$key';
  }

  final Map<String, String> _localPlugins = {
    'translate_role': '你是一个翻译助手, 能够将任何文本翻译成中文',
    'translate_prompt': '''
请将以下文本翻译成中文：
```
{{text}}
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
    "cases": ["关键案例1"]
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
