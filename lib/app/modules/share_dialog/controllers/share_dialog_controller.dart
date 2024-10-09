import 'package:daily_satori/global.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:get/get.dart';

class ShareDialogController extends GetxController {
  String? shareURL = isProduction ? null : 'https://www.oschina.net/news/315112/python-3130-final-released';

  InAppWebViewController? webViewController;
  final webloadProgress = 0.0.obs;

  void saveArticle(title, excerpt, content, textContent) {}
}
