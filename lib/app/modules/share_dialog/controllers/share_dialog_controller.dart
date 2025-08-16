import 'dart:async';
import 'package:daily_satori/app/repositories/article_repository.dart';
import 'package:daily_satori/app/repositories/tag_repository.dart';
import 'package:flutter/services.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/webpage_parser_service.dart';
import 'package:daily_satori/app/components/dialogs/processing_dialog.dart';

/// 分享对话框控制器
/// 管理网页内容的保存和更新
class ShareDialogController extends GetxController {
  static const platform = MethodChannel('android/back/desktop');

  // 状态变量
  final RxString shareURL = ''.obs;
  final RxBool isUpdate = false.obs;

  final RxInt articleID = 0.obs;
  final RxString articleTitle = ''.obs;
  final RxString articleTags = ''.obs; // 逗号分隔的标签文本
  final RxList<String> tagList = <String>[].obs; // 标签列表（用于 Chips UI）

  // 是否重新抓取并AI分析
  final RxBool refreshAndAnalyze = true.obs;

  // 控制器
  final TextEditingController commentController = TextEditingController();
  final TextEditingController titleController = TextEditingController();
  final TextEditingController tagsController = TextEditingController();

  @override
  void onInit() {
    super.onInit();
    _initDefaultValues();
  }

  @override
  void onClose() {
    commentController.dispose();
    titleController.dispose();
    tagsController.dispose();
    super.onClose();
  }

  /// 初始化默认值
  void _initDefaultValues() async {
    // 从路由参数中获取初始值
    final Map<String, dynamic> args = Get.arguments ?? {};

    // 初始化文章ID - 优先级最高，决定是新增还是更新模式

    if (args.containsKey('articleID') && args['articleID'] != null) {
      articleID.value = args['articleID'];
      isUpdate.value = true;
      logger.i("初始化文章ID: ${articleID.value}, 模式: 更新");

      await _loadArticleInfo();
    } else {
      isUpdate.value = false;
      logger.i("模式: 新增");
    }

    // 初始化分享URL
    if (args.containsKey('shareURL') && args['shareURL'] != null) {
      shareURL.value = args['shareURL'];
      logger.i("初始化分享链接: ${shareURL.value}");
    }
  }

  /// 加载文章信息
  Future<void> _loadArticleInfo() async {
    if (articleID.value <= 0) return;

    final article = ArticleRepository.find(articleID.value);
    if (article != null) {
      articleTitle.value = article.showTitle();
      titleController.text = article.showTitle();
      if (shareURL.value.isEmpty) {
        shareURL.value = article.url ?? '';
      }
      commentController.text = article.comment ?? '';
      // 组装标签
      try {
        final tagNames = article.tags.map((t) => t.name ?? '').where((e) => e.isNotEmpty).toList();
        articleTags.value = tagNames.join(', ');
        tagsController.text = articleTags.value;
        tagList.assignAll(tagNames);
      } catch (_) {}
      logger.i("加载文章信息成功: ${article.title}");
    }
  }

  /// 保存按钮点击
  Future<void> onSaveButtonPressed() async {
    logger.i('[ShareDialog] 点击保存: isUpdate=${isUpdate.value}, refreshAndAnalyze=${refreshAndAnalyze.value}');
    // 如果是更新并且选择不重新抓取/AI分析，则只做字段更新
    if (isUpdate.value && !refreshAndAnalyze.value) {
      await _updateArticleFieldsOnly();
      backToPreviousStep();
      return;
    }

    await ProcessingDialog.show(
      message: 'AI分析中...',
      onProcess: (updateMessage) async {
        final saved = await WebpageParserService.i.saveWebpage(
          url: shareURL.value,
          comment: commentController.text,
          isUpdate: isUpdate.value,
          articleID: articleID.value,
        );
        // 新增模式：保存后拿到新文章ID，便于应用手动字段覆盖
        if (articleID.value <= 0 && saved.id > 0) {
          articleID.value = saved.id;
          logger.i('[ShareDialog] 新增文章保存完成，ID=${articleID.value}');
        }
      },
    );
    // 保存后再应用用户手动输入的标题与标签（避免被抓取/AI覆盖）
    await _applyManualFieldsPostProcess();
    backToPreviousStep();
  }

