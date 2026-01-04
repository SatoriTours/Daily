import 'package:flutter/material.dart';
import 'package:daily_satori/app/utils/utils.dart';
import 'package:feather_icons/feather_icons.dart';
import 'package:daily_satori/app/styles/styles.dart';

/// 日记卡片的更多操作菜单
class DiaryMoreMenu extends StatelessWidget {
  final VoidCallback onEdit;
  final VoidCallback onDelete;

  const DiaryMoreMenu({super.key, required this.onEdit, required this.onDelete});

  @override
  Widget build(BuildContext context) {
    return PopupMenuButton<String>(
      padding: EdgeInsets.zero,
      iconSize: Dimensions.iconSizeXs,
      icon: Icon(
        FeatherIcons.moreHorizontal,
        size: Dimensions.iconSizeXs,
        color: DiaryStyles.getSecondaryTextColor(context),
      ),
      splashRadius: Dimensions.spacingS + 4,
      tooltip: '更多选项',
      elevation: 3,
      shape: RoundedRectangleBorder(borderRadius: Dimensions.borderRadiusM),
      itemBuilder: (context) => [
        PopupMenuItem<String>(
          value: 'edit',
          child: Row(
            children: [
              Icon(FeatherIcons.edit2, size: Dimensions.iconSizeXs, color: DiaryStyles.getAccentColor(context)),
              Dimensions.horizontalSpacerS,
              const Text('编辑日记'),
            ],
          ),
        ),
        const PopupMenuItem<String>(
          value: 'delete',
          child: Row(
            children: [
              Icon(FeatherIcons.trash2, size: Dimensions.iconSizeXs, color: Colors.red),
              Dimensions.horizontalSpacerS,
              Text('删除日记', style: TextStyle(color: Colors.red)),
            ],
          ),
        ),
      ],
      onSelected: (value) {
        if (value == 'edit') {
          onEdit();
        } else if (value == 'delete') {
          _showDeleteConfirmation(context);
        }
      },
    );
  }

  /// 显示删除确认对话框
  void _showDeleteConfirmation(BuildContext context) {
    DialogUtils.showConfirm(
      title: '确认删除',
      message: '你确定要删除这条日记吗？此操作无法撤销。',
      confirmText: '删除',
      cancelText: '取消',
      onConfirm: onDelete,
    );
  }
}
