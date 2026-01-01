/// Share Dialog Controller Provider
///
/// 分享对话框控制器，管理网页内容的保存和更新。

library;

import 'dart:async';
import 'package:flutter/material.dart';
import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/providers/providers.dart';
import 'package:daily_satori/app/services/index.dart';
import 'package:daily_satori/app/components/dialogs/processing_dialog.dart';
import 'package:daily_satori/app/utils/dialog_utils.dart';
import 'package:daily_satori/app/navigation/app_navigation.dart';
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

  /// 初始化
  void initialize(Map<String, dynamic> args) {
    // 初始化文章ID
    if (args.containsKey('articleID') && args['articleID'] != null) {
      state = state.copyWith(articleID: args['articleID'], isUpdate: true);
      logger.i("初始化文章ID: ${state.articleID}, 模式: 更新");
      _loadArticleInfo(state.articleID);
    } else {
      logger.i("模式: 新增");
    }

    // 检查是否从剪切板来的
    if (args.containsKey('fromClipboard') && args['fromClipboard'] == true) {
      state = state.copyWith(fromClipboard: true);
      logger.i("来源: 剪切板");
    }

    // 初始化分享URL
    if (args.containsKey('shareURL') && args['shareURL'] != null) {
      state = state.copyWith(shareURL: args['shareURL']);
      logger.i("初始化分享链接: ${state.shareURL}");
      ClipboardMonitorService.i.markUrlProcessed(state.shareURL);
    }

    // 记录初始标题并监听编辑变化
    _initialTitleText = titleController.text;
  }

  /// 加载文章信息
  Future<void> _loadArticleInfo(int articleId) async {
    if (articleId <= 0) return;

    final article = ArticleRepository.i.findModel(articleId);
    if (article != null) {
      state = state.copyWith(articleTitle: article.showTitle());
      titleController.text = article.showTitle();

      if (state.shareURL.isEmpty) {
        state = state.copyWith(shareURL: article.url ?? '');
      }

      commentController.text = article.comment ?? '';

      // 组装标签
      try {
        final tagNames = article.tags.map((t) => t.name ?? '').where((e) => e.isNotEmpty).toList();
        state = state.copyWith(articleTags: tagNames.join(', '), tagList: tagNames);
        tagsController.text = state.articleTags;
      } catch (_) {}
      logger.i("加载文章信息成功: ${article.singleLineTitle}");
    }
  }

  /// 保存按钮点击
  Future<void> onSaveButtonPressed(BuildContext context) async {
    logger.i('[ShareDialog] 点击保存: isUpdate=${state.isUpdate}, refreshAndAnalyze=${state.refreshAndAnalyze}');

    try {
      // 如果是更新并且选择不重新抓取/AI分析，则只做字段更新
      if (state.isUpdate && !state.refreshAndAnalyze) {
        await _updateArticleFieldsOnly();
      } else {
        final userEditedTitle = _getUserEditedTitle();

        final newArticle = await ProcessingDialog.show(
          context: context,
          messageKey: 'component.ai_analyzing',
          onProcess: () async {
            return await WebpageParserService.i.saveWebpage(
              url: state.shareURL,
              comment: commentController.text,
              isUpdate: state.isUpdate,
              articleID: state.articleID,
              userTitle: userEditedTitle,
            );
          },
        );

        if (newArticle != null) {
          // 新增模式：保存后拿到新文章ID
          if (state.articleID <= 0 && newArticle.id > 0) {
            state = state.copyWith(articleID: newArticle.id);
            logger.i('[ShareDialog] 新增文章保存完成，ID=${state.articleID}');
            // 通知文章创建
            ref.read(articleStateProvider.notifier).notifyArticleCreated(newArticle);
          }
        } else {
          throw Exception('文章保存失败');
        }

        // 保存后再应用用户手动输入的标签
        await _applyManualTagsPostProcess();
      }

      if (context.mounted) {
        _backToPreviousStep(context);
      }
    } catch (e) {
      final msg = e.toString();
      if (msg.contains('网页已存在')) {
        final exist = ArticleRepository.i.findByUrl(state.shareURL);
        if (exist != null) {
          if (context.mounted) {
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
          return;
        }
      }
      rethrow;
    }
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

  /// 仅更新字段
  Future<void> _updateArticleFieldsOnly() async {
    if (state.articleID <= 0) return;
    final article = ArticleRepository.i.findModel(state.articleID);
    if (article == null) return;

    // 标题与备注
    _setTitleIfEdited(article);
    article.comment = commentController.text.trim();

    // 标签
    final tagNames = _getEffectiveTagNames(tagsController.text.trim());
    await _replaceTags(article, tagNames);

    await _saveAndNotify(article, log: '文章字段已更新(无重新抓取)');
  }

  /// 返回上一步
  void _backToPreviousStep(BuildContext context) {
    if (state.isUpdate) {
      // 更新模式：先关闭当前页面，再返回详情页
      // 使用 popUntil 返回到详情页，而不是 push 新页面
      Navigator.of(context).pop();
    } else {
      // 新增模式：关闭分享对话框，导航到详情页
      Navigator.of(context).pop();
      _navigateToDetail();
    }
  }

  /// 导航到详情页
  void _navigateToDetail() {
    if (state.articleID <= 0) return;

    AppNavigation.toNamed(Routes.articleDetail, arguments: state.articleID);
  }

  // 私有方法
  bool _setTitleIfEdited(ArticleModel article) {
    final manualTitle = titleController.text.trim();
    if (state.titleEdited && manualTitle.isNotEmpty) {
      logger.i('[ShareDialog] 应用用户编辑的标题: "$manualTitle"');
      article.title = manualTitle;
      article.aiTitle = manualTitle;
      return true;
    }
    return false;
  }

  List<String> _getEffectiveTagNames(String rawText) {
    final source = state.tagList.isNotEmpty ? state.tagList : rawText.split(RegExp(r'[，,]'));
    return source.map((e) => e.trim()).where((e) => e.isNotEmpty).toSet().toList();
  }

  Future<void> _replaceTags(ArticleModel article, List<String> tagNames) async {
    try {
      await TagRepository.i.setTagsForArticle(article.id, tagNames);
    } catch (e) {
      logger.w('更新标签失败: $e');
    }
  }

  Future<void> _applyManualTagsPostProcess() async {
    if (state.articleID <= 0) return;
    final article = ArticleRepository.i.findModel(state.articleID);
    if (article == null) return;

    final rawTags = tagsController.text.trim();
    if (rawTags.isNotEmpty || state.tagList.isNotEmpty) {
      final tagNames = _getEffectiveTagNames(rawTags);
      await _mergeTags(article, tagNames);
      await _saveAndNotify(article, log: '已应用手动标签修改');
    }
  }

  Future<bool> _mergeTags(ArticleModel article, List<String> tagNames) async {
    try {
      final Set<String> existing = article.tags
          .map<String>((t) => (t.name ?? '').toString())
          .where((String e) => e.isNotEmpty)
          .toSet();
      var added = false;
      for (final name in tagNames) {
        if (!existing.contains(name)) {
          added = true;
        }
      }
      if (added) {
        final merged = {...existing, ...tagNames}.toList();
        await TagRepository.i.setTagsForArticle(article.id, merged);
      }
      return added;
    } catch (e) {
      logger.w('应用手动标签失败: $e');
      return false;
    }
  }

  Future<void> _saveAndNotify(ArticleModel article, {String log = '已更新'}) async {
    article.updatedAt = DateTime.now().toUtc();
    ArticleRepository.i.updateModel(article);
    ref.read(articleStateProvider.notifier).notifyArticleUpdated(article);
    logger.i('$log: ${article.id}');
  }

  /// 添加标签
  void addTag(String tag) {
    final t = tag.trim();
    if (t.isEmpty) return;
    if (!state.tagList.contains(t)) {
      state = state.copyWith(tagList: [...state.tagList, t]);
      _syncTagsText();
    }
  }

  /// 移除标签
  void removeTag(String tag) {
    final updatedList = List<String>.from(state.tagList)..remove(tag);
    state = state.copyWith(tagList: updatedList);
    _syncTagsText();
  }

  void _syncTagsText() {
    final tagsText = state.tagList.join(', ');
    state = state.copyWith(articleTags: tagsText);
    tagsController.text = tagsText;
  }

  /// 获取短URL
  String getShortUrl(String url) {
    if (url.isEmpty) return '';

    Uri? uri;
    try {
      uri = Uri.parse(url);
    } catch (_) {}

    if (uri != null && uri.host.isNotEmpty) {
      return uri.host;
    }

    if (url.length > 30) {
      return '${url.substring(0, 30)}...';
    }
    return url;
  }

  /// 切换是否重新抓取并AI分析
  void toggleRefreshAndAnalyze(bool? value) {
    if (value != null) {
      state = state.copyWith(refreshAndAnalyze: value);
    }
  }

  /// 标题变更
  void onTitleChanged(String value) {
    if (value != _initialTitleText) {
      state = state.copyWith(titleEdited: true);
    }
  }
}
