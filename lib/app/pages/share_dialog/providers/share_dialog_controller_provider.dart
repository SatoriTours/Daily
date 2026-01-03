/// Share Dialog Controller Provider
///
/// 分享对话框控制器，管理网页内容的保存和更新。

library;

import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/providers/providers.dart';
import 'package:daily_satori/app/services/index.dart';
import 'package:daily_satori/app/components/dialogs/processing_dialog.dart';
import 'package:daily_satori/app/utils/dialog_utils.dart';
import 'package:daily_satori/app/routes/app_navigation.dart';
import 'package:daily_satori/app/routes/app_routes.dart';

part 'share_dialog_controller_provider.freezed.dart';
part 'share_dialog_controller_provider.g.dart';

/// ShareDialogController 状态
@freezed
abstract class ShareDialogControllerState with _$ShareDialogControllerState {
  const factory ShareDialogControllerState({
    /// 分享URL
    @Default('') String shareURL,

    /// 是否是更新模式
    @Default(false) bool isUpdate,

    /// 是否从剪切板来的
    @Default(false) bool fromClipboard,

    /// 是否从其他app分享来的
    @Default(false) bool fromShare,

    /// 文章ID
    @Default(0) int articleID,

    /// 文章标题
    @Default('') String articleTitle,

    /// 文章标签
    @Default('') String articleTags,

    /// 标签列表
    @Default([]) List<String> tagList,

    /// 是否重新抓取并AI分析
    @Default(true) bool refreshAndAnalyze,

    /// 标题是否编辑过
    @Default(false) bool titleEdited,
  }) = _ShareDialogControllerState;
}

/// ShareDialogController Provider
@riverpod
class ShareDialogController extends _$ShareDialogController {
  final TextEditingController commentController = TextEditingController();
  final TextEditingController titleController = TextEditingController();
  final TextEditingController tagsController = TextEditingController();

  String _initialTitleText = '';

  @override
  ShareDialogControllerState build() {
    return const ShareDialogControllerState();
  }

  // ============================================================================
  // 初始化相关
  // ============================================================================

  /// 初始化对话框
  void initialize(Map<String, dynamic> args) {
    _initializeMode(args);
    _initializeSource(args);
    _initializeUrl(args);
    _initialTitleText = titleController.text;
  }

  /// 初始化模式（新增/更新）
  void _initializeMode(Map<String, dynamic> args) {
    final articleID = args['articleID'] as int?;
    if (articleID != null && articleID > 0) {
      state = state.copyWith(articleID: articleID, isUpdate: true);
      logger.i("初始化文章ID: $articleID, 模式: 更新");
      _loadArticleInfo(articleID);
    } else {
      logger.i("模式: 新增");
    }
  }

  /// 初始化来源
  void _initializeSource(Map<String, dynamic> args) {
    if (args['fromClipboard'] == true) {
      state = state.copyWith(fromClipboard: true);
      logger.i("来源: 剪切板");
    }
    if (args['fromShare'] == true) {
      state = state.copyWith(fromShare: true);
      logger.i("来源: 其他app分享");
    }
  }

  /// 初始化URL
  void _initializeUrl(Map<String, dynamic> args) {
    final url = args['shareURL'] as String?;
    if (url != null && url.isNotEmpty) {
      state = state.copyWith(shareURL: url);
      logger.i("初始化分享链接: $url");
      ClipboardMonitorService.i.markUrlProcessed(url);
    }
  }

  /// 加载文章信息
  Future<void> _loadArticleInfo(int articleId) async {
    final article = ArticleRepository.i.findModel(articleId);
    if (article == null) return;

    final title = article.showTitle();
    titleController.text = title;

    state = state.copyWith(
      articleTitle: title,
      shareURL: state.shareURL.isEmpty ? (article.url ?? '') : state.shareURL,
    );

    commentController.text = article.comment ?? '';
    _loadArticleTags(article);

    logger.i("加载文章信息成功: ${article.singleLineTitle}");
  }

  /// 加载文章标签
  void _loadArticleTags(ArticleModel article) {
    try {
      final tagNames = article.tags.map((t) => t.name ?? '').where((e) => e.isNotEmpty).toList();
      final tagsText = tagNames.join(', ');
      state = state.copyWith(articleTags: tagsText, tagList: tagNames);
      tagsController.text = tagsText;
    } catch (e) {
      logger.w('加载标签失败: $e');
    }
  }

