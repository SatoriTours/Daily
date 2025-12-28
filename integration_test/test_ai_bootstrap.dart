// ignore_for_file: avoid_print

import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';

import 'test_config.dart';

class TestAiBootstrap {
  TestAiBootstrap._();

  static Future<void> configureFromEnv() async {
    TestConfig.printConfig();

    final token = TestConfig.aiToken.trim();
    final rawUrl = TestConfig.aiUrl.trim();
    final model = TestConfig.aiModel.trim();

    if (token.isEmpty || rawUrl.isEmpty) {
      return;
    }

    final baseUrl = _normalizeBaseUrl(rawUrl);

    // 1) 写入 Setting（作为 AIConfig 为空时的兜底）
    SettingRepository.i.saveSetting(SettingService.openAITokenKey, token);
    SettingRepository.i.saveSetting(SettingService.openAIAddressKey, baseUrl);

    // 2) 写入 AIConfig（作为全局默认配置）
    // 某些测试场景下默认配置可能尚未初始化；这里补一次默认配置以保证可用。
    AIConfigRepository.i.initDefaultConfigs();

    final general = AIConfigRepository.i.getGeneralConfig();
    if (general == null) return;

    general.apiToken = token;
    general.apiAddress = baseUrl;
    if (model.isNotEmpty) {
      general.modelName = model;
    }
    general.isDefault = true;
    AIConfigRepository.i.save(general);

    // 3) 文章分析配置确保可用（部分逻辑可能优先读取 functionType=1 的默认配置）
    final articleDefault = AIConfigRepository.i.getDefaultAIConfigByFunctionType(1);
    if (articleDefault != null) {
      articleDefault.inheritFromGeneral = true;
      // 为了避免继承链断裂，这里也同步写入，确保服务能拿到 token/baseUrl
      articleDefault.apiToken = token;
      articleDefault.apiAddress = baseUrl;
      if (model.isNotEmpty) {
        articleDefault.modelName = model;
      }
      articleDefault.isDefault = true;
      AIConfigRepository.i.save(articleDefault);
    }
  }

  static String _normalizeBaseUrl(String url) {
    final uri = Uri.tryParse(url);
    if (uri == null) return url;

    var path = uri.path;
    // 常见误配：传入了 /v1/chat/completions 或 /chat/completions
    path = path.replaceAll(RegExp(r'/chat/completions/?$'), '');
    path = path.replaceAll(RegExp(r'/v1/chat/completions/?$'), '/v1');

    final normalized = uri.replace(path: path).toString();
    // 避免末尾多一个 /
    return normalized.endsWith('/') ? normalized.substring(0, normalized.length - 1) : normalized;
  }
}
