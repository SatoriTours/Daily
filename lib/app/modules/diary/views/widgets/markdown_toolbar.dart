import 'package:flutter/material.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:font_awesome_flutter/font_awesome_flutter.dart';
import 'package:daily_satori/app/styles/diary_style.dart';
import 'package:daily_satori/app/modules/diary/utils/diary_utils.dart';

/// Markdown工具栏组件
class MarkdownToolbar extends StatelessWidget {
  final TextEditingController controller;
  final Function()? onSave;
  final String saveLabel;

  const MarkdownToolbar({super.key, required this.controller, this.onSave, this.saveLabel = '保存'});

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 48,
      margin: EdgeInsets.only(top: 8),
      child: Row(
        children: [
          // 工具栏 - 带滚动功能
          Expanded(
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: EdgeInsets.zero,
              children: [
                _buildToolbarButton(
                  context,
                  FontAwesomeIcons.heading,
                  '标题',
                  () => DiaryUtils.insertMarkdown(controller, '# '),
                ),
                _buildToolbarButton(
                  context,
                  FontAwesomeIcons.bold,
                  '粗体',
                  () => DiaryUtils.insertMarkdown(controller, '**文本**'),
                ),
                _buildToolbarButton(
                  context,
                  FontAwesomeIcons.italic,
                  '斜体',
                  () => DiaryUtils.insertMarkdown(controller, '*文本*'),
                ),
                _buildToolbarButton(
                  context,
                  FontAwesomeIcons.listUl,
                  '无序列表',
                  () => DiaryUtils.insertMarkdown(controller, '- 项目\n- 项目\n- 项目'),
                ),
                _buildToolbarButton(
                  context,
                  FontAwesomeIcons.listOl,
                  '有序列表',
                  () => DiaryUtils.insertMarkdown(controller, '1. 项目\n2. 项目\n3. 项目'),
                ),
                _buildToolbarButton(
                  context,
                  FontAwesomeIcons.link,
                  '链接',
                  () => DiaryUtils.insertMarkdown(controller, '[链接文本](https://example.com)'),
                ),
                _buildToolbarButton(
                  context,
                  FontAwesomeIcons.quoteLeft,
                  '引用',
                  () => DiaryUtils.insertMarkdown(controller, '> 引用文本'),
                ),
                _buildToolbarButton(
                  context,
                  FontAwesomeIcons.code,
                  '代码',
                  () => DiaryUtils.insertMarkdown(controller, '`代码`'),
                ),
                _buildToolbarButton(
                  context,
                  FontAwesomeIcons.minus,
                  '分割线',
                  () => DiaryUtils.insertMarkdown(controller, '\n---\n'),
                ),
              ],
            ),
          ),

          // 保存/更新按钮
          if (onSave != null) _buildSaveButton(context, saveLabel, onSave!),
        ],
      ),
    );
  }

  // 构建工具栏图标按钮
  Widget _buildToolbarButton(BuildContext context, IconData icon, String tooltip, VoidCallback onPressed) {
    return Tooltip(
      message: tooltip,
      child: Container(
        width: 36,
        height: 36,
        margin: EdgeInsets.symmetric(horizontal: 2),
        child: IconButton(
          icon: Icon(icon, size: 16, color: DiaryStyle.primaryTextColor(context)),
          onPressed: onPressed,
          padding: EdgeInsets.all(0),
          constraints: BoxConstraints(),
          visualDensity: VisualDensity.compact,
        ),
      ),
    );
  }

  // 构建保存/更新按钮
  Widget _buildSaveButton(BuildContext context, String text, VoidCallback onPressed) {
    return Container(
      margin: EdgeInsets.only(left: 8),
      child: IconButton(
        onPressed: onPressed,
        icon: Icon(FeatherIcons.check, size: 18, color: DiaryStyle.accentColor(context)),
        tooltip: text,
        padding: EdgeInsets.all(0),
        visualDensity: VisualDensity.compact,
      ),
    );
  }
}
