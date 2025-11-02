import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:daily_satori/app/styles/diary_style.dart';
import 'package:daily_satori/app_exports.dart';
import '../../utils/diary_utils.dart';
import 'diary_timestamp.dart';
import 'diary_more_menu.dart';
import 'diary_image_gallery.dart';
import 'package:daily_satori/app/repositories/book_viewpoint_repository.dart';

/// 单个日记卡片组件 - 支持Markdown和图片
class DiaryCard extends StatefulWidget {
  final DiaryModel diary;
  final VoidCallback onDelete;
  final VoidCallback onEdit;

  const DiaryCard({super.key, required this.diary, required this.onDelete, required this.onEdit});

  @override
  State<DiaryCard> createState() => _DiaryCardState();
}

class _DiaryCardState extends State<DiaryCard> {
  // ---- UI 常量 ----
  static const double _kCardRadius = 12.0;
  static const double _kPillRadius = 8.0;
  static const double _kSmallIcon = 14.0;
  static const double _kPadH = 16.0;
  static const double _kPadV = 16.0;

  // ---- 状态 ----
  bool _isExpanded = false; // 内容是否已展开
  static const int _maxCollapsedLength = 300; // 折叠时最大字符数
  static const int _minExpandableLength = 400; // 低于此长度不显示展开按钮
  late bool _needsExpand; // 是否需要“展开/收起”按钮
  late String _displayContent; // 当前用于展示的内容（可能被截断）

  @override
  void initState() {
    super.initState();
    _initializeContent();
    logger.d('初始化日记卡片: ID=${widget.diary.id}');
  }