  // ============================================================================
  // 保存逻辑
  // ============================================================================

  /// 保存按钮点击
  Future<void> onSaveButtonPressed(BuildContext context) async {
    logger.i('[ShareDialog] 点击保存: isUpdate=${state.isUpdate}, refreshAndAnalyze=${state.refreshAndAnalyze}');

    try {
      if (state.isUpdate && !state.refreshAndAnalyze) {
        // 仅更新字段，不重新抓取
        await _updateFieldsOnly();
      } else {
        // 完整保存：抓取+AI分析
        await _saveWithFetch(context);
      }

      if (context.mounted) {
        _handleSaveSuccess(context);
      }
    } catch (e) {
      if (context.mounted) {
        await _handleSaveError(e, context);
      }
    }
  }

  /// 完整保存流程（抓取+AI分析）
  Future<void> _saveWithFetch(BuildContext context) async {
    final userEditedTitle = _getUserEditedTitle();

    final newArticle = await ProcessingDialog.show(
      messageKey: 'component.ai_analyzing',
      onProcess: () => WebpageParserService.i.saveWebpage(
        url: state.shareURL,
        comment: commentController.text,
        isUpdate: state.isUpdate,
        articleID: state.articleID,
        userTitle: userEditedTitle,
      ),
    );

    if (newArticle == null) {
      throw Exception('文章保存失败');
    }

    // 新增模式：更新文章ID并通知
    if (state.articleID <= 0 && newArticle.id > 0) {
      state = state.copyWith(articleID: newArticle.id);
      logger.i('[ShareDialog] 新增文章保存完成，ID=${state.articleID}');
      ref.read(articleStateProvider.notifier).notifyArticleCreated(newArticle);
    }

    // 应用用户手动输入的标签
    await _applyManualTags();
  }

  /// 仅更新字段（不重新抓取）
  Future<void> _updateFieldsOnly() async {
    final article = _getArticle();
    if (article == null) return;

    _updateArticleFields(article);
    await _updateArticleTags(article);
    await _saveArticle(article, log: '文章字段已更新(无重新抓取)');
  }

  /// 更新文章基本字段
  void _updateArticleFields(ArticleModel article) {
    final manualTitle = titleController.text.trim();
    if (state.titleEdited && manualTitle.isNotEmpty) {
      logger.i('[ShareDialog] 应用用户编辑的标题: "$manualTitle"');
      article.title = manualTitle;
      article.aiTitle = manualTitle;
    }
    article.comment = commentController.text.trim();
  }

  /// 更新文章标签
  Future<void> _updateArticleTags(ArticleModel article) async {
    final tagNames = _parseTagNames(tagsController.text.trim());
    await _setArticleTags(article.id, tagNames);
  }

  /// 应用手动输入的标签（后处理）
  Future<void> _applyManualTags() async {
    final article = _getArticle();
    if (article == null) return;

    final rawTags = tagsController.text.trim();
    if (rawTags.isEmpty && state.tagList.isEmpty) return;

    final tagNames = _parseTagNames(rawTags);
    final added = await _mergeArticleTags(article, tagNames);

    if (added) {
      await _saveArticle(article, log: '已应用手动标签修改');
    }
  }

  /// 处理保存成功
  void _handleSaveSuccess(BuildContext context) {
    if (state.isUpdate) {
      // 更新模式：返回详情页
      AppNavigation.back();
    } else {
      // 新增模式：根据来源决定行为
      AppNavigation.offAllNamed(Routes.home);

      if (state.fromShare) {
        logger.i('[ShareDialog] 从其他app分享，将app推到后台');
        _moveAppToBackground();
      }
    }
  }

  /// 处理保存错误
  Future<void> _handleSaveError(Object error, BuildContext context) async {
    final msg = error.toString();
    if (!msg.contains('网页已存在')) {
      throw error;
    }

    final exist = ArticleRepository.i.findByUrl(state.shareURL);
    if (exist == null) {
      throw error;
    }

    await DialogUtils.showConfirm(
      title: '提示',
      message: '该网页已存在，是否更新？',
      confirmText: '更新',
      onConfirm: () async {
        state = state.copyWith(isUpdate: true, articleID: exist.id);
        await onSaveButtonPressed(context);
      },
    );
  }

