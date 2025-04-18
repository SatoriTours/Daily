import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:daily_satori/app/styles/diary_style.dart';

/// 日记标签列表组件
class DiaryTagsList extends StatelessWidget {
  final RxList<String> tags;
  final Function(String) onTagSelected;

  const DiaryTagsList({super.key, required this.tags, required this.onTagSelected});

  @override
  Widget build(BuildContext context) {
    return Obx(() {
      if (tags.isEmpty) {
        return Padding(
          padding: const EdgeInsets.all(16),
          child: Center(
            child: Text('暂无标签', style: TextStyle(color: DiaryStyle.secondaryTextColor(context), fontSize: 14)),
          ),
        );
      }

      return Padding(
        padding: const EdgeInsets.all(16),
        child: Wrap(
          spacing: 8, // 水平间距
          runSpacing: 12, // 垂直间距
          children: tags.map((tag) => _buildTagChip(context, tag)).toList(),
        ),
      );
    });
  }

  Widget _buildTagChip(BuildContext context, String tag) {
    return InkWell(
      onTap: () => onTagSelected(tag),
      child: Chip(
        label: Text('#$tag', style: TextStyle(color: DiaryStyle.primaryTextColor(context), fontSize: 13)),
        backgroundColor: DiaryStyle.tagBackgroundColor(context),
        padding: const EdgeInsets.symmetric(horizontal: 4),
        visualDensity: VisualDensity.compact,
      ),
    );
  }
}
