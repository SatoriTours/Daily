import 'dart:io';
import 'package:dio/dio.dart' show DioException, Options;
import 'package:get/get.dart';
import 'package:open_file/open_file.dart';
import 'package:permission_handler/permission_handler.dart';

import 'package:daily_satori/app/services/http_service.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/global.dart';

class AppUpgradeService {
  // 单例模式
  AppUpgradeService._();
  static final AppUpgradeService _instance = AppUpgradeService._();
  static AppUpgradeService get i => _instance;

  // 常量定义
  static const String _githubReleaseApi = 'https://api.github.com/repos/SatoriTours/Daily/releases/latest';
  static const String _githubReleaseApiMirror =
      'https://mirror.ghproxy.com/https://api.github.com/repos/SatoriTours/Daily/releases/latest';

  // 版本信息
  late String _currentVersion;
  late String _latestVersion;
  late String _downloadURL;

  String get downloadURL => _downloadURL;
  bool get needUpgrade => _currentVersion != _latestVersion;

  // 初始化服务
  Future<void> init() async {
    logger.i("[初始化服务] AppUpgradeService");
  }

  // 检查并下载新版本(带UI提示)
  Future<void> checkAndDownload() async {
    UIUtils.showLoading();
    try {
      if (await check()) {
        await _downAndInstallApp();
      } else {
        UIUtils.showSuccess('当前已是最新版本');
      }
    } finally {
      Get.close();
    }
  }

  // 后台检查并下载新版本
  Future<void> checkAndDownloadInbackend() async {
    if (await check()) {
      await _downAndInstallApp();
    }
  }

  // 请求安装权限
  Future<bool> _requestInstallPermission() async {
    final status = await Permission.requestInstallPackages.status;
    if (status.isDenied) {
      final result = await Permission.requestInstallPackages.request();
      return result.isGranted;
    }
    return true;
  }

  // 检查版本
  Future<bool> check() async {
    try {
      await Future.wait([_getCurrentVersion(), _getLatestVersionFromGithub()]);
      logger.i("当前版本: $_currentVersion, 最新版本: $_latestVersion, 需要更新: $needUpgrade");
      return needUpgrade;
    } catch (e) {
      logger.e("检查版本失败: $e");
      return false;
    }
  }

  // 下载并安装新版本
  Future<void> _downAndInstallApp() async {
    if (!needUpgrade || !AppInfoUtils.isProduction) return;

    await DialogUtils.showConfirm(
      title: '检测到新版本',
      message: '当前版本 [$_currentVersion], 最新版本 [$_latestVersion]\n请确认是否下载更新',
      onConfirm: () async {
        // 仅 Android 需要安装未知来源权限
        if (GetPlatform.isAndroid) {
          if (!await _requestInstallPermission()) {
            UIUtils.showError('需要安装权限才能继续');
            return;
          }
        }

        UIUtils.showLoading();
        try {
          final appFilePath = await HttpService.i.downloadFile(_downloadURL);
          logger.i("下载文件到: $appFilePath");

          if (appFilePath.isEmpty) {
            UIUtils.showError('下载失败，请检查网络后重试');
            return;
          }

          // 指定 APK 的 MIME，其他类型走默认
          final isApk = appFilePath.toLowerCase().endsWith('.apk');
          final result = await OpenFile.open(
            appFilePath,
            type: isApk ? 'application/vnd.android.package-archive' : null,
          );
          logger.i("打开文件结果: ${result.type} ${result.message}");
          if (result.type != ResultType.done) {
            UIUtils.showError('打开安装文件失败: ${result.message}');
          }
        } catch (e) {
          logger.e("下载或安装失败: $e");
          UIUtils.showError("更新失败，请稍后重试");
        } finally {
          Get.close();
        }
      },
    );
  }

  // 获取当前版本号
  Future<void> _getCurrentVersion() async {
    _currentVersion = _normalizeVersion(await AppInfoUtils.getVersion());
  }

