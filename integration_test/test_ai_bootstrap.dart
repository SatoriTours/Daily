// ignore_for_file: avoid_print

import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/services/setting_service/setting_service.dart';

import '../test/test_config.dart';

class TestAiBootstrap {
  TestAiBootstrap._();

  static Future<void> configureFromEnv() async {
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
    final general = AIConfigRepository.i.getGeneralConfig();
    if (general == null) {
      // 正常情况下 ServiceRegistry 会初始化默认配置；如果这里为空，就不强行创建，避免破坏初始化流程。
      return;
    }

    general.apiToken = token;
    general.apiAddress = baseUrl;
    if (model.isNotEmpty) {
      general.modelName = model;
    }
    general.isDefault = true;
    AIConfigRepository.i.save(general);
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
