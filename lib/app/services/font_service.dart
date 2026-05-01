import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'package:google_fonts/google_fonts.dart';

import 'package:daily_satori/app/utils/app_info_utils.dart';
import 'package:daily_satori/app/config/app_config.dart';
import 'package:daily_satori/app/services/service_base.dart';

/// 字体服务类
///
/// 负责管理应用程序的字体配置，包括：
/// - 配置 Google Fonts
/// - 注册字体许可证
/// - 管理字体加载行为
class FontService extends AppService {
  // 私有构造函数
  FontService._();

  // 单例实例
  static final FontService _instance = FontService._();

  /// 获取 FontService 实例
  static FontService get i => _instance;

  @override
  ServicePriority get priority => ServicePriority.high;

  /// 字体许可证文件路径（已迁移至 UrlConfig）
  static String get _fontLicensePath => UrlConfig.fontLicensePath;

  @override
  Future<void> init() async {
    if (AppInfoUtils.isProduction) {
      _configureProductionFonts();
    }
  }

  /// 配置生产环境字体设置
  void _configureProductionFonts() {
    // 禁用运行时字体获取
    GoogleFonts.config.allowRuntimeFetching = false;

    // 注册字体许可证
    LicenseRegistry.addLicense(() async* {
      final license = await rootBundle.loadString(_fontLicensePath);
      yield LicenseEntryWithLineBreaks(['assets/fonts/google'], license);
    });
  }
}