  // ============================================================================
  // 标签管理
  // ============================================================================

  /// 添加标签
  void addTag(String tag) {
    final trimmed = tag.trim();
    if (trimmed.isEmpty || state.tagList.contains(trimmed)) return;

    state = state.copyWith(tagList: [...state.tagList, trimmed]);
    _syncTagsToController();
  }

  /// 移除标签
  void removeTag(String tag) {
    final updatedList = List<String>.from(state.tagList)..remove(tag);
    state = state.copyWith(tagList: updatedList);
    _syncTagsToController();
  }

  /// 同步标签列表到输入框
  void _syncTagsToController() {
    final tagsText = state.tagList.join(', ');
    state = state.copyWith(articleTags: tagsText);
    tagsController.text = tagsText;
  }

  // ============================================================================
  // UI辅助方法
  // ============================================================================

  /// 获取短URL（用于显示）
  String getShortUrl(String url) {
    if (url.isEmpty) return '';

    final uri = Uri.tryParse(url);
    if (uri != null && uri.host.isNotEmpty) {
      return uri.host;
    }

    return url.length > 30 ? '${url.substring(0, 30)}...' : url;
  }

  /// 切换是否重新抓取并AI分析
  void toggleRefreshAndAnalyze(bool? value) {
    if (value != null) {
      state = state.copyWith(refreshAndAnalyze: value);
    }
  }

  /// 标题变更回调
  void onTitleChanged(String value) {
    if (value != _initialTitleText) {
      state = state.copyWith(titleEdited: true);
    }
  }

  // ============================================================================
  // 私有辅助方法
  // ============================================================================

  /// 获取当前文章（如果存在）
  ArticleModel? _getArticle() {
    if (state.articleID <= 0) return null;
    return ArticleRepository.i.findModel(state.articleID);
  }

  /// 获取用户编辑的标题
  String? _getUserEditedTitle() {
    final manualTitle = titleController.text.trim();
    if (state.titleEdited && manualTitle.isNotEmpty) {
      logger.i('[ShareDialog] 用户编辑了标题: "$manualTitle"');
      return manualTitle;
    }
    return null;
  }

  /// 解析标签名称（从文本或列表）
  List<String> _parseTagNames(String rawText) {
    final source = state.tagList.isNotEmpty ? state.tagList : rawText.split(RegExp(r'[，,]'));
    return source.map((e) => e.trim()).where((e) => e.isNotEmpty).toSet().toList();
  }

  /// 设置文章标签（替换模式）
  Future<void> _setArticleTags(int articleId, List<String> tagNames) async {
    try {
      await TagRepository.i.setTagsForArticle(articleId, tagNames);
    } catch (e) {
      logger.w('设置标签失败: $e');
    }
  }

  /// 合并文章标签（添加新标签，保留已有标签）
  Future<bool> _mergeArticleTags(ArticleModel article, List<String> newTags) async {
    try {
      final existing = article.tags.map((t) => t.name ?? '').where((e) => e.isNotEmpty).toSet();

      final hasNewTags = newTags.any((tag) => !existing.contains(tag));
      if (!hasNewTags) return false;

      final merged = {...existing, ...newTags}.toList();
      await TagRepository.i.setTagsForArticle(article.id, merged);
      return true;
    } catch (e) {
      logger.w('合并标签失败: $e');
      return false;
    }
  }

  /// 保存文章并通知状态更新
  Future<void> _saveArticle(ArticleModel article, {String log = '已更新'}) async {
    article.updatedAt = DateTime.now().toUtc();
    ArticleRepository.i.updateModel(article);
    ref.read(articleStateProvider.notifier).notifyArticleUpdated(article);
    logger.i('$log: ${article.id}');
  }

  /// 将应用推到后台（Android）
  Future<void> _moveAppToBackground() async {
    const channel = MethodChannel('android/back/desktop');
    try {
      await channel.invokeMethod('backDesktop');
    } catch (e) {
      logger.w('[ShareDialog] moveTaskToBack 失败，使用 AppNavigation.exitApp: $e');
      AppNavigation.exitApp();
    }
  }

  /// 导航到详情页
  void _navigateToDetail() {
    if (state.articleID > 0) {
      AppNavigation.toNamed(Routes.articleDetail, arguments: state.articleID);
    }
  }
}
