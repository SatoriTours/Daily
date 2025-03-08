import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app/routes/app_pages.dart';
import 'package:daily_satori/app/services/web_service/web_service.dart';
import 'package:daily_satori/app/styles/app_theme.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

/// 文章列表页面的AppBar组件
class ArticlesAppBar extends StatelessWidget implements PreferredSizeWidget {
  final ArticlesController controller;

  const ArticlesAppBar({super.key, required this.controller});

  @override
  Widget build(BuildContext context) {
    final textTheme = AppTheme.getTextTheme(context);

    return AppBar(
      title: Obx(() => Text(_getTitle(), style: textTheme.titleLarge?.copyWith(color: Colors.white))),
      centerTitle: true,
      leading: IconButton(icon: const Icon(Icons.menu), onPressed: () => Get.toNamed(Routes.LEFT_BAR)),
      actions: [
        IconButton(icon: const Icon(Icons.search), onPressed: controller.toggleSearchState),
        _buildConnectionIndicator(),
      ],
    );
  }

  /// 构建WebSocket连接状态指示器
  Widget _buildConnectionIndicator() {
    return Obx(() {
      final isConnected = WebService.i.webSocketTunnel.isConnected.value;
      return IconButton(icon: Icon(Icons.circle, color: isConnected ? Colors.green : Colors.red), onPressed: () {});
    });
  }

  /// 获取标题文本
  String _getTitle() => controller.getTitle();

  @override
  Size get preferredSize => const Size.fromHeight(kToolbarHeight);
}
