import 'package:flutter/material.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:daily_satori/app/styles/index.dart';

/// 日记标签组件
class DiaryTags extends StatelessWidget {
  final String tagsString;

  const DiaryTags({super.key, required this.tagsString});

  @override
  Widget build(BuildContext context) {
    final List<String> tags = tagsString.split(',');

    return Wrap(
      spacing: Dimensions.spacingS,
      runSpacing: Dimensions.spacingS,
      children: tags.map((tag) => _buildTagItem(context, tag)).toList(),
    );
  }

  /// 构建单个标签项
  Widget _buildTagItem(BuildContext context, String tag) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: Dimensions.spacingXs + 2, vertical: Dimensions.spacingXs - 1),
      decoration: BoxDecoration(
        color: DiaryStyles.getTagBackgroundColor(context),
        borderRadius: BorderRadius.circular(Dimensions.radiusS + 2),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(FeatherIcons.hash, size: 10, color: DiaryStyles.getSecondaryTextColor(context)),
          Text(tag, style: TextStyle(fontSize: 10, color: DiaryStyles.getSecondaryTextColor(context))),
        ],
      ),
    );
  }
}