  /// 仅更新标题/标签/备注，不重新抓取网页与AI处理
  Future<void> _updateArticleFieldsOnly() async {
    if (articleID.value <= 0) return;
    final article = ArticleRepository.find(articleID.value);
    if (article == null) return;

    // 更新标题（优先用户填写的原始标题，不动 aiTitle）
    final manualTitle = titleController.text.trim();
    if (manualTitle.isNotEmpty) {
      article.title = manualTitle;
      // 用户显式编辑标题后，清空 aiTitle，避免 showTitle() 继续优先显示 AI 标题
      article.aiTitle = '';
    }
    article.comment = commentController.text.trim();

    // 处理标签
    final rawTags = tagsController.text.trim();
    final tagNames = _getEffectiveTagNames(rawTags);

    // 清空并重新添加
    try {
      article.tags.clear();
      for (final name in tagNames) {
        await TagRepository.addTagToArticle(article, name);
      }
    } catch (e) {
      logger.w('更新标签失败: $e');
    }

    article.updatedAt = DateTime.now().toUtc();
    await article.save();
    logger.i('文章字段已更新(无重新抓取): ${article.id}');
  }

  /// 在重新抓取并AI分析后应用用户手动输入字段
  Future<void> _applyManualFieldsPostProcess() async {
    if (articleID.value <= 0) return; // 新增模式: saveWebpage 内部创建了文章, 需要重新找到ID
    final article = ArticleRepository.find(articleID.value);
    if (article == null) return;

    bool changed = false;
    final manualTitle = titleController.text.trim();
    if (manualTitle.isNotEmpty && manualTitle != article.title) {
      article.title = manualTitle;
      // 手动标题覆盖后，确保不再使用 AI 标题展示
      article.aiTitle = '';
      changed = true;
    }

    // 标签
    final rawTags = tagsController.text.trim();
    if (rawTags.isNotEmpty || tagList.isNotEmpty) {
      final tagNames = _getEffectiveTagNames(rawTags);
      try {
        // 合并现有标签与手动标签（保留AI生成）
        final existing = article.tags.map((t) => t.name ?? '').where((e) => e.isNotEmpty).toSet();
        for (final name in tagNames) {
          if (!existing.contains(name)) {
            await TagRepository.addTagToArticle(article, name);
          }
        }
        changed = true;
      } catch (e) {
        logger.w('应用手动标签失败: $e');
      }
    }

    if (changed) {
      article.updatedAt = DateTime.now().toUtc();
      await article.save();
      logger.i('已应用手动标题/标签修改: ${article.id}');
    }
  }

  /// 计算有效标签集合（优先使用 tagList，如果为空再基于原始文本解析）
  List<String> _getEffectiveTagNames(String rawText) {
    if (tagList.isNotEmpty) {
      return tagList.map((e) => e.trim()).where((e) => e.isNotEmpty).toSet().toList();
    }
    return rawText.split(RegExp(r'[，,]')).map((e) => e.trim()).where((e) => e.isNotEmpty).toSet().toList();
  }

  /// 添加标签（Chips 编辑器调用）
  void addTag(String tag) {
    final t = tag.trim();
    if (t.isEmpty) return;
    if (!tagList.contains(t)) {
      tagList.add(t);
      _syncTagsText();
    }
  }

  /// 批量添加标签
  void addTags(Iterable<String> tags) {
    final added = tags.map((e) => e.trim()).where((e) => e.isNotEmpty);
    var changed = false;
    for (final t in added) {
      if (!tagList.contains(t)) {
        tagList.add(t);
        changed = true;
      }
    }
    if (changed) _syncTagsText();
  }

  /// 移除标签
  void removeTag(String tag) {
    tagList.remove(tag);
    _syncTagsText();
  }

  /// 将 tagList 同步回文本控制器（以逗号分隔）
  void _syncTagsText() {
    articleTags.value = tagList.join(', ');
    tagsController.text = articleTags.value;
  }

  /// 获取短URL显示
  String getShortUrl(String url) {
    if (url.isEmpty) return '';

    // 尝试从URL中提取域名部分
    Uri? uri;
    try {
      uri = Uri.parse(url);
    } catch (_) {
      // 如果解析失败，使用原始URL
    }

    if (uri != null && uri.host.isNotEmpty) {
      return uri.host;
    }

    // 如果URL太长，只显示前30个字符
    if (url.length > 30) {
      return '${url.substring(0, 30)}...';
    }
    return url;
  }

  /// 点击取消按钮
  void backToPreviousStep() {
    if (isUpdate.value) {
      _navigateToHome();
    } else {
      _backToPreviousApp();
    }
  }

  /// 返回到之前的应用
  Future<void> _backToPreviousApp() async {
    try {
      Get.back();
      await platform.invokeMethod('backDesktop');
    } on PlatformException catch (e) {
      logger.e("通信失败: ${e.toString()}");
      await SystemNavigator.pop();
    }
  }

  /// 导航至首页
  void _navigateToHome() {
    logger.i('导航至首页');
    Get.back(times: 2);
  }
}
