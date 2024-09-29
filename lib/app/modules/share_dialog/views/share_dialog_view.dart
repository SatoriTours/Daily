import 'package:daily_satori/global.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';

import 'package:get/get.dart';

import 'package:daily_satori/app/modules/share_dialog/controllers/share_dialog_controller.dart';

class ShareDialogView extends GetView<ShareDialogController> {
  const ShareDialogView({super.key});

  String? get shareURL =>
      Get.arguments?['shareURL'] ??
      (isProduction
          ? null
          : 'https://x.com/mrbear1024/status/1840380988448247941');

  @override
  Widget build(BuildContext context) {
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
    return Text(
      shareURL ?? "没有得到链接",
      style: const TextStyle(
        fontSize: 16,
        fontWeight: FontWeight.w500,
      ),
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
        initialUrlRequest: URLRequest(url: WebUri(shareURL ?? '')),
        initialSettings: settings,
        onWebViewCreated: (controller) {
          // 可以在这里保存 WebView 控制器以供后续使用
        },
        onPermissionRequest: (controller, request) async {
          return PermissionResponse(
              resources: request.resources,
              action: PermissionResponseAction.GRANT);
        },
        onLoadStart: (controller, url) {
          // 页面开始加载时的回调
        },
        onLoadStop: (controller, url) {},
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
