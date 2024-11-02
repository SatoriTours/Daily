import 'package:daily_satori/app/services/http_service.dart';
import 'package:daily_satori/global.dart';
import 'package:get/get.dart';
import 'package:open_file/open_file.dart';
import 'package:package_info_plus/package_info_plus.dart';

class AppUpgradeService {
  AppUpgradeService._privateConstructor();
  static final AppUpgradeService _instance = AppUpgradeService._privateConstructor();
  static AppUpgradeService get i => _instance;

  static const String _githubReleaseAPI = 'https://api.github.com/repos/SatoriTours/Daily/releases/latest';

  late String _version;
  late String _githubLatestVersion;
  late String _downloadURL;

  String get downloadURL => _downloadURL;
  bool get needUpgrade => _version != _githubLatestVersion;

  Future<void> init() async {
    logger.i("[初始化服务] AppUpgradeService");
  }

  Future<void> checkAndDownload() async {
    showFullScreenLoading();
    if (await AppUpgradeService.i.check()) {
      Get.close();
      await AppUpgradeService.i.downAndInstallApp();
    } else {
      Get.close();
      successNotice('没有新版本');
    }
  }

  Future<void> checkAndDownloadInbackend() async {
    if (await AppUpgradeService.i.check()) {
      await AppUpgradeService.i.downAndInstallApp();
    }
  }

  Future<bool> check() async {
    await _getCurrentVersion();
    await _getLatestVersionFromGithub();
    logger.i("version => $_version, github version => $_githubLatestVersion, $needUpgrade, $_downloadURL");
    return needUpgrade;
  }

  Future<void> downAndInstallApp() async {
    // if (needUpgrade && isProduction) {
    if (needUpgrade &&
        await showConfirmationDialog('检测到新版本', '当前版本 [$_version], 最新版本 [$_githubLatestVersion]\n请确认是否下载更新')) {
      String appFilePath = await HttpService.i.downloadFile(_downloadURL);
      logger.i("下载文件到 $appFilePath");
      if (appFilePath.isNotEmpty) {
        showFullScreenLoading(title: '开始更新: 从 $_version 更新到 $_githubLatestVersion');
        await OpenFile.open(appFilePath);
      }
    }
  }

  Future<void> _getCurrentVersion() async {
    PackageInfo packageInfo = await PackageInfo.fromPlatform();
    _version = "v${packageInfo.version}";
  }

  Future<void> _getLatestVersionFromGithub() async {
    final response = await HttpService.i.dio.get(_githubReleaseAPI);
    if (response.statusCode == 200) {
      _githubLatestVersion = response.data['tag_name'] as String;
      _downloadURL = response.data['assets'][0]['browser_download_url'] as String;
    } else {
      logger.e("无法获取最新版本, 状态码: ${response.statusCode}");
      throw Exception("无法获取最新版本");
    }
  }
}
