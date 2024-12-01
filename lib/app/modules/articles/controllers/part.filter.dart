part of 'articles_controller.dart';

extension PartFilter on ArticlesController {
  // 处理所有的过滤条件
  List<Article> _queryByFilter([Condition<Article>? condition]) {
    Condition<Article>? finalCondition;

    final conditions = [
      _favoriteCondition(),
      _searchCondition(),
      condition,
    ].whereType<Condition<Article>>();

    // 组合所有非空条件
    for (var condition in conditions) {
      finalCondition =
          finalCondition == null ? condition : finalCondition & condition;
    }

    final query = articleBox.query(finalCondition);
    _tagFilter(query);

    // 按ID倒序排序
    query.order(Article_.id, flags: Order.descending);

    return (query.build()..limit = _pageSize).find();
  }

  Condition<Article>? _favoriteCondition() {
    if (_onlyFavorite.value) {
      return Article_.isFavorite.equals(true);
    }
    return null;
  }

  // 处理搜索的条件
  Condition<Article>? _searchCondition() {
    if (enableSearch.value && _searchText.isNotEmpty) {
      return Article_.title.contains(_searchText) |
          Article_.aiTitle.contains(_searchText) |
          Article_.content.contains(_searchText) |
          Article_.aiContent.contains(_searchText);
    }
    return null;
  }

  void _tagFilter(QueryBuilder<Article> query) async {
    logger.i("搜索标签ID: $_tagID");
    if (_tagID > 0) {
      query.linkMany(Article_.tags, Tag_.id.equals(_tagID));
    }
  }
}
