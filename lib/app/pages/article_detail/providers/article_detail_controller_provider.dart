/// Article Detail Controller Provider
///
/// 文章详情控制器，管理文章详情页的状态和交互。

library;

import 'package:freezed_annotation/freezed_annotation.dart';

import 'package:daily_satori/app_exports.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

part 'article_detail_controller_provider.freezed.dart';
part 'article_detail_controller_provider.g.dart';

/// ArticleDetailController 状态
@freezed
abstract class ArticleDetailControllerState
    with _$ArticleDetailControllerState {
  const ArticleDetailControllerState._();

  const factory ArticleDetailControllerState({
    /// 当前文章模型
    ArticleModel? articleModel,

    /// 文章标签字符串,以逗号分隔
    @Default('') String tags,
  }) = _ArticleDetailControllerState;
}

/// ArticleDetailController Provider
/// 参数为 int 类型的文章 ID
@riverpod
class ArticleDetailController extends _$ArticleDetailController {
  @override
  ArticleDetailControllerState build(int articleId) {
    // 在 build 中直接加载文章
    final articleModel = _loadArticleById(articleId);

    if (articleModel != null) {
      // 直接计算标签字符串，不修改 state
      final tags = articleModel.tags.map((tag) => tag.name).join(', ');

      // 监听文章更新事件（直接在 build 中设置监听器）
      ref.listen(articleStateProvider, (previous, next) {
        final event = next.articleUpdateEvent;
        final prevEvent = previous?.articleUpdateEvent;

        // 只在事件实际变化时处理
        if (prevEvent == event) return;

        // 检查事件是否影响当前文章
        if (!_affectsArticle(event, articleModel.id)) return;

        logger.d("[ArticleDetail] 检测到文章事件: $event");

        switch (event) {
          case ArticleUpdateEventUpdated(:final article):
            final newTags = article.tags.map((tag) => tag.name).join(', ');
            state = state.copyWith(articleModel: article, tags: newTags);
            break;
          case ArticleUpdateEventDeleted():
            // 如果文章被删除，返回上一页
            logger.i("[ArticleDetail] 文章已被删除，返回列表");
            AppNavigation.back();
            break;
          case ArticleUpdateEventCreated():
          case ArticleUpdateEventNone():
            // 不需要处理
            break;
        }
      });

      return ArticleDetailControllerState(
        articleModel: articleModel,
        tags: tags,
      );
    }

    return const ArticleDetailControllerState();
  }

  /// 通过 ID 加载文章
  ArticleModel? _loadArticleById(int articleId) {
    final article = ArticleRepository.i.findModel(articleId);
    if (article == null) {
      logger.w('Article not found with ID: $articleId');
      return null;
    }
    return article;
  }

  /// 检查事件是否影响指定文章
  bool _affectsArticle(ArticleUpdateEvent event, int targetArticleId) {
    return switch (event) {
      ArticleUpdateEventUpdated(:final article) =>
        article.id == targetArticleId,
      ArticleUpdateEventDeleted(:final articleId) =>
        articleId == targetArticleId,
      ArticleUpdateEventCreated() => false,
      ArticleUpdateEventNone() => false,
      _ => false,
    };
  }

  /// 删除当前文章
  Future<void> deleteArticle() async {
    final articleModel = state.articleModel;
    if (articleModel == null) return;

    final articleId = articleModel.id;
    await ArticleRepository.i.deleteArticle(articleId);

    // 通知文章删除
    ref.read(articleStateProvider.notifier).notifyArticleDeleted(articleId);
  }

  /// 生成文章的Markdown内容
  Future<void> generateMarkdownContent() async {
    final articleModel = state.articleModel;
    if (articleModel == null) return;

    // 检查HTML内容是否存在
    if (articleModel.htmlContent == null || articleModel.htmlContent!.isEmpty) {
      logger.i("无法生成Markdown：HTML内容为空");
      return;
    }

    // 检查是否已经生成过Markdown内容
    if (articleModel.aiMarkdownContent != null &&
        articleModel.aiMarkdownContent!.isNotEmpty) {
      logger.i("Markdown内容已存在，跳过生成");
      return;
    }

    try {
      logger.i("开始生成Markdown内容");

      // 使用AI服务将HTML转换为Markdown
      final markdown = await AiService.i.htmlToMarkdown(articleModel.htmlContent!);

      if (markdown.isEmpty) {
        throw Exception("Markdown内容生成失败");
      }

      // 保存Markdown内容到文章模型
      articleModel.aiMarkdownContent = markdown;
      ArticleRepository.i.updateModel(articleModel);

      // 更新状态
      state = state.copyWith(articleModel: articleModel);

      // 通知全局状态服务文章已更新
      ref
          .read(articleStateProvider.notifier)
          .notifyArticleUpdated(articleModel);

      logger.i("Markdown内容生成并保存成功");
    } catch (e) {
      logger.e("Markdown内容生成失败: $e");
      rethrow;
    }
  }

  /// 获取文章内容图片列表(不含主图)
  List<String> getArticleImages() {
    final articleModel = state.articleModel;
    if (articleModel == null) return [];

    final images = _getValidImagePaths(articleModel.images);
    return images.length > 1 ? images.sublist(1) : [];
  }

  /// 获取有效的图片路径列表
  List<String> _getValidImagePaths(List<dynamic> items) {
    return items
        .where((item) => item.path != null && item.path!.isNotEmpty)
        .map((item) => item.path! as String)
        .toList();
  }

  /// 删除文章图片
  Future<void> deleteImage(String imagePath) async {
    final articleModel = state.articleModel;
    if (articleModel == null) return;

    articleModel.images.removeWhere((image) => image.path == imagePath);
    ArticleRepository.i.updateModel(articleModel);

    // 更新状态
    state = state.copyWith(articleModel: articleModel);

    // 通知全局状态服务文章已更新
    ref.read(articleStateProvider.notifier).notifyArticleUpdated(articleModel);
  }
}
