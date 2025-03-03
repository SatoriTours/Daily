import 'package:flutter/material.dart';

import 'package:daily_satori/app/styles/colors.dart';

/// 自定义弹出菜单项
class CustomMenuItem<T> {
  final T value;
  final String title;
  final IconData icon;

  CustomMenuItem({required this.value, required this.title, required this.icon});
}

/// 自定义弹出菜单
class CustomPopupMenu<T> extends StatelessWidget {
  final List<CustomMenuItem<T>> items;
  final Function(T) onSelected;
  final Widget? icon;
  final String? tooltip;

  const CustomPopupMenu({super.key, required this.items, required this.onSelected, this.icon, this.tooltip});

  @override
  Widget build(BuildContext context) {
    return PopupMenuButton<T>(
      icon: icon ?? Icon(Icons.more_horiz, color: AppColors.textPrimary(context)),
      tooltip: tooltip,
      offset: const Offset(0, 50),
      padding: EdgeInsets.zero,
      color: AppColors.cardBackground(context),
      elevation: 4,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      itemBuilder: (context) => _buildPopupMenuItems(context),
      onSelected: onSelected,
    );
  }

  List<PopupMenuEntry<T>> _buildPopupMenuItems(BuildContext context) {
    return items.map((item) => _buildPopupMenuItem(context, item)).toList();
  }

  PopupMenuEntry<T> _buildPopupMenuItem(BuildContext context, CustomMenuItem<T> item) {
    return PopupMenuItem<T>(
      value: item.value,
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Row(
        children: [
          Icon(item.icon, size: 20, color: AppColors.textPrimary(context)),
          const SizedBox(width: 12),
          Text(
            item.title,
            style: TextStyle(fontSize: 14, color: AppColors.textPrimary(context), fontWeight: FontWeight.w500),
          ),
        ],
      ),
    );
  }
}
