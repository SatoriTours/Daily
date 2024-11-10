part of 'share_dialog_controller.dart';

extension PartTags on ShareDialogController {
  Future<void> _saveTags(Article? article, List<String> tags) async {
    if (article == null) return;
    // 先删除文章所有标签
    await db.articleTags.deleteWhere((tbl) => tbl.articleId.equals(article.id));

    // 获取或创建标签
    final tagIds = await Future.wait(tags.map((tagTitle) async {
      // 查找已存在的标签
      final existingTags = await (db.select(db.tags)..where((t) => t.title.equals(tagTitle))).get();

      if (existingTags.isNotEmpty) {
        return existingTags.first.id;
      }

      // 如果标签不存在,创建新标签
      final newTag = await db.into(db.tags).insertReturning(TagsCompanion(
            title: drift.Value(tagTitle),
          ));

      return newTag.id;
    }));

    // 保存文章标签
    await db.articleTags.insertAll(tagIds.map((tagId) => ArticleTagsCompanion(
          articleId: drift.Value(article.id),
          tagId: drift.Value(tagId),
        )));
  }
}
