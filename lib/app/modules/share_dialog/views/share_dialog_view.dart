import 'package:daily_satori/global.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'package:get/get.dart';

import 'package:daily_satori/app/modules/share_dialog/controllers/share_dialog_controller.dart';

class ShareDialogView extends GetView<ShareDialogController> {
  const ShareDialogView({super.key});
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text("保存文章"),
          centerTitle: true,
        ),
        body: Container(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                shareText ?? "没获取到链接",
                style: const TextStyle(
                  fontSize: 16,
                  fontWeight: FontWeight.w500,
                ),
              ),
              const SizedBox(height: 10),
              Expanded(
                child: Container(
                  color: Colors.grey[300], // Placeholder color
                  child: const Center(child: Text("Web View Placeholder")),
                ),
              ),
              const SizedBox(height: 10),
              const TextField(
                decoration: InputDecoration(
                  labelText: "备注",
                  border: OutlineInputBorder(),
                ),
                maxLines: null,
                keyboardType: TextInputType.multiline,
              ),
              const SizedBox(height: 10),
              Row(
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
              ),
            ],
          ),
        ),
      ),
    );
  }
}
