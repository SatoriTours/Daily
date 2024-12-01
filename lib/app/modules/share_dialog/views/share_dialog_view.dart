import 'package:flutter/material.dart';

import 'package:get/get.dart';

import 'package:daily_satori/app/compontents/dream_webview/dream_webview.dart';
import 'package:daily_satori/app/modules/share_dialog/controllers/share_dialog_controller.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/global.dart';

class ShareDialogView extends GetView<ShareDialogController> {
  const ShareDialogView({super.key});

  @override
  Widget build(BuildContext context) {
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

    return Scaffold(
      appBar: _buildAppBar(),
      body: Container(
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Expanded(child: _displayWebContent()),
            const SizedBox(height: 10),
            _buildCommentTextfield(),
            const SizedBox(height: 10),
            _buildActionButtons(),
          ],
        ),
      ),
    );
  }

  PreferredSizeWidget _buildAppBar() {
    return AppBar(
      leading: IconButton(
        icon: const Icon(Icons.home),
        onPressed: () {
          controller.webViewController?.loadUrl(controller.shareURL ?? '');
        },
      ),
      title: _displaySharedLink(),
      centerTitle: true,
      actions: [
        IconButton(
          icon: const Icon(Icons.arrow_back),
          onPressed: () {
            controller.webViewController?.goBack();
          },
        ),
        IconButton(
          icon: const Icon(Icons.arrow_forward),
          onPressed: () {
            controller.webViewController?.goForward();
          },
        ),
        IconButton(
          icon: const Icon(Icons.refresh),
          onPressed: () {
            controller.webViewController?.reload();
          },
        ),
        IconButton(
          icon: const Icon(Icons.translate),
          onPressed: () {
            controller.webViewController?.translatePage();
          },
        ),
      ],
    );
  }

  Widget _displaySharedLink() {
    return Container(
      child: Obx(() => controller.webLoadProgress.value > 0
          ? LinearProgressIndicator(
              value: controller.webLoadProgress.value,
              backgroundColor: Colors.grey[200],
              valueColor: AlwaysStoppedAnimation<Color>(Colors.blue),
            )
          : const SizedBox.shrink()),
    );
  }

  Widget _displayWebContent() {
    return Container(
      decoration: BoxDecoration(
        border: Border.all(
          color: Colors.grey,
          width: 1,
        ),
      ),
      child: DreamWebView(
        url: controller.shareURL ?? '',
        onWebViewCreated: (webController) {
          controller.webViewController = webController;
          // parse_content.js 里面的回调, 用于获取网页内容

          webController.addJavaScriptHandler(
            handlerName: "getPageContent",
            callback: (args) {
              List<String> images = List.from(args[6]);

              controller.saveArticleInfo(
                args[0].toString().trim(), // url
                args[1].toString().trim(), // title
                args[2].toString().trim(), // excerpt
                args[3].toString().trim(), // htmlContent
                args[4].toString().trim(), // textContent
                args[5].toString().trim(), // publishedTime
                images, // imagesUrl
              );
            },
          );
        },
      ),
    );
  }

  Widget _buildCommentTextfield() {
    return TextField(
      controller: controller.commentController,
      decoration: InputDecoration(
        labelText: "备注",
        border: OutlineInputBorder(),
      ),
      maxLines: null,
      keyboardType: TextInputType.multiline,
    );
  }

  Widget _buildActionButtons() {
    return Row(
      children: [
        Expanded(
          child: TextButton(
            style: TextButton.styleFrom(
              backgroundColor: Colors.grey,
              foregroundColor: Colors.white,
            ),
            child: const Text("取消"),
            onPressed: () => controller.clickChannelBtn(),
          ),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: TextButton(
            style: TextButton.styleFrom(
              backgroundColor: Colors.blue,
              foregroundColor: Colors.white,
            ),
            child: const Text("保存"),
            onPressed: () async {
              if (!isProduction) {
                controller.webViewController
                    ?.evaluateJavascript(source: "testNode()");
              }
              controller.showProcessDialog();
              controller.parseWebContent();
            },
          ),
        ),
      ],
    );
  }
}
