import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:daily_satori/app_exports.dart';

import '../../controllers/diary_controller.dart';

/// 日记页面的应用栏
class DiaryAppBar extends StatelessWidget implements PreferredSizeWidget {
  final DiaryController controller;

  const DiaryAppBar({super.key, required this.controller});

  @override
  Widget build(BuildContext context) {
    return AppBar(
      title: Obx(() {
        if (controller.searchQuery.value.isNotEmpty) {
          return TextField(
            controller: controller.searchController,
            autofocus: true,
            decoration: const InputDecoration(
              hintText: '搜索...',
              border: InputBorder.none,
              hintStyle: TextStyle(color: Colors.white70),
            ),
            style: const TextStyle(color: Colors.white),
            onChanged: controller.search,
          );
        }
        return const Text('我的日记');
      }),
      actions: [
        // 搜索按钮
        Obx(() {
          if (controller.searchQuery.value.isEmpty) {
            return IconButton(
              icon: const Icon(Icons.search),
              onPressed: () {
                controller.enableSearch(true);
              },
            );
          } else {
            return IconButton(
              icon: const Icon(Icons.close),
              onPressed: () {
                controller.clearFilters();
                controller.enableSearch(false);
              },
            );
          }
        }),

        // 标签筛选
        IconButton(
          icon: const Icon(Icons.tag),
          onPressed: () {
            _showTagsDialog(context);
          },
        ),
      ],
    );
  }

  /// 显示标签选择对话框
  void _showTagsDialog(BuildContext context) {
    showDialog(
      context: context,
      builder: (BuildContext context) {
        return AlertDialog(
          title: const Text('选择标签'),
          content: SizedBox(
            width: double.maxFinite,
            child: Obx(() {
              if (controller.tags.isEmpty) {
                return const Center(child: Text('没有找到标签'));
              }

              return ListView.builder(
                shrinkWrap: true,
                itemCount: controller.tags.length,
                itemBuilder: (context, index) {
                  final tag = controller.tags[index];
                  return ListTile(
                    title: Text(tag),
                    onTap: () {
                      controller.filterByTag(tag);
                      Navigator.pop(context);
                    },
                  );
                },
              );
            }),
          ),
          actions: [
            TextButton(
              onPressed: () {
                controller.clearFilters();
                Navigator.pop(context);
              },
              child: const Text('清除筛选'),
            ),
            TextButton(
              onPressed: () {
                Navigator.pop(context);
              },
              child: const Text('关闭'),
            ),
          ],
        );
      },
    );
  }

  /// 启用/禁用搜索
  void enableSearch(bool enable) {
    if (enable) {
      controller.searchController.clear();
    } else {
      controller.clearFilters();
    }
  }

  @override
  Size get preferredSize => const Size.fromHeight(kToolbarHeight);
}
