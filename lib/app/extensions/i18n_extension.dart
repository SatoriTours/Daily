import 'package:daily_satori/app/services/i18n/i18n_service.dart';

/// 国际化扩展方法
extension I18nExtension on String {
  /// 翻译字符串
  ///
  /// 使用方式：
  /// ```dart
  /// 'button.save'.t  // 获取保存按钮的文本
  /// 'title.settings'.t  // 获取设置页面的标题
  /// ```
  String get t {
    return I18nService.i.t(this);
  }
}