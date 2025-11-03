/// URL配置
class UrlConfig {
  UrlConfig._();

  // GitHub相关
  static const String githubReleaseApi = 'https://api.github.com/repos/SatoriTours/Daily/releases/latest';
  static const String githubReleaseApiMirror =
      'https://mirror.ghproxy.com/https://api.github.com/repos/SatoriTours/Daily/releases/latest';

  // AdBlock相关
  static const String easylistUrl = 'https://easylist-downloads.adblockplus.org/v3/full/easylistchina+easylist.txt';
  static const String localEasylistFile = 'assets/easylistchina+easylist.txt';

  // 字体相关
  static const String fontLicensePath = 'assets/fonts/google/OFL.txt';
}
