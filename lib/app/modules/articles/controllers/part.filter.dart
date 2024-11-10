part of 'articles_controller.dart';

extension PartFilter on ArticlesController {
  // 处理所有的过滤条件
  SimpleSelectStatement<$ArticlesTable, Article> _addFilterExpression() {
    final select = _db.select(_db.articles);

    select
      ..orderBy([(t) => OrderingTerm(expression: t.id, mode: OrderingMode.desc)])
      ..limit(_pageSize);

    _addSearchExpression(select);
    _addFavoriteExpression(select);
    return select;
  }

  void _addFavoriteExpression(SimpleSelectStatement<$ArticlesTable, Article> select) {
    if (_onlyFavorite.value) select.where((t) => t.isFavorite.equals(true));
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

  Future<List<Article>> _getArticlesByTagID(SimpleSelectStatement<$ArticlesTable, Article> select) async {
    final joinSelect = select.join([
      innerJoin(_db.articleTags, _db.articleTags.articleId.equalsExp(_db.articles.id)),
    ])
      ..where(_db.articleTags.tagId.equals(_tagID));

    final rows = await joinSelect.get();
    return rows.map((e) => e.readTable(_db.articles)).toList();
  }

  Future<List<Article>> _getArticles(SimpleSelectStatement<$ArticlesTable, Article> select) async {
    logger.i("搜索标签ID: $_tagID");
    if (_tagID > 0) {
      return _getArticlesByTagID(select);
    } else {
      return select.get();
    }
  }
}
