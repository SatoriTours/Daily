import 'package:flutter/material.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/components/dream_webview/dream_webview.dart';
import 'package:daily_satori/app/modules/share_dialog/controllers/share_dialog_controller.dart';

/// 分享对话框视图
class ShareDialogView extends GetView<ShareDialogController> {
  const ShareDialogView({super.key});

  @override
  Widget build(BuildContext context) {
    _processArguments();

    return Scaffold(appBar: _buildAppBar(context), body: _buildBody(context));
  }

  /// 构建AppBar
  PreferredSizeWidget _buildAppBar(BuildContext context) {
    return AppBar(
      leading: IconButton(
        icon: const Icon(Icons.home),
        onPressed: () => controller.webViewController?.loadUrl(controller.shareURL.value),
      ),
      title: Obx(
        () =>
            controller.webLoadProgress.value > 0
                ? LinearProgressIndicator(
                  value: controller.webLoadProgress.value,
                  backgroundColor: Colors.grey[200],
                  valueColor: const AlwaysStoppedAnimation<Color>(Colors.blue),
                )
                : const SizedBox.shrink(),
      ),
      centerTitle: true,
      actions: [
        IconButton(icon: const Icon(Icons.arrow_back), onPressed: () => controller.webViewController?.goBack()),
        IconButton(icon: const Icon(Icons.arrow_forward), onPressed: () => controller.webViewController?.goForward()),
        IconButton(icon: const Icon(Icons.refresh), onPressed: () => controller.webViewController?.reload()),
        IconButton(icon: const Icon(Icons.translate), onPressed: () => controller.webViewController?.translatePage()),
      ],
    );
  }

  /// 构建主体内容
  Widget _buildBody(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Expanded(child: _buildWebContent()),
          const SizedBox(height: 10),
          _buildCommentField(),
          const SizedBox(height: 10),
          _buildActionButtons(),
        ],
      ),
    );
  }

  /// 构建网页内容
  Widget _buildWebContent() {
    return Container(
      decoration: BoxDecoration(border: Border.all(color: Colors.grey, width: 1)),
      child: Obx(
        () => DreamWebView(
          url: controller.shareURL.value,
          onWebViewCreated: controller.onWebViewCreated,
          onProgressChanged: controller.updateWebLoadProgress,
        ),
      ),
    );
  }

  /// 构建备注输入框
  Widget _buildCommentField() {
    return TextField(
      controller: controller.commentController,
      decoration: const InputDecoration(labelText: "备注", border: OutlineInputBorder()),
      maxLines: null,
      keyboardType: TextInputType.multiline,
    );
  }

  /// 构建操作按钮
  Widget _buildActionButtons() {
    return Row(
      children: [
        Expanded(
          child: TextButton(
            style: TextButton.styleFrom(backgroundColor: Colors.grey, foregroundColor: Colors.white),
            child: const Text("取消"),
            onPressed: () => controller.clickChannelBtn(),
          ),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: TextButton(
            style: TextButton.styleFrom(backgroundColor: Colors.blue, foregroundColor: Colors.white),
            child: const Text("保存"),
            onPressed: () => controller.onSaveButtonPressed(),
          ),
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
