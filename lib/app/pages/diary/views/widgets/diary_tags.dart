import 'package:flutter/material.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:daily_satori/app/styles/diary_style.dart';

/// 日记标签组件
class DiaryTags extends StatelessWidget {
  final String tagsString;

  const DiaryTags({super.key, required this.tagsString});

  @override
  Widget build(BuildContext context) {
    final List<String> tags = tagsString.split(',');

    return Wrap(spacing: 8, runSpacing: 8, children: tags.map((tag) => _buildTagItem(context, tag)).toList());
  }

  /// 构建单个标签项
  Widget _buildTagItem(BuildContext context, String tag) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
      decoration: BoxDecoration(color: DiaryStyle.tagBackgroundColor(context), borderRadius: BorderRadius.circular(10)),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(FeatherIcons.hash, size: 10, color: DiaryStyle.secondaryTextColor(context)),
          Text(tag, style: TextStyle(fontSize: 10, color: DiaryStyle.secondaryTextColor(context))),
        ],
      ),
    );
  }
}
