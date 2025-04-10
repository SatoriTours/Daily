import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:daily_satori/app/styles/diary_style.dart';
import 'package:daily_satori/app_exports.dart';
import '../../utils/diary_utils.dart';
import 'dart:math';

// 引入抽离的子组件
import 'diary_timestamp.dart';
import 'diary_more_menu.dart';
import 'diary_image_gallery.dart';

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
  // 内容是否已展开
  bool _isExpanded = false;
  // 折叠时显示的最大字符数
  static const int _maxCollapsedLength = 300;
  // 展开内容的最小长度，低于此长度不显示展开按钮
  static const int _minExpandableLength = 400;
  // 是否需要展开按钮
  late bool _needsExpand;
  // 用于显示的内容
  late String _displayContent;

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
    // 判断内容是否足够长，需要展开按钮
    _needsExpand = widget.diary.content.length > _minExpandableLength;

    // 如果需要折叠且当前未展开，则截取部分内容
    if (_needsExpand && !_isExpanded) {
      _displayContent = _truncateContent(widget.diary.content);
    } else {
      _displayContent = widget.diary.content;
    }
  }

  /// 智能截断内容文本
  String _truncateContent(String fullContent) {
    // 截取指定长度的文本
    String truncated = fullContent.substring(0, _maxCollapsedLength);

    // 尝试在合适的位置截断（句号、逗号、换行等）
    int breakPos = truncated.lastIndexOf('\n');
    if (breakPos <= _maxCollapsedLength * 0.5) {
      // 如果找不到合适的换行符，或者换行位置太靠前，则尝试查找句号或逗号
      int periodPos = truncated.lastIndexOf('。');
      int commaPos = truncated.lastIndexOf('，');
      breakPos = max(periodPos, commaPos);

      if (breakPos <= _maxCollapsedLength * 0.5) {
        // 如果还是找不到合适的位置，或位置太靠前，则尝试找空格
        int spacePos = truncated.lastIndexOf(' ');
        if (spacePos > _maxCollapsedLength * 0.7) {
          breakPos = spacePos;
        } else {
          // 实在找不到合适的位置，就使用最大长度
          breakPos = _maxCollapsedLength;
        }
      }
    }

    return '${fullContent.substring(0, breakPos)}...';
  }

  /// 切换展开/折叠状态
  void _toggleExpand() {
    setState(() {
      _isExpanded = !_isExpanded;
      _initializeContent();
      logger.d('切换日记展开状态: ID=${widget.diary.id}, 展开=${_isExpanded}');
    });
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: DiaryStyle.cardColor(context),
        borderRadius: BorderRadius.circular(12),
        boxShadow: DiaryStyle.cardShadow(context),
      ),
      child: Material(
        color: Colors.transparent,
        borderRadius: BorderRadius.circular(12),
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
      padding: const EdgeInsets.only(left: 16, right: 8, top: 10, bottom: 2),
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
    // 处理内容，移除#标签部分
    final String contentWithoutTags = _removeTagsFromContent();

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Markdown渲染的日记内容，去掉标签
          _buildMarkdownContent(context, contentWithoutTags),

          // 标签列表 - 移到"显示更多"按钮之前
          if (widget.diary.tags != null && widget.diary.tags!.isNotEmpty) ...[
            const SizedBox(height: 12),
            _buildTags(context),
          ],

          // 图片显示
          if (widget.diary.images != null && widget.diary.images!.isNotEmpty) ...[
            const SizedBox(height: 12),
            DiaryImageGallery(imagesString: widget.diary.images!),
          ],

          // 显示"更多"按钮
          if (_needsExpand) _buildExpandToggleButton(context),
        ],
      ),
    );
  }

  /// 从内容中移除标签
  String _removeTagsFromContent() {
    String contentWithoutTags = _displayContent;
    if (widget.diary.tags != null && widget.diary.tags!.isNotEmpty) {
      // 移除所有#tag格式的内容
      final List<String> tags = widget.diary.tags!.split(',');
      for (final tag in tags) {
        contentWithoutTags = contentWithoutTags.replaceAll('#$tag', '');
      }
    }
    return contentWithoutTags;
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
      onTapLink: (text, href, title) {
        // 实现点击超链接打开浏览器
        if (href != null) {
          DiaryUtils.launchURL(href);
        }
      },
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
          errorBuilder:
              (context, error, stackTrace) => Container(
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
    final List<String> tags = widget.diary.tags!.split(',');
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
          Icon(Icons.tag, size: 14, color: DiaryStyle.accentColor(context)),
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
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.only(top: 16.0),
      padding: const EdgeInsets.symmetric(vertical: 6.0),
      decoration: BoxDecoration(
        color: DiaryStyle.accentColor(context).withAlpha(20),
        borderRadius: BorderRadius.circular(6.0),
      ),
      child: GestureDetector(
        onTap: _toggleExpand,
        behavior: HitTestBehavior.opaque,
        child: Center(
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                _isExpanded ? '收起内容' : '查看更多',
                style: TextStyle(color: DiaryStyle.accentColor(context), fontSize: 13.0, fontWeight: FontWeight.w500),
              ),
              Icon(
                _isExpanded ? Icons.keyboard_arrow_up : Icons.keyboard_arrow_down,
                size: 16.0,
                color: DiaryStyle.accentColor(context),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
