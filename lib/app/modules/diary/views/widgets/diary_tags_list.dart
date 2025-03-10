import 'package:flutter/material.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:daily_satori/app_exports.dart';
import 'package:daily_satori/app/styles/diary_style.dart';

/// 日记标签列表
class DiaryTagsList extends StatelessWidget {
  final RxList<String> tags;
  final Function(String) onTagSelected;

  const DiaryTagsList({super.key, required this.tags, required this.onTagSelected});

  @override
  Widget build(BuildContext context) {
    return Container(
      constraints: BoxConstraints(maxHeight: MediaQuery.of(context).size.height * 0.4),
      child: Obx(() {
        if (tags.isEmpty) {
          return Padding(
            padding: const EdgeInsets.all(20),
            child: Center(child: Text('没有找到标签', style: TextStyle(color: DiaryStyle.secondaryTextColor(context)))),
          );
        }

        return ListView.separated(
          shrinkWrap: true,
          padding: const EdgeInsets.symmetric(vertical: 10),
          itemCount: tags.length,
          separatorBuilder:
              (context, index) => Divider(
                height: 1,
                thickness: 0.5,
                indent: 20,
                endIndent: 20,
                color: DiaryStyle.dividerColor(context),
              ),
          itemBuilder: (context, index) {
            final tag = tags[index];
            return ListTile(
              dense: true,
              leading: Icon(FeatherIcons.hash, size: 16, color: DiaryStyle.accentColor(context)),
              title: Text(tag, style: TextStyle(fontSize: 15, color: DiaryStyle.primaryTextColor(context))),
              onTap: () => onTagSelected(tag),
            );
          },
        );
      }),
    );
  }
}
