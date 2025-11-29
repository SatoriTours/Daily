import 'package:flutter/material.dart';

/// AI 配置类型常量
///
/// 集中管理 AI 配置的类型定义、图标和颜色
class AIConfigTypes {
  AIConfigTypes._();

  // ========================================================================
  // 类型常量
  // ========================================================================

  /// 通用配置
  static const int general = 0;

  /// 文章总结
  static const int articleSummary = 1;

  /// 书本解读
  static const int bookInterpretation = 2;

  /// 日记总结
  static const int diarySummary = 3;

  // ========================================================================
  // 类型名称
  // ========================================================================

  /// 获取类型名称
  static String getName(int type) {
    switch (type) {
      case general:
        return '通用配置';
      case articleSummary:
        return '文章总结';
      case bookInterpretation:
        return '书本解读';
      case diarySummary:
        return '日记总结';
      default:
        return '未知类型';
    }
  }

  // ========================================================================
  // 类型图标
  // ========================================================================

  /// 获取类型图标
  static IconData getIcon(int type) {
    switch (type) {
      case general:
        return Icons.settings;
      case articleSummary:
        return Icons.article;
      case bookInterpretation:
        return Icons.book;
      case diarySummary:
        return Icons.edit_note;
      default:
        return Icons.settings;
    }
  }

  // ========================================================================
  // 类型颜色
  // ========================================================================

  /// 获取类型颜色
  static Color getColor(int type) {
    switch (type) {
      case general:
        return Colors.blue;
      case articleSummary:
        return Colors.green;
      case bookInterpretation:
        return Colors.orange;
      case diarySummary:
        return Colors.purple;
      default:
        return Colors.blue;
    }
  }

  // ========================================================================
  // 类型判断
  // ========================================================================

  /// 判断是否为系统预设配置类型（不可修改名称）
  ///
  /// 系统配置包括：通用配置、文章总结、书本解读、日记总结
  static bool isSystemConfig(int type) {
    return type >= general && type <= diarySummary;
  }

  /// 判断是否为特殊配置类型（可以继承通用配置）
  ///
  /// 特殊配置包括：文章总结、书本解读、日记总结
  static bool isSpecialConfig(int type) {
    return type >= articleSummary && type <= diarySummary;
  }
}
