import 'package:daily_satori/global.dart';
import 'package:flutter/material.dart';

import 'package:get/get.dart';

import 'package:daily_satori/app/modules/share_dialog/controllers/share_dialog_controller.dart';

class ShareDialogView extends GetView<ShareDialogController> {
  const ShareDialogView({super.key});
  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16.0),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Text("Received Text", style: TextStyle(fontSize: 18)),
          const SizedBox(height: 10),
          Text(shareText ?? ''),
          const SizedBox(height: 10),
          TextButton(
            child: const Text("OK"),
            onPressed: () {
              Navigator.of(context).pop(); // 关闭底部弹出框
            },
          ),
        ],
      ),
    );
  }
}
