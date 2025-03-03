import 'package:get/get.dart';
import 'package:open_file/open_file.dart';
import 'package:package_info_plus/package_info_plus.dart';
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
  static const String _githubReleaseAPI = 'https://api.github.com/repos/SatoriTours/Daily/releases/latest';

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
    showFullScreenLoading();
    try {
      if (await check()) {
        await _downAndInstallApp();
      } else {
        successNotice('当前已是最新版本');
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
    if (!needUpgrade || !isProduction) return;

    await showConfirmationDialog(
      '检测到新版本',
      '当前版本 [$_currentVersion], 最新版本 [$_latestVersion]\n请确认是否下载更新',
      onConfirmed: () async {
        if (!await _requestInstallPermission()) {
          errorNotice('需要安装权限才能继续');
          return;
        }

        showFullScreenLoading();
        try {
          final appFilePath = await HttpService.i.downloadFile(_downloadURL);
          logger.i("下载文件到: $appFilePath");

          if (appFilePath.isNotEmpty) {
            final result = await OpenFile.open(appFilePath);
            logger.i("打开文件结果: ${result.type} ${result.message}");
          }
        } catch (e) {
          logger.e("下载或安装失败: $e");
          errorNotice("更新失败，请稍后重试");
        } finally {
          Get.close();
        }
      },
    );
  }

  // 获取当前版本号
  Future<void> _getCurrentVersion() async {
    _currentVersion = await getAppVersion();
  }

  // 从Github获取最新版本信息
  Future<void> _getLatestVersionFromGithub() async {
    try {
      final response = await HttpService.i.dio.get(_githubReleaseAPI);
      if (response.statusCode == 200) {
        final data = response.data;
        _latestVersion = data['tag_name'] as String;
        _downloadURL = data['assets'][0]['browser_download_url'] as String;
      } else {
        throw Exception('获取版本信息失败: ${response.statusCode}');
      }
    } catch (e) {
      logger.e("获取Github版本信息失败: $e");
      rethrow;
    }
  }
}
