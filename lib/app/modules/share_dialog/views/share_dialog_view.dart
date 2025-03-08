import 'package:flutter/material.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/modules/share_dialog/controllers/share_dialog_controller.dart';
import 'package:daily_satori/app/modules/share_dialog/views/widgets/web_view_app_bar.dart';
import 'package:daily_satori/app/modules/share_dialog/views/widgets/web_content_view.dart';
import 'package:daily_satori/app/modules/share_dialog/views/widgets/comment_field.dart';
import 'package:daily_satori/app/modules/share_dialog/views/widgets/action_buttons.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/global.dart';

/// 分享对话框视图
class ShareDialogView extends GetView<ShareDialogController> {
  const ShareDialogView({super.key});

  @override
  Widget build(BuildContext context) {
    _processArguments();

    return Scaffold(appBar: WebViewAppBar(controller: controller), body: _buildBody());
  }

  /// 构建主体内容
  Widget _buildBody() {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Expanded(child: WebContentView(controller: controller)),
          const SizedBox(height: 10),
          CommentField(controller: controller),
          const SizedBox(height: 10),
          ActionButtons(controller: controller),
        ],
      ),
    );
  }

  /// 处理传入的参数
  void _processArguments() {
    if (Get.arguments?['shareURL'] != null) {
      controller.shareURL = Get.arguments?['shareURL'];
      logger.i("接收到分享的链接 ${controller.shareURL}");
    }

    if (Get.arguments?['update'] != null) {
      controller.isUpdate = Get.arguments?['update'];
      logger.i("收到更新参数 ${controller.isUpdate}");
    }

    if (Get.arguments?['articleID'] != null) {
      controller.articleID = Get.arguments?['articleID'];
      logger.i("收到文章ID参数 ${controller.articleID}");
    }

    if (Get.arguments?['needBackToApp'] != null) {
      controller.needBackToApp = Get.arguments?['needBackToApp'];
      logger.i("收到返回应用参数 ${controller.needBackToApp}");
    }
  }
}