  // 从Github获取最新版本信息
  Future<void> _getLatestVersionFromGithub() async {
    try {
      final data = await _fetchLatestReleaseJsonWithFallback();
      _latestVersion = _normalizeVersion(data['tag_name'] as String);

      // 优先选择 APK 资源，其次回退到第一个资源
      final assets = (data['assets'] as List?) ?? const [];
      if (assets.isEmpty) {
        throw Exception('发布中未找到可下载资源');
      }
      String? apkUrl;
      for (final a in assets) {
        final url = a['browser_download_url'] as String?;
        if (url != null && url.toLowerCase().endsWith('.apk')) {
          apkUrl = url;
          break;
        }
      }
      // 优先使用 jsDelivr 作为国内下载源（若可转换）；否则用原始 GitHub 直链
      final rawUrl = apkUrl ?? (assets.first['browser_download_url'] as String);
      _downloadURL = _toJsDelivrIfPossible(rawUrl) ?? rawUrl;
    } catch (e) {
      logger.e("获取Github版本信息失败: $e");
      rethrow;
    }
  }

  // 依次尝试官方与镜像 API，返回 release JSON

  // 将 GitHub releases/download 直链转换为 jsDelivr（仅当可解析时返回，否则返回 null）
  // 形如：https://github.com/{owner}/{repo}/releases/download/{tag}/{file}
  // 转为：https://cdn.jsdelivr.net/gh/{owner}/{repo}@{tag}/{file}
  // 注意：如果文件并不在仓库对应 tag 的源码树中（仅作为 Release 附件上传），jsDelivr 可能无法提供；
  // 此时下载会失败，HttpService 会继续回退到原始直链或 ghproxy。
  String? _toJsDelivrIfPossible(String url) {
    try {
      final uri = Uri.parse(url);
      if (uri.host != 'github.com') return null;
      final seg = uri.pathSegments;
      // 期望 path: owner/repo/releases/download/tag/file...
      final i = seg.indexOf('releases');
      if (i <= 1 || i + 2 >= seg.length) return null;
      if (seg[i + 1] != 'download') return null;
      final owner = seg[i - 2];
      final repo = seg[i - 1];
      final tag = seg[i + 2];
      final filePath = seg.sublist(i + 3).join('/');
      if (owner.isEmpty || repo.isEmpty || tag.isEmpty || filePath.isEmpty) return null;
      return 'https://cdn.jsdelivr.net/gh/$owner/$repo@$tag/$filePath';
    } catch (_) {
      return null;
    }
  }

  Future<Map<String, dynamic>> _fetchLatestReleaseJsonWithFallback() async {
    Map<String, String> buildHeaders() {
      final base = <String, String>{
        'User-Agent': 'DailyApp/UpgradeChecker (+https://github.com/SatoriTours/Daily)',
        'Accept': 'application/vnd.github+json',
        'X-GitHub-Api-Version': '2022-11-28',
      };

      // 可选：支持通过环境变量提供 GitHub Token，以避免 403（未认证限流）。
      // Windows/macOS/Linux 桌面端可生效；移动端通常无此环境变量。
      final token = Platform.environment['GITHUB_TOKEN'] ?? Platform.environment['DS_GITHUB_TOKEN'];
      if (token != null && token.trim().isNotEmpty) {
        base['Authorization'] = 'Bearer ${token.trim()}';
      }
      return base;
    }

    Future<Map<String, dynamic>> doGet(String url) async {
      final resp = await HttpService.i.dio.get(url, options: Options(headers: buildHeaders()));
      if (resp.statusCode == 200 && resp.data is Map<String, dynamic>) {
        return Map<String, dynamic>.from(resp.data);
      }
      throw Exception('获取版本信息失败: ${resp.statusCode}');
    }

    // 尝试主站
    try {
      return await doGet(_githubReleaseApi);
    } catch (e) {
      // 403 多见于未携带 Token 导致的未认证限流；此时降级到镜像，不再用告警级别噪声提示。
      if (e is DioException) {
        final code = e.response?.statusCode;
        if (code == 403) {
          logger.i("GitHub 主站返回 403（可能触发未认证限流），已自动切换镜像获取版本信息。");
        } else {
          logger.w("GitHub 主站获取失败（HTTP $code），尝试镜像: ${e.message}");
        }
      } else {
        logger.w("GitHub 主站获取失败，尝试镜像: $e");
      }
      // 尝试镜像
      return await doGet(_githubReleaseApiMirror);
    }
  }

  // 版本标准化（去掉前缀 v/V）
  String _normalizeVersion(String v) {
    if (v.isEmpty) return v;
    final trimmed = v.trim();
    if (trimmed.startsWith('v') || trimmed.startsWith('V')) {
      return trimmed.substring(1);
    }
    return trimmed;
  }
}
