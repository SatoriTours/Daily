import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'package:google_fonts/google_fonts.dart';

import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/global.dart';

class FontService {
  FontService._privateConstructor();
  static final FontService _instance = FontService._privateConstructor();
  static FontService get i => _instance;

  Future<void> init() async {
    logger.i("[初始化服务] FontService");
    if (isProduction) {
      GoogleFonts.config.allowRuntimeFetching = false;
      LicenseRegistry.addLicense(() async* {
        final license =
            await rootBundle.loadString('assets/fonts/google/OFL.txt');
        yield LicenseEntryWithLineBreaks(['assets/fonts/google'], license);
      });
    }
  }
}
