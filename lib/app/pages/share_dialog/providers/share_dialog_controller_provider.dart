/// Share Dialog Controller Provider
///
/// 分享对话框控制器，管理网页内容的保存和更新。
library;

import 'dart:async';

import 'package:daily_satori/app_exports.dart';
import 'package:flutter/services.dart';
import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'share_dialog_controller_provider.freezed.dart';
part 'share_dialog_controller_provider.g.dart';

/// ShareDialogController 状态
@freezed
abstract class ShareDialogControllerState with _$ShareDialogControllerState {
  const factory ShareDialogControllerState({
    @Default('') String shareURL,
    @Default(false) bool isUpdate,
    @Default(false) bool fromClipboard,
    @Default(false) bool fromShare,
    @Default(0) int articleID,
    @Default('') String articleTitle,
    @Default('') String articleTags,
    @Default([]) List<String> tagList,
    @Default(true) bool refreshAndAnalyze,
    @Default(false) bool titleEdited,
  }) = _ShareDialogControllerState;
}

/// ShareDialogController Provider
@riverpod
class ShareDialogController extends _$ShareDialogController {
  String _initialTitleText = '';

  @override
  ShareDialogControllerState build() {
    return const ShareDialogControllerState();
  }

  void initialize(Map<String, dynamic> args) {
    _initializeMode(args);
    _initializeSource(args);
    _initializeUrl(args);
  }

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

  void _initializeUrl(Map<String, dynamic> args) {
    final url = args['shareURL'] as String?;
    if (url != null && url.isNotEmpty) {
      state = state.copyWith(shareURL: url);
      logger.i("初始化分享链接: $url");
      ClipboardMonitorService.i.markUrlProcessed(url);
    }
  }

  Future<void> _loadArticleInfo(int articleId) async {
    final article = ArticleRepository.i.findModel(articleId);
    if (article == null) return;

    final title = article.showTitle();
    state = state.copyWith(
      articleTitle: title,
      shareURL: state.shareURL.isEmpty ? (article.url ?? '') : state.shareURL,
    );
    _loadArticleTags(article);
    logger.i("加载文章信息成功: ${article.singleLineTitle}");
  }

  void _loadArticleTags(ArticleModel article) {
    try {
      final tagNames = article.tags
          .map((t) => t.name ?? '')
          .where((e) => e.isNotEmpty)
          .toList();
      final tagsText = tagNames.join(', ');
      state = state.copyWith(articleTags: tagsText, tagList: tagNames);
    } catch (e) {
      logger.w('加载标签失败: $e');
    }
  }

