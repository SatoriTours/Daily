part of 'articles_controller.dart';

extension PartFilter on ArticlesController {
  // 处理所有的过滤条件
  void _addFilterExpression(SimpleSelectStatement<$ArticlesTable, Article> select) {
    _addSearchExpression(select);
  }

  // 处理搜索的条件
  void _addSearchExpression(SimpleSelectStatement<$ArticlesTable, Article> select) {
    if (enableSearch.value && _searchText.isNotEmpty) {
      final searchExpression = "%$_searchText%";
      // 使用 where 条件的时候,头文件需要包含 import 'package:drift/drift.dart'; 不然会报错找不到 like 方法
      select.where((t) {
        return t.title.like(searchExpression) |
            t.aiTitle.like(searchExpression) |
            t.content.like(searchExpression) |
            t.aiContent.like(searchExpression);
      });
    }
  }
}
