import 'dart:async';
import 'package:flutter/services.dart';
import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/components/dialogs/processing_dialog.dart';

/// 分享对话框控制器
/// 管理网页内容的保存和更新
class ShareDialogController extends BaseGetXController {
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

  // 标题编辑追踪：用于区分"本次会话是否改过标题"
  String _initialTitleText = '';
  final RxBool titleEdited = false.obs;

  // 状态服务
  late final ArticleStateService _articleStateService;

  @override
  void onInit() {
    super.onInit();
    _articleStateService = Get.find<ArticleStateService>();
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
    final args = Get.arguments ?? {};

    // 初始化文章ID - 决定是新增还是更新模式
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
      ClipboardUtils.markUrlProcessed(shareURL.value);
    }

    // 记录初始标题并监听编辑变化
    _initialTitleText = titleController.text;
    titleController.addListener(() {
      titleEdited.value = titleController.text.trim() != _initialTitleText.trim();
    });
  }

  /// 加载文章信息
  Future<void> _loadArticleInfo() async {
    if (articleID.value <= 0) return;

    final article = ArticleRepository.d.findModel(articleID.value);
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

    await safeExecute(
      () async {
        // 如果是更新并且选择不重新抓取/AI分析，则只做字段更新
        if (isUpdate.value && !refreshAndAnalyze.value) {
          await _updateArticleFieldsOnly();
        } else {
          await ProcessingDialog.show(
            message: 'AI分析中...',
            onProcess: (updateMessage) async {
              final newArticle = await WebpageParserService.i.saveWebpage(
                url: shareURL.value,
                comment: commentController.text,
                isUpdate: isUpdate.value,
                articleID: articleID.value,
              );
              // 新增模式：保存后拿到新文章ID
              if (articleID.value <= 0 && newArticle.id > 0) {
                articleID.value = newArticle.id;
                logger.i('[ShareDialog] 新增文章保存完成，ID=${articleID.value}');
                // 通知文章创建
                _articleStateService.notifyArticleCreated(newArticle);
              }
            },
          );
          // 保存后再应用用户手动输入的标题与标签
          await _applyManualFieldsPostProcess();
        }
      },
      loadingMessage: '保存中...',
      errorMessage: '保存失败',
      onSuccess: (_) => backToPreviousStep(),
    );
  }

  /// 仅更新标题/标签/备注，不重新抓取网页与AI处理
  Future<void> _updateArticleFieldsOnly() async {
    if (articleID.value <= 0) return;
    final article = ArticleRepository.d.findModel(articleID.value);
    if (article == null) return;

    // 标题与备注
    _setTitleIfEdited(article);
    article.comment = commentController.text.trim();

    // 标签：替换模式
    final tagNames = _getEffectiveTagNames(tagsController.text.trim());
    await _replaceTags(article, tagNames);

    await _saveAndNotify(article, log: '文章字段已更新(无重新抓取)');
  }

  /// 在重新抓取并AI分析后应用用户手动输入字段
  Future<void> _applyManualFieldsPostProcess() async {
    if (articleID.value <= 0) return; // 新增模式: saveWebpage 内部创建了文章, 需要重新找到ID
    final article = ArticleRepository.d.findModel(articleID.value);
    if (article == null) return;

    bool changed = false;
    changed = _setTitleIfEdited(article) || changed;

    // 标签
    final rawTags = tagsController.text.trim();
    if (rawTags.isNotEmpty || tagList.isNotEmpty) {
      final tagNames = _getEffectiveTagNames(rawTags);
      changed = await _mergeTags(article, tagNames) || changed;
    }

    if (changed) {
      await _saveAndNotify(article, log: '已应用手动标题/标签修改');
    }
  }

  /// 计算有效标签集合（优先使用 tagList，如果为空再基于原始文本解析）
  List<String> _getEffectiveTagNames(String rawText) {
    final source = tagList.isNotEmpty ? tagList : rawText.split(RegExp(r'[，,]'));
    return source.map((e) => e.trim()).where((e) => e.isNotEmpty).toSet().toList();
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
      _navigateToDetail();
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

  /// 导航到文章详情页（更新模式下调用）
  void _navigateToDetail() {
    if (articleID.value <= 0) {
      Get.back();
      return;
    }

    final freshArticle = ArticleRepository.d.findModel(articleID.value);
    final arg = freshArticle ?? articleID.value;

    // 更新活跃文章状态
    if (freshArticle != null && Get.isRegistered<ArticleStateService>()) {
      Get.find<ArticleStateService>().setActiveArticle(freshArticle);
    }

    logger.i('跳转到文章详情页: ${articleID.value}, 状态: ${freshArticle?.status}');
    Get.offNamed(Routes.articleDetail, arguments: arg);
  }

  // ===== 私有通用方法（提炼复用） =====

  /// 如果用户在本次会话中编辑过标题，则覆盖标题并清空 aiTitle；返回是否有修改
  bool _setTitleIfEdited(ArticleModel article) {
    final manualTitle = titleController.text.trim();
    if (titleEdited.value && manualTitle.isNotEmpty && manualTitle != (article.title ?? '')) {
      article.title = manualTitle;
      article.aiTitle = '';
      return true;
    }
    return false;
  }

  /// 用传入的标签集合替换当前文章标签
  Future<void> _replaceTags(ArticleModel article, List<String> tagNames) async {
    try {
      await TagRepository.instance.setTagsForArticle(article.id, tagNames);
    } catch (e) {
      logger.w('更新标签失败: $e');
    }
  }

  /// 把传入标签与现有标签合并（不重复），返回是否有新增
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
        await TagRepository.instance.setTagsForArticle(article.id, merged);
      }
      return added;
    } catch (e) {
      logger.w('应用手动标签失败: $e');
      return false;
    }
  }

  /// 保存文章并通知状态服务，同时记录日志前缀
  Future<void> _saveAndNotify(ArticleModel article, {String log = '已更新'}) async {
    article.updatedAt = DateTime.now().toUtc();
    await article.save();

    // 通知全局状态服务文章已更新
    _articleStateService.notifyArticleUpdated(article);

    logger.i('$log: ${article.id}');
  }
}
