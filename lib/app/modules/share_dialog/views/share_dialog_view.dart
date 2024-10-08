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
        title: const Text("保存文章"),
        centerTitle: true,
      ),
      body: Container(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            _displaySharedLink(),
            const SizedBox(height: 10),
            Expanded(child: _displayWebContent()),
            const SizedBox(height: 10),
            _buildCommentsTextfield(),
            const SizedBox(height: 10),
            _buildActionButtons(),
          ],
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
          child: Obx(() => controller.webloadProgress.value > 0
              ? LinearProgressIndicator(
                  value: controller.webloadProgress.value,
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
        iframeAllowFullscreen: true);

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
        },
        onPermissionRequest: (controller, request) async {
          return PermissionResponse(
              resources: request.resources,
              action: PermissionResponseAction.GRANT);
        },
        onLoadStart: (webController, url) {
          controller.webloadProgress.value = 0;
        },
        onLoadStop: (webController, url) async {
          controller.webloadProgress.value = 0;
          await webController.injectJavascriptFileFromAsset(
              assetFilePath: "assets/js/.js");
        },
        onReceivedError: (webController, request, error) {
          controller.webloadProgress.value = 0;
        },
        onProgressChanged: (webController, progress) {
          controller.webloadProgress.value = progress / 100;
          if (controller.webloadProgress.value >= 1.0) {
            controller.webloadProgress.value = 0;
          }
        },
      ),
    );
  }

  Widget _buildCommentsTextfield() {
    return const TextField(
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
            onPressed: () {
              SystemNavigator.pop();
            },
          ),
        ),
      ],
    );
  }
}