  @override
  void didUpdateWidget(DiaryCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.diary.content != widget.diary.content) {
      logger.d('日记卡片内容已更新: ID=${widget.diary.id}');
      _initializeContent();
    }
  }

  /// 初始化卡片内容
  void _initializeContent() {
    _needsExpand = widget.diary.content.length > _minExpandableLength;
    if (_needsExpand && !_isExpanded) {
      _displayContent = _truncateContent(widget.diary.content);
    } else {
      _displayContent = widget.diary.content;
    }
  }

  /// 智能截断内容文本
  String _truncateContent(String fullContent) {
    if (fullContent.length <= _maxCollapsedLength) return fullContent;
    final truncated = fullContent.substring(0, _maxCollapsedLength);
    var breakPos = truncated.lastIndexOf('\n');
    if (breakPos <= _maxCollapsedLength * 0.5) {
      final periodPos = truncated.lastIndexOf('。');
      final commaPos = truncated.lastIndexOf('，');
      breakPos = periodPos > commaPos ? periodPos : commaPos;
      if (breakPos <= _maxCollapsedLength * 0.5) {
        final spacePos = truncated.lastIndexOf(' ');
        breakPos = spacePos > _maxCollapsedLength * 0.7 ? spacePos : _maxCollapsedLength;
      }
    }
    return '${fullContent.substring(0, breakPos)}...';
  }

  /// 切换展开/折叠状态
  void _toggleExpand() {
    setState(() {
      _isExpanded = !_isExpanded;
      _initializeContent();
      logger.d('切换日记展开状态: ID=${widget.diary.id}, 展开=$_isExpanded');
    });
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: DiaryStyle.cardColor(context),
        borderRadius: BorderRadius.circular(_kCardRadius),
        boxShadow: DiaryStyle.cardShadow(context),
      ),
      child: Material(
        color: Colors.transparent,
        borderRadius: BorderRadius.circular(_kCardRadius),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onLongPress: widget.onEdit,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // 顶部时间和更多菜单
              _buildHeader(context),
              // 日记内容区域
              _buildContentArea(context),
            ],
          ),
        ),
      ),
    );
  }

  /// 构建卡片头部区域（时间戳和菜单）
  Widget _buildHeader(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(left: _kPadH, right: 8, top: 10, bottom: 2),
      child: SizedBox(
        height: 24,
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            // 时间戳组件
            DiaryTimestamp(timestamp: widget.diary.createdAt),
            // 更多菜单组件
            DiaryMoreMenu(onEdit: widget.onEdit, onDelete: widget.onDelete),
          ],
        ),
      ),
    );
  }

  /// 构建内容区域（文本、图片、标签）
  Widget _buildContentArea(BuildContext context) {
    // 内容主体（移除标签）与深链解析
    final String contentWithoutTags = _removeTagsFromContent();
    final _DeepLinkInfo? deepLink = _extractBookDeepLink(widget.diary.content);

    return Padding(
      padding: const EdgeInsets.fromLTRB(_kPadH, 4, _kPadH, _kPadV),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Markdown渲染的日记内容，去掉标签
          _buildMarkdownContent(context, contentWithoutTags),

          // 标签列表 - 移到"显示更多"按钮之前
          if (_hasTags) ...[const SizedBox(height: 12), _buildTags(context)],

          // 图片显示
          if (widget.diary.images != null && widget.diary.images!.isNotEmpty) ...[
            const SizedBox(height: 12),
            DiaryImageGallery(imagesString: widget.diary.images!),
          ],

          // 显示"更多"按钮
          if (_needsExpand) _buildExpandToggleButton(context),

          // 将来源胶囊放在最底部
          if (deepLink != null) ...[const SizedBox(height: 10), _buildSourcePill(context, deepLink)],
        ],
      ),
    );
  }

  /// 如果内容包含 app://books/viewpoint/{id} 形式的深链，提取其ID
  _DeepLinkInfo? _extractBookDeepLink(String text) {
    final id = _parseViewpointIdFromText(text);
    return id == null ? null : _DeepLinkInfo(viewpointId: id);
  }

  /// 构建底部“来自读书”来源胶囊（整块可点击跳转）
  Widget _buildSourcePill(BuildContext context, _DeepLinkInfo info) {
    final accent = DiaryStyle.accentColor(context);
    final textColor = DiaryStyle.secondaryTextColor(context);
    final bg = DiaryStyle.tagBackgroundColor(context);

    final meta = _getBookMeta(info.viewpointId);

    return InkWell(
      borderRadius: BorderRadius.circular(_kPillRadius),
      onTap: () => _navigateToViewpoint(info.viewpointId),
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
        decoration: BoxDecoration(
          color: bg,
          borderRadius: BorderRadius.circular(_kPillRadius),
          border: Border.all(color: accent.withAlpha(50), width: 1),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Icon(Icons.menu_book, size: _kSmallIcon, color: accent),
            const SizedBox(width: 6),
            Expanded(
              child: Text(
                _buildSourceLabel(meta),
                style: TextStyle(fontSize: 12, color: textColor, fontWeight: FontWeight.w500, height: 1.35),
                softWrap: true,
              ),
            ),
          ],
        ),
      ),
    );
  }

  /// 从内容中移除标签
  String _removeTagsFromContent() {
    String content = _displayContent;
    if (!_hasTags) return content;
    // 移除所有 #tag 文本（按已存储的 tags 列表精确移除）
    for (final tag in _tagsList) {
      content = content.replaceAll('#$tag', '');
    }
    return content;
  }

  /// 构建Markdown内容
  Widget _buildMarkdownContent(BuildContext context, String content) {
    return MarkdownBody(
      data: content,
      selectable: true,
      styleSheet: DiaryUtils.getMarkdownStyleSheet(context),
      softLineBreak: true,
      fitContent: true,
      shrinkWrap: true,
      // ignore: deprecated_member_use
      imageBuilder: (Uri uri, String? title, String? alt) {
        return _buildMarkdownImage(context, uri);
      },
      // 链接点击：支持内部深链，其余走外部浏览器
      onTapLink: (text, href, title) => _handleMarkdownLinkTap(href),
    );
  }

  /// 构建Markdown图片
  Widget _buildMarkdownImage(BuildContext context, Uri uri) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8.0),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(8.0),
        child: Image.network(
          uri.toString(),
          fit: BoxFit.contain,
          errorBuilder: (context, error, stackTrace) => Container(
            height: 150,
            decoration: BoxDecoration(
              color: DiaryStyle.tagBackgroundColor(context),
              borderRadius: BorderRadius.circular(8.0),
            ),
            child: Center(child: Icon(Icons.broken_image_outlined, color: DiaryStyle.secondaryTextColor(context))),
          ),
        ),
      ),
    );
  }

  /// 构建标签列表
  Widget _buildTags(BuildContext context) {
    final List<String> tags = _tagsList;
    return Wrap(spacing: 8, runSpacing: 8, children: tags.map((tag) => _buildTagItem(context, tag)).toList());
  }

  /// 构建单个标签项
  Widget _buildTagItem(BuildContext context, String tag) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: DiaryStyle.accentColor(context).withAlpha(20),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: DiaryStyle.accentColor(context).withAlpha(50), width: 1),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.tag, size: _kSmallIcon, color: DiaryStyle.accentColor(context)),
          const SizedBox(width: 4),
          Text(
            tag,
            style: TextStyle(fontSize: 13, fontWeight: FontWeight.w500, color: DiaryStyle.accentColor(context)),
          ),
        ],
      ),
    );
  }

  /// 构建展开/折叠按钮
  Widget _buildExpandToggleButton(BuildContext context) {
    final accent = DiaryStyle.accentColor(context);
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.only(top: 16.0),
      padding: const EdgeInsets.symmetric(vertical: 6.0),
      decoration: BoxDecoration(color: accent.withAlpha(20), borderRadius: BorderRadius.circular(6.0)),
      child: GestureDetector(
        onTap: _toggleExpand,
        behavior: HitTestBehavior.opaque,
        child: Center(
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                _isExpanded ? '收起内容' : '查看更多',
                style: TextStyle(color: accent, fontSize: 13.0, fontWeight: FontWeight.w500),
              ),
              Icon(_isExpanded ? Icons.keyboard_arrow_up : Icons.keyboard_arrow_down, size: 16.0, color: accent),
            ],
          ),
        ),
      ),
    );
  }

  // ---- 小工具方法：链接与导航 ----

  /// 统一处理 Markdown 链接点击：内部深链跳转，其余外部打开
  void _handleMarkdownLinkTap(String? href) {
    if (href == null) return;
    final id = _parseViewpointIdFromText(href);
    if (id != null) {
      _navigateToViewpoint(id);
    } else {
      DiaryUtils.launchURL(href);
    }
  }

  /// 从文本中解析出 viewpointId（app://books/viewpoint/{id}）
  int? _parseViewpointIdFromText(String text) {
    final reg = RegExp(r'app://books/viewpoint/(\d+)');
    final m = reg.firstMatch(text);
    if (m == null) return null;
    return int.tryParse(m.group(1)!);
  }

  /// 触发“跳转到对应读书观点”
  void _navigateToViewpoint(int viewpointId) {
    DiaryUtils.openBookViewpoint(viewpointId);
  }

  // ---- 小工具方法：标签 ----

  bool get _hasTags => widget.diary.tags != null && widget.diary.tags!.isNotEmpty;
  List<String> get _tagsList => _hasTags ? widget.diary.tags!.split(',') : const [];

  // ---- 小工具方法：书籍元信息 ----

  /// 根据观点ID查询书名/作者（查询失败时返回空元信息）
  _BookMeta _getBookMeta(int viewpointId) {
    final vp = BookViewpointRepository.i.find(viewpointId);
    if (vp != null && vp.book != null) {
      return _BookMeta(title: vp.book!.title, author: vp.book!.author);
    }
    return const _BookMeta();
  }

  /// 构建“来自读书 …”文案
  String _buildSourceLabel(_BookMeta meta) {
    if (meta.hasTitle) {
      return meta.hasAuthor ? '来自读书 · 《${meta.title}》 · ${meta.author}' : '来自读书 · 《${meta.title}》';
    }
    return '来自读书';
  }
}

/// 内部类：存储深链信息
class _DeepLinkInfo {
  final int viewpointId;
  _DeepLinkInfo({required this.viewpointId});
}

/// 内部类：书籍元信息（标题/作者）
class _BookMeta {
  final String? title;
  final String? author;
  const _BookMeta({this.title, this.author});

  bool get hasTitle => (title ?? '').isNotEmpty;
  bool get hasAuthor => (author ?? '').isNotEmpty;
}
