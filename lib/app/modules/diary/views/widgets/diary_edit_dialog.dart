import 'package:flutter/material.dart';
import 'package:daily_satori/app/models/diary_model.dart';
import 'package:daily_satori/app/styles/diary_style.dart';

import '../../controllers/diary_controller.dart';
import 'diary_input_decoration.dart';
import 'image_preview.dart';
import 'diary_toolbar.dart';

/// 日记编辑对话框组件
class DiaryEditDialog extends StatefulWidget {
  final DiaryModel diary;
  final DiaryController controller;

  const DiaryEditDialog({super.key, required this.diary, required this.controller});

  @override
  State<DiaryEditDialog> createState() => _DiaryEditDialogState();
}

class _DiaryEditDialogState extends State<DiaryEditDialog> {
  late TextEditingController contentController;
  late List<String> currentImages;
  final List<String> imagesToDelete = [];

  @override
  void initState() {
    super.initState();
    contentController = TextEditingController(text: widget.diary.content);
    currentImages = widget.diary.images?.split(',') ?? [];
  }

  @override
  void dispose() {
    contentController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      height: MediaQuery.of(context).size.height * 0.8,
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom + 8, left: 16, right: 16, top: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // 编辑区域
          Expanded(
            child: TextField(
              controller: contentController,
              maxLines: null,
              expands: true,
              autofocus: true,
              textAlignVertical: TextAlignVertical.top,
              decoration: DiaryInputDecoration.get(context),
              style: TextStyle(fontSize: 16, height: 1.5, color: DiaryStyle.primaryTextColor(context)),
            ),
          ),

          // 显示现有图片
          if (currentImages.isNotEmpty)
            ImagePreview(
              images: currentImages,
              onDelete:
                  (index) => setState(() {
                    // 标记要删除的图片
                    imagesToDelete.add(currentImages[index]);
                    currentImages.removeAt(index);
                  }),
            ),

          // 工具栏和操作按钮
          DiaryToolbar(
            controller: contentController,
            onImagePick: () => widget.controller.pickAndSaveImages(context, setState, currentImages),
            onSave:
                () => widget.controller.updateDiaryWithImages(
                  context,
                  widget.diary,
                  contentController,
                  currentImages,
                  imagesToDelete,
                ),
            saveLabel: '更新',
          ),
        ],
      ),
    );
  }
}
