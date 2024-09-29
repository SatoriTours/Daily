import 'package:daily_satori/app/modules/share_dialog/views/share_dialog_view.dart';
import 'package:daily_satori/global.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import 'package:get/get.dart';
import '../controllers/home_controller.dart';

class HomeView extends GetView<HomeController> {
  const HomeView({super.key});

  @override
  Widget build(BuildContext context) {
    const platform = MethodChannel('tours.sator.daily/share');

    platform.setMethodCallHandler((call) async {
      logger.i("接收到原生 android 的消息: ${call.method}");
      if (call.method == 'receiveSharedText') {
        Get.off(() => const ShareDialogView(),
            transition: Transition.leftToRight,
            arguments: {'shareURL': call.arguments});
      }
    });

    return Center(
      child: Container(
        decoration: const BoxDecoration(
          image: DecorationImage(
            image: AssetImage('assets/images/cover.jpeg'),
            fit: BoxFit.cover,
          ),
        ),
      ),
    );
  }
}
