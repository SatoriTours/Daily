import 'package:flutter/material.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/modules/share_dialog/controllers/share_dialog_controller.dart';

/// 分享对话框视图
class ShareDialogView extends GetView<ShareDialogController> {
  const ShareDialogView({super.key});

  @override
  Widget build(BuildContext context) {
    _processArguments();

    return Dialog(
      insetPadding: const EdgeInsets.symmetric(horizontal: 20.0, vertical: 20.0),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: Container(
        width: MediaQuery.of(context).size.width * 0.9,
        constraints: BoxConstraints(maxHeight: MediaQuery.of(context).size.height * 0.6),
        padding: const EdgeInsets.all(16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _buildHeader(context),
            const SizedBox(height: 16),
            _buildUrlDisplay(),
            const SizedBox(height: 16),
            _buildCommentField(),
            const SizedBox(height: 16),
            _buildActionButtons(),
          ],
        ),
      ),
    );
  }

  /// 构建标题栏
  Widget _buildHeader(BuildContext context) {
    return Row(
      children: [
        const Icon(Icons.bookmark_add, color: Colors.blue),
        const SizedBox(width: 8),
        Expanded(
          child: Obx(
            () => Text(
              controller.shareURL.value.isNotEmpty ? '保存链接' : '添加备注',
              style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),
          ),
        ),
        IconButton(
          icon: const Icon(Icons.close, size: 20),
          onPressed: () => controller.clickChannelBtn(),
          padding: EdgeInsets.zero,
          constraints: const BoxConstraints(),
        ),
      ],
    );
  }

  /// 构建网页链接显示区域
  Widget _buildUrlDisplay() {
    return Obx(
      () =>
          controller.shareURL.value.isNotEmpty
              ? Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  border: Border.all(color: Colors.grey.shade300),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text("网页链接:", style: TextStyle(fontWeight: FontWeight.bold)),
                    const SizedBox(height: 4),
                    Text(
                      controller.shareURL.value,
                      style: TextStyle(color: Colors.blue.shade700),
                      maxLines: 3,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              )
              : const SizedBox.shrink(),
    );
  }

  /// 构建备注输入框
  Widget _buildCommentField() {
    return Expanded(
      child: TextField(
        controller: controller.commentController,
        decoration: const InputDecoration(
          labelText: "备注信息",
          hintText: "添加备注信息（可选）",
          border: OutlineInputBorder(),
          contentPadding: EdgeInsets.symmetric(horizontal: 12, vertical: 16),
        ),
        maxLines: null,
        keyboardType: TextInputType.multiline,
        textInputAction: TextInputAction.newline,
      ),
    );
  }

  /// 构建操作按钮
  Widget _buildActionButtons() {
    return Row(
      mainAxisAlignment: MainAxisAlignment.end,
      children: [
        TextButton(
          style: TextButton.styleFrom(
            foregroundColor: Colors.grey[700],
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
          ),
          child: const Text("取消"),
          onPressed: () => controller.clickChannelBtn(),
        ),
        const SizedBox(width: 10),
        ElevatedButton(
          style: ElevatedButton.styleFrom(
            backgroundColor: Colors.blue,
            foregroundColor: Colors.white,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
          ),
          child: const Text("保存"),
          onPressed: () => controller.onSaveButtonPressed(),
        ),
      ],
    );
  }

  /// 处理传入的参数
  void _processArguments() {
    if (Get.arguments?['shareURL'] != null) {
      controller.updateShareURL(Get.arguments?['shareURL']);
    }

    if (Get.arguments?['update'] != null) {
      controller.updateIsUpdate(Get.arguments?['update']);
    }

    if (Get.arguments?['articleID'] != null) {
      controller.updateArticleID(Get.arguments?['articleID']);
    }

    if (Get.arguments?['needBackToApp'] != null) {
      controller.updateNeedBackToApp(Get.arguments?['needBackToApp']);
    }
  }
}
