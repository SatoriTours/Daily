library;

/// Daily Satori 应用程序的主要导出文件
///
/// 这个文件集中导出了应用程序中常用的组件、模型、服务和工具类，
/// 使其他文件可以通过单一导入获取所需的依赖。

// 导出工具类
export 'app/utils/utils.dart';

// 导出所有模型
export 'package:flutter/material.dart' hide VoidCallback;
export 'package:get/get.dart';
export 'app/models/models.dart';

// 导出所有仓储
export 'app/repositories/repositories.dart';

// 导出常用服务
export 'app/services/services.dart';

// 导出常用路由
export 'app/routes/app_pages.dart';

// 导出基础控制器
export 'app/controllers/base_controller.dart';
