import 'package:flutter/material.dart';
import 'package:get/get.dart';
import '../controllers/settings_controller.dart';

class SettingsView extends GetView<SettingsController> {
  const SettingsView({super.key});

  @override
  Widget build(BuildContext context) {
    controller.initData();
    return Scaffold(
      appBar: AppBar(
        title: const Text('配置页'),
        centerTitle: true,
      ),
      body: GestureDetector(
        onTap: () {
          FocusScope.of(context).unfocus(); // 点击空白处去掉输入框焦点
        },
        child: Center(
          child: Padding(
            padding: EdgeInsets.symmetric(horizontal: 32.0, vertical: 16.0), // 增加输入框间距
            child: Column(
              mainAxisAlignment: MainAxisAlignment.start,
              children: [
                _buildOpenAIUrlField(),
                SizedBox(height: 16.0), // 增加输入框之间的间距
                _buildOpenAITokenField(),
                Spacer(), // 添加间隔以将按钮推到页面底部
                _buildSaveButton(context),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildOpenAIUrlField() {
    return TextField(
      controller: controller.openaiAddress,
      decoration: InputDecoration(
        labelText: 'openai 地址',
      ),
    );
  }

  Widget _buildOpenAITokenField() {
    return TextField(
      controller: controller.openaiToken,
      decoration: InputDecoration(
        labelText: 'openai token',
      ),
    );
  }

  Widget _buildSaveButton(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        SizedBox(
          width: MediaQuery.of(context).size.width / 2, // 设置宽度为页面的一半
          child: TextButton(
            style: TextButton.styleFrom(
              backgroundColor: Colors.blue,
              foregroundColor: Colors.white,
            ),
            child: const Text("保存"),
            onPressed: () async {
              await controller.save();
              Get.snackbar('提示', '设置已保存', snackPosition: SnackPosition.top, backgroundColor: Colors.green);
            },
          ),
        ),
      ],
    );
  }
}
