/// 透明度常量定义
///
/// 提供统一的透明度值，确保整个应用使用一致的透明度级别
library;

/// 透明度常量类
class Opacities {
  // 私有构造函数，防止实例化
  Opacities._();

  // 极低透明度 (1%)
  static const double veryLow = 0.01;

  // 超低透明度 (3%)
  static const double ultraLow = 0.03;

  // 极浅透明度 (5%)
  static const double extraLow = 0.05;

  // 低透明度 (10%)
  static const double low = 0.1;

  // 中等低透明度 (15%)
  static const double mediumLow = 0.15;

  // 中等透明度 (20%)
  static const double medium = 0.2;

  // 中等高透明度 (25%)
  static const double mediumHigh = 0.25;

  // 高透明度 (30%)
  static const double high = 0.3;

  // 较高透明度 (40%)
  static const double higher = 0.4;

  // 半透明度 (50%)
  static const double half = 0.5;

  // 较高不透明度 (60%)
  static const double higherOpaque = 0.6;

  // 高不透明度 (70%)
  static const double highOpaque = 0.7;

  // 中等不透明度 (80%)
  static const double mediumOpaque = 0.8;

  // 低不透明度 (85%)
  static const double lowOpaque = 0.85;

  // 极低不透明度 (90%)
  static const double veryLowOpaque = 0.9;

  // 几乎不透明 (95%)
  static const double almostOpaque = 0.95;

  // 完全透明
  static const double transparent = 0.0;

  // 完全不透明
  static const double opaque = 1.0;
}