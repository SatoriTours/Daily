library;

/// Daily Satori 应用程序的主要导出文件
///
/// 这个文件集中导出了应用程序中常用的组件、模型、服务和工具类，
/// 使其他文件可以通过单一导入获取所需的依赖。

// 导出工具类
export 'app/utils/utils.dart';

// 导出 Flutter
export 'package:flutter/material.dart';

// 导出数据层（模型和仓储）
export 'app/data/data.dart';

// 导出常用服务
export 'app/services/services.dart';

// 导出常用路由
export 'app/routes/routes.dart';

export 'package:daily_satori/app/providers/providers.dart';
export 'package:daily_satori/app/components/components.dart';

export 'package:flutter_riverpod/flutter_riverpod.dart';
export 'package:feather_icons/feather_icons.dart';
export 'package:share_plus/share_plus.dart';

// 导出样式系统
export 'app/styles/styles.dart';
