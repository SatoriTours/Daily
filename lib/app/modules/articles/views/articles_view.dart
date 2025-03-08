import 'package:flutter/material.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/modules/articles/controllers/articles_controller.dart';
import 'package:daily_satori/app/modules/articles/views/widgets/articles_app_bar.dart';
import 'package:daily_satori/app/modules/articles/views/widgets/articles_body.dart';

/// 文章列表页面
class ArticlesView extends GetView<ArticlesController> {
  const ArticlesView({super.key});

  @override
  Widget build(BuildContext context) {
    // 使用组件实例化而不是方法调用
    final appBar = ArticlesAppBar(controller: controller);
    final body = ArticlesBody(controller: controller);

    return Scaffold(appBar: appBar, body: body);
  }
}
