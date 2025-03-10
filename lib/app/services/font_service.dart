import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'package:google_fonts/google_fonts.dart';

import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/global.dart';

class FontService {
  // 私有构造函数
  FontService._();

  // 单例实例
  static final FontService _instance = FontService._();

  // 单例访问器
  static FontService get i => _instance;

  // 字体许可证文件路径
  static const String _fontLicensePath = 'assets/fonts/google/OFL.txt';

  /// 初始化字体服务
  Future<void> init() async {
    logger.i("[初始化服务] FontService");

    // 仅在生产环境下配置字体
    if (isProduction) {
      _configureProductionFonts();
    }
    logger.i('字体服务初始化完成');
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
