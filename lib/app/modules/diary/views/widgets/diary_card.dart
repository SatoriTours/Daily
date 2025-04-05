import 'package:flutter/material.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:daily_satori/app/models/diary_model.dart';
import 'package:daily_satori/app/styles/diary_style.dart';
import '../../utils/diary_utils.dart';
import 'dart:math';

// 引入抽离的子组件
import 'diary_timestamp.dart';
import 'diary_more_menu.dart';
import 'diary_image_gallery.dart';
import 'diary_tags.dart';

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
  }

  @override
  void didUpdateWidget(DiaryCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.diary.content != widget.diary.content) {
      _initializeContent();
    }
  }

  void _initializeContent() {
    // 判断内容是否足够长，需要展开按钮
    _needsExpand = widget.diary.content.length > _minExpandableLength;

    // 如果需要折叠且当前未展开，则截取部分内容
    if (_needsExpand && !_isExpanded) {
      // 截取指定长度的文本
      String truncated = widget.diary.content.substring(0, _maxCollapsedLength);

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

      _displayContent = '${widget.diary.content.substring(0, breakPos)}...';
    } else {
      _displayContent = widget.diary.content;
    }
  }

  // 切换展开/折叠状态
  void _toggleExpand() {
    setState(() {
      _isExpanded = !_isExpanded;
      _initializeContent();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 8),
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
      padding: const EdgeInsets.only(left: 16, right: 8, top: 6, bottom: 0),
      child: SizedBox(
        height: 22,
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
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 2, 16, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Markdown渲染的日记内容
          MarkdownBody(
            data: _displayContent,
            selectable: true,
            styleSheet: DiaryUtils.getMarkdownStyleSheet(context),
            softLineBreak: true,
            fitContent: true,
            shrinkWrap: true,
            // ignore: deprecated_member_use
            imageBuilder: (Uri uri, String? title, String? alt) {
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
                          child: Center(
                            child: Icon(Icons.broken_image_outlined, color: DiaryStyle.secondaryTextColor(context)),
                          ),
                        ),
                  ),
                ),
              );
            },
            onTapLink: (text, href, title) {
              // 实现点击超链接打开浏览器
              if (href != null) {
                DiaryUtils.launchURL(href);
              }
            },
          ),

          // 显示"更多"按钮
          if (_needsExpand)
            Container(
              width: double.infinity,
              margin: const EdgeInsets.only(top: 16.0), // 增加与文章内容的间距
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
                        style: TextStyle(
                          color: DiaryStyle.accentColor(context),
                          fontSize: 13.0,
                          fontWeight: FontWeight.w500,
                        ),
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
            ),

          // 图片显示
          if (widget.diary.images != null && widget.diary.images!.isNotEmpty) ...[
            const SizedBox(height: 8),
            DiaryImageGallery(imagesString: widget.diary.images!),
          ],

          // 标签
          if (widget.diary.tags != null && widget.diary.tags!.isNotEmpty) ...[
            const SizedBox(height: 8),
            DiaryTags(tagsString: widget.diary.tags!),
          ],
        ],
      ),
    );
  }
}
