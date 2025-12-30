/// Article Detail Controller Provider
///
/// 文章详情控制器，管理文章详情页的状态和交互。

library;

import 'package:freezed_annotation/freezed_annotation.dart';
import 'package:riverpod_annotation/riverpod_annotation.dart';

import 'package:daily_satori/app/data/index.dart';
import 'package:daily_satori/app/navigation/app_navigation.dart';
import 'package:daily_satori/app/providers/providers.dart';
import 'package:daily_satori/app/services/index.dart';

part 'article_detail_controller_provider.freezed.dart';
part 'article_detail_controller_provider.g.dart';

/// ArticleDetailController 状态
@freezed
abstract class ArticleDetailControllerState with _$ArticleDetailControllerState {
  const ArticleDetailControllerState._();

  const factory ArticleDetailControllerState({
    /// 当前文章模型
    ArticleModel? articleModel,

    /// 文章标签字符串,以逗号分隔
    @Default('') String tags,
  }) = _ArticleDetailControllerState;
}

/// ArticleDetailController Provider
@riverpod
class ArticleDetailController extends _$ArticleDetailController {
  @override
  ArticleDetailControllerState build() {
    return const ArticleDetailControllerState();
  }

  /// 加载文章
  void loadArticle(dynamic argument) {
    ArticleModel? articleModel;

    if (argument is ArticleModel) {
      // 从数据库重新获取最新状态
      articleModel = ArticleRepository.i.findModel(argument.id) ?? argument;
    } else if (argument is int) {
      // 通过ID查找文章
      final articleRef = ArticleRepository.i.findModel(argument);
      if (articleRef == null) {
        throw ArgumentError('Article not found with ID: $argument');
      }
      articleModel = articleRef;
    } else {
      throw ArgumentError('Invalid argument type: ${argument.runtimeType}');
    }

    state = state.copyWith(articleModel: articleModel);
    loadTags(articleModel);

    // 设置为活跃文章
    ref.read(articleStateProvider.notifier).setActiveArticle(articleModel);

    // 监听文章更新事件
    _initArticleUpdateListener(articleModel.id);
  }

  /// 初始化文章更新监听器
  void _initArticleUpdateListener(int articleId) {
    ref.listen(articleStateProvider, (previous, next) {
      final event = next.articleUpdateEvent;

      // 检查事件是否影响当前文章
      if (!_affectsArticle(event, articleId)) return;

      logger.d("[ArticleDetail] 检测到文章事件: $event");

      switch (event) {
        case ArticleUpdateEventUpdated(:final article):
          state = state.copyWith(articleModel: article);
          loadTags(article);
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
  }

  /// 检查事件是否影响指定文章
  bool _affectsArticle(ArticleUpdateEvent event, int targetArticleId) {
    return switch (event) {
      ArticleUpdateEventUpdated(:final article) => article.id == targetArticleId,
      ArticleUpdateEventDeleted(:final articleId) => articleId == targetArticleId,
      ArticleUpdateEventCreated() => false,
      ArticleUpdateEventNone() => false,
      _ => false,
    };
  }

  /// 加载并格式化文章标签
  void loadTags(ArticleModel? article) {
    if (article == null) return;
    state = state.copyWith(tags: article.tags.map((tag) => tag.name).join(', '));
  }

  /// 删除当前文章
  Future<void> deleteArticle() async {
    final articleModel = state.articleModel;
    if (articleModel == null) return;

    final articleId = articleModel.id;
    await ArticleRepository.i.deleteArticle(articleId);

    // 通知文章删除
    ref.read(articleStateProvider.notifier).notifyArticleDeleted(articleId);

    // 清除活跃文章状态
    ref.read(articleStateProvider.notifier).clearActiveArticle();
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
    if (articleModel.aiMarkdownContent != null && articleModel.aiMarkdownContent!.isNotEmpty) {
      logger.i("Markdown内容已存在，跳过生成");
      return;
    }

    try {
      logger.i("开始生成Markdown内容");

      // 使用AI服务将HTML转换为Markdown
      final markdown = await AiService.i.convertHtmlToMarkdown(
        articleModel.htmlContent!,
        title: articleModel.title ?? articleModel.aiTitle,
        updatedAt: articleModel.updatedAt,
      );

      if (markdown.isEmpty) {
        throw Exception("Markdown内容生成失败");
      }

      // 保存Markdown内容到文章模型
      articleModel.aiMarkdownContent = markdown;
      ArticleRepository.i.updateModel(articleModel);

      // 更新状态
      state = state.copyWith(articleModel: articleModel);

      // 通知全局状态服务文章已更新
      ref.read(articleStateProvider.notifier).notifyArticleUpdated(articleModel);

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
