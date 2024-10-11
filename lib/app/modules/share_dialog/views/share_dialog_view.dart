import 'package:daily_satori/global.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';

import 'package:get/get.dart';

import 'package:daily_satori/app/modules/share_dialog/controllers/share_dialog_controller.dart';

class ShareDialogView extends GetView<ShareDialogController> {
  const ShareDialogView({super.key});

  @override
  Widget build(BuildContext context) {
    if (Get.arguments?['shareURL'] != null) {
      controller.shareURL = Get.arguments?['shareURL'];
    }

    return Scaffold(
      appBar: AppBar(
        title: const Center(child: Text('分享对话框')),
        toolbarHeight: 30, // 调整工具栏高度以减少与body之间的空隙
      ),
      body: Container(
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const SizedBox(height: 10),
            _displaySharedLink(),
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

  Widget _title() {
    return const Center(
      child: Text(
        '保存文章',
        style: TextStyle(
          fontSize: 18,
          fontWeight: FontWeight.bold,
        ),
      ),
    );
  }

  Widget _displaySharedLink() {
    return Row(
      children: [
        IconButton(
          icon: const Icon(Icons.home),
          onPressed: () {
            controller.webViewController?.loadUrl(
                urlRequest: URLRequest(url: WebUri(controller.shareURL ?? '')));
          },
        ),
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
        Expanded(
          child: Obx(() => controller.webLoadProgress.value > 0
              ? LinearProgressIndicator(
                  value: controller.webLoadProgress.value,
                  backgroundColor: Colors.grey[200],
                  valueColor: AlwaysStoppedAnimation<Color>(Colors.blue),
                )
              : const SizedBox.shrink()),
        ),
      ],
    );
  }

  Widget _displayWebContent() {
    InAppWebViewSettings settings = InAppWebViewSettings(
        isInspectable: !isProduction,
        mediaPlaybackRequiresUserGesture: false,
        allowsInlineMediaPlayback: true,
        iframeAllow: "camera; microphone",
        iframeAllowFullscreen: true,
        verticalScrollBarEnabled: false,   // 隐藏垂直滚动条
    );

    return Container(
      decoration: BoxDecoration(
        border: Border.all(
          color: Colors.grey,
          width: 1,
        ),
      ),
      child: InAppWebView(
        initialUrlRequest: URLRequest(url: WebUri(controller.shareURL ?? '')),
        initialSettings: settings,
        onWebViewCreated: (webController) {
          controller.webViewController = webController;
          // parse_content.js 里面的回调, 用于获取网页内容
          webController.addJavaScriptHandler(
              handlerName: "getPageContent",
              callback: (args) {
                controller.saveArticleInfo(
                  args[0].toString().trim(), // url
                  args[1].toString().trim(), // title
                  args[2].toString().trim(), // excerpt
                  args[3].toString().trim(), // htmlContent
                  args[4].toString().trim(), // textContent
                  args[5].toString().trim(), // publishedTime
                  args[6].toString().trim(), // imageUrl
                );
              });
        },
        onPermissionRequest: (controller, request) async {
          return PermissionResponse(
              resources: request.resources,
              action: PermissionResponseAction.GRANT);
        },
        onLoadStart: (webController, url) async {
          controller.webLoadProgress.value = 0;
          webController.injectJavascriptFileFromAsset(
              assetFilePath: "assets/js/common.js");
        },
        onLoadStop: (webController, url) async {
          controller.webLoadProgress.value = 0;
        },
        onReceivedError: (webController, request, error) {
          controller.webLoadProgress.value = 0;
        },
        onProgressChanged: (webController, progress) {
          controller.webLoadProgress.value = progress / 100;
          if (controller.webLoadProgress.value >= 1.0) {
            controller.webLoadProgress.value = 0;
          }
        },
        onConsoleMessage: (controller, consoleMessage) {
          logger.d("浏览器日志: ${consoleMessage.message}");
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
            onPressed: () {
              SystemNavigator.pop();
            },
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
                controller.webViewController?.evaluateJavascript(source: "testNode()");
              }
              await controller.webViewController?.evaluateJavascript(source: "removeAllAdNode()");
              await controller.webViewController?.evaluateJavascript(source: "removeHeaderNode()");
              controller.showProcessDialog();
              controller.parseWebContent();
            },
          ),
        ),
      ],
    );
  }
}
