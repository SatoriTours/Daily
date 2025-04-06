import 'package:daily_satori/app/styles/font_style.dart';
import 'package:flutter/material.dart';
import 'package:get/get.dart';

import 'package:daily_satori/app/modules/share_dialog/controllers/share_dialog_controller.dart';

import '../../../styles/index.dart';

/// 分享页面视图
/// 用于保存链接或添加/更新文章备注信息
class ShareDialogView extends GetView<ShareDialogController> {
  const ShareDialogView({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: _buildAppBar(),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 20.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _buildSpacer(),
              _buildArticleInfo(),
              _buildSpacer(),
              _buildCommentSection(),
              _buildSpacer(),
              _buildSaveButton(),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildSpacer({double height = 24}) {
    return SizedBox(height: height);
  }

  // 构建顶部应用栏
  PreferredSizeWidget _buildAppBar() {
    return AppBar(
      title: Text(controller.isUpdate.value ? '更新文章' : '保存链接', style: MyFontStyle.appBarTitleStyle),
      automaticallyImplyLeading: !controller.isUpdate.value,
    );
  }

  // 构建文章信息区域
  Widget _buildArticleInfo() {
    if (controller.isUpdate.value) {
      return _buildArticleTitle();
    } else {
      return _buildArticleURL();
    }
  }

  // 构建文章标题区域
  Widget _buildArticleTitle() {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(Icons.article_outlined, size: 18),
        _buildSpacer(height: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text("文章标题", style: MyFontStyle.bodyLarge),
              _buildSpacer(height: 4),
              Text(
                controller.articleTitle.value,
                style: MyFontStyle.bodyMedium,
                maxLines: 3,
                overflow: TextOverflow.ellipsis,
              ),
            ],
          ),
        ),
      ],
    );
  }

  // 构建文章URL区域
  Widget _buildArticleURL() {
    return Container(
      padding: const EdgeInsets.all(20),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(Icons.link_rounded, size: 18),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(controller.getShortUrl(controller.shareURL.value), style: MyFontStyle.bodyMedium),
                const SizedBox(height: 4),
                Text(
                  controller.shareURL.value,
                  style: MyFontStyle.bodyMedium,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  // 构建备注信息区域
  Widget _buildCommentSection() {
    return Expanded(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.only(left: 4, bottom: 12),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                Icon(Icons.comment_outlined, size: 18),
                const SizedBox(width: 10),
                Text("备注信息", style: TextStyle(fontSize: 16, fontWeight: FontWeight.w500)),
              ],
            ),
          ),
          Expanded(
            child: TextField(
              controller: controller.commentController,
              decoration: InputDecoration(
                hintText: "添加备注信息（可选）",
                border: OutlineInputBorder(borderRadius: BorderRadius.circular(12), borderSide: BorderSide.none),
                contentPadding: const EdgeInsets.all(16),
              ),
              style: MyFontStyle.bodyMedium,
              minLines: 3,
              maxLines: null,
              keyboardType: TextInputType.multiline,
              textInputAction: TextInputAction.newline,
            ),
          ),
        ],
      ),
    );
  }

  // 构建保存按钮
  Widget _buildSaveButton() {
    return Padding(
      padding: const EdgeInsets.only(bottom: 24),
      child: SizedBox(
        width: double.infinity,
        height: 48,
        child: ElevatedButton(onPressed: () => controller.onSaveButtonPressed(), child: const Text("保存")),
      ),
    );
  }
}
