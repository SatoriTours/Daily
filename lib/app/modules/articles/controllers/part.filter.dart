part of 'articles_controller.dart';

extension PartFilter on ArticlesController {
  /// 根据条件查询文章列表
  List<ArticleModel> _queryByFilter([int? referenceId, bool? isGreaterThan]) {
    String? keyword = enableSearch.value && _searchText.isNotEmpty ? _searchText : null;
    bool? favorite = _onlyFavorite.value ? true : null;
    List<int>? tagIds = _tagID > 0 ? [_tagID] : null;

    // 基本查询条件
    var models = ArticleModel.where(keyword: keyword, isFavorite: favorite, tagIds: tagIds);

    // 如果有额外的ID条件
    if (referenceId != null && isGreaterThan != null) {
      // 根据ID过滤结果
      if (isGreaterThan) {
        models = models.where((model) => model.id > referenceId).toList();
      } else {
        models = models.where((model) => model.id < referenceId).toList();
      }
    }

    // 按ID排序
    models.sort((a, b) => b.id.compareTo(a.id));

    // 应用分页
    if (models.length > _pageSize) {
      models = models.sublist(0, _pageSize);
    }

    return models;
  }

  /// 构建最终的查询条件
  Condition<Article>? _buildFinalCondition(Condition<Article>? additionalCondition) {
    final conditions =
        [_buildFavoriteCondition(), _buildSearchCondition(), additionalCondition].whereType<Condition<Article>>();

    return conditions.fold<Condition<Article>?>(
      null,
      (finalCondition, condition) => finalCondition == null ? condition : finalCondition & condition,
    );
  }

  /// 构建收藏过滤条件
  Condition<Article>? _buildFavoriteCondition() {
    return _onlyFavorite.value ? Article_.isFavorite.equals(true) : null;
  }

  /// 构建搜索过滤条件
  Condition<Article>? _buildSearchCondition() {
    if (!enableSearch.value || _searchText.isEmpty) return null;

    return Article_.title.contains(_searchText) |
        Article_.aiTitle.contains(_searchText) |
        Article_.content.contains(_searchText) |
        Article_.aiContent.contains(_searchText) |
        Article_.comment.contains(_searchText);
  }

  /// 应用标签过滤
  void _applyTagFilter(QueryBuilder<Article> query) {
    if (_tagID > 0) {
      logger.i('应用标签过滤: $_tagID');
      query.linkMany(Article_.tags, Tag_.id.equals(_tagID));
    }
  }

  /// 应用排序规则
  void _applySorting(QueryBuilder<Article> query) {
    query.order(Article_.id, flags: Order.descending);
  }

  /// 执行查询并返回结果
  List<Article> _executeQuery(QueryBuilder<Article> query) {
    return (query.build()..limit = _pageSize).find();
  }
}
