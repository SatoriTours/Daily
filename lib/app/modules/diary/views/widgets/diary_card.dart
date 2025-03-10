import 'package:flutter/material.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:daily_satori/app/models/diary_model.dart';
import 'package:daily_satori/app/styles/diary_style.dart';
import '../../utils/diary_utils.dart';

// 引入抽离的子组件
import 'diary_timestamp.dart';
import 'diary_more_menu.dart';
import 'diary_image_gallery.dart';
import 'diary_tags.dart';

/// 单个日记卡片组件 - 支持Markdown和图片
class DiaryCard extends StatelessWidget {
  final DiaryModel diary;
  final VoidCallback onDelete;
  final VoidCallback onEdit;

  const DiaryCard({super.key, required this.diary, required this.onDelete, required this.onEdit});

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
          onLongPress: onEdit,
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
            DiaryTimestamp(timestamp: diary.createdAt),

            // 更多菜单组件
            DiaryMoreMenu(onEdit: onEdit, onDelete: onDelete),
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
          MarkdownBody(data: diary.content, selectable: true, styleSheet: DiaryUtils.getMarkdownStyleSheet(context)),

          // 图片显示
          if (diary.images != null && diary.images!.isNotEmpty) ...[
            const SizedBox(height: 8),
            DiaryImageGallery(imagesString: diary.images!),
          ],

          // 标签
          if (diary.tags != null && diary.tags!.isNotEmpty) ...[
            const SizedBox(height: 8),
            DiaryTags(tagsString: diary.tags!),
          ],
        ],
      ),
    );
  }
}
