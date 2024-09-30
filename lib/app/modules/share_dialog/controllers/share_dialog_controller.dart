import 'package:daily_satori/global.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:get/get.dart';

class ShareDialogController extends GetxController {
  String? shareURL = isProduction
      ? null
      : 'https://x.com/mrbear1024/status/1840380988448247941';

  InAppWebViewController? webViewController;
  final webloadProgress = 0.0.obs;
}