  Future<void> onSaveButtonPressed(
    BuildContext context, {
    required String title,
    required String comment,
  }) async {
    logger.i(
      '[ShareDialog] 点击保存: isUpdate=${state.isUpdate}, refreshAndAnalyze=${state.refreshAndAnalyze}',
    );

    try {
      if (state.isUpdate && !state.refreshAndAnalyze) {
        await _updateFieldsOnly(title, comment);
      } else {
        await _saveWithFetch(context, title, comment);
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

  Future<void> _saveWithFetch(
    BuildContext context,
    String title,
    String comment,
  ) async {
    final userEditedTitle = state.titleEdited ? title : null;

    final newArticle = await ProcessingDialog.show(
      messageKey: 'component.ai_analyzing',
      onProcess: () => WebpageParserService.i.saveWebpage(
        url: state.shareURL,
        comment: comment,
        isUpdate: state.isUpdate,
        articleID: state.articleID,
        userTitle: userEditedTitle,
      ),
    );

    if (newArticle == null) {
      throw Exception('文章保存失败');
    }

    if (state.articleID <= 0 && newArticle.id > 0) {
      state = state.copyWith(articleID: newArticle.id);
      logger.i('[ShareDialog] 新增文章保存完成，ID=${state.articleID}');
      ref.read(articleStateProvider.notifier).notifyArticleCreated(newArticle);
    }

    await _applyManualTags(newArticle.id);
  }

  Future<void> _updateFieldsOnly(String title, String comment) async {
    final article = _getArticle();
    if (article == null) return;

    _updateArticleFields(article, title, comment);
    await _updateArticleTags(article.id);
    await _saveArticle(article, log: '文章字段已更新(无重新抓取)');
  }

  void _updateArticleFields(
    ArticleModel article,
    String title,
    String comment,
  ) {
    final manualTitle = title.trim();
    if (state.titleEdited && manualTitle.isNotEmpty) {
      logger.i('[ShareDialog] 应用用户编辑的标题: "$manualTitle"');
      article.title = manualTitle;
      article.aiTitle = manualTitle;
    }
    article.comment = comment.trim();
  }

  Future<void> _updateArticleTags(int articleId) async {
    if (state.tagList.isEmpty) return;
    await _setArticleTags(articleId, state.tagList);
  }

  Future<void> _applyManualTags(int articleId) async {
    final article = ArticleRepository.i.findModel(articleId);
    if (article == null) return;

    if (state.tagList.isEmpty) return;

    final added = await _mergeArticleTags(article, state.tagList);

    if (added) {
      await _saveArticle(article, log: '已应用手动标签修改');
    }
  }

  void _handleSaveSuccess(BuildContext context) {
    if (state.isUpdate) {
      AppNavigation.back();
    } else {
      AppNavigation.offAllNamed(Routes.home);

      if (state.fromShare) {
        logger.i('[ShareDialog] 从其他app分享，将app推到后台');
        _moveAppToBackground();
      }
    }
  }

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
        await onSaveButtonPressed(
          context,
          title: state.articleTitle,
          comment: '',
        );
      },
    );
  }

  void addTag(String tag) {
    final trimmed = tag.trim();
    if (trimmed.isEmpty || state.tagList.contains(trimmed)) return;
    state = state.copyWith(tagList: [...state.tagList, trimmed]);
  }

  void removeTag(String tag) {
    final updatedList = List<String>.from(state.tagList)..remove(tag);
    state = state.copyWith(tagList: updatedList);
  }

  void toggleRefreshAndAnalyze(bool? value) {
    if (value != null) {
      state = state.copyWith(refreshAndAnalyze: value);
    }
  }

  void onTitleChanged(String value) {
    if (_initialTitleText.isEmpty) {
      _initialTitleText = value;
    }
    if (value != _initialTitleText) {
      state = state.copyWith(titleEdited: true);
    }
  }

  ArticleModel? _getArticle() {
    if (state.articleID <= 0) return null;
    return ArticleRepository.i.findModel(state.articleID);
  }

  Future<void> _setArticleTags(int articleId, List<String> tagNames) async {
    try {
      await TagRepository.i.setTagsForArticle(articleId, tagNames);
    } catch (e) {
      logger.w('设置标签失败: $e');
    }
  }

  Future<bool> _mergeArticleTags(
    ArticleModel article,
    List<String> newTags,
  ) async {
    try {
      final existing = article.tags
          .map((t) => t.name ?? '')
          .where((e) => e.isNotEmpty)
          .toSet();

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

  Future<void> _saveArticle(ArticleModel article, {String log = '已更新'}) async {
    article.updatedAt = DateTime.now().toUtc();
    ArticleRepository.i.updateModel(article);
    ref.read(articleStateProvider.notifier).notifyArticleUpdated(article);
    logger.i('$log: ${article.id}');
  }

  Future<void> _moveAppToBackground() async {
    const channel = MethodChannel('android/back/desktop');
    try {
      await channel.invokeMethod('backDesktop');
    } catch (e) {
      logger.w('[ShareDialog] moveTaskToBack 失败，使用 AppNavigation.exitApp: $e');
      AppNavigation.exitApp();
    }
  }
}
