part of 'share_dialog_controller.dart';

extension PartTags on ShareDialogController {
  Future<void> _saveTags(Article article, List<String> tags) async {
    logger.i("[ShareDialogController] 开始保存标签: $tags");

    // 清除文章现有标签
    article.tags.removeWhere((tag) => true);

    // 获取或创建标签
    for (var tagTitle in tags) {
      // 查找已存在的标签
      var tag = tagBox.query(Tag_.name.equals(tagTitle)).build().findFirst();

      // 如果标签不存在,创建新标签
      if (tag == null) {
        tag = Tag(name: tagTitle);
        tagBox.put(tag);
      }

      // 添加标签到文章
      article.tags.add(tag);
    }

    // 保存文章
    articleBox.put(article);

    TagsService.i.reload();
    logger.i("[ShareDialogController] 标签保存完成 ${article.id}");
  }
}
