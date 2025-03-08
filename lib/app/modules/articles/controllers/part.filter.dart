part of 'articles_controller.dart';

extension PartFilter on ArticlesController {
  /// 根据条件查询文章列表
  List<ArticleModel> _queryByFilter([int? referenceId, bool? isGreaterThan]) {
    String? keyword = enableSearch.value && _searchText.isNotEmpty ? _searchText : null;
    bool? favorite = _onlyFavorite.value ? true : null;
    List<int>? tagIds = _tagID > 0 ? [_tagID] : null;

    // 使用增强的Repository方法，直接在数据库层进行所有过滤、排序和分页
    return ArticleRepository.where(
      keyword: keyword,
      isFavorite: favorite,
      tagIds: tagIds,
      referenceId: referenceId,
      isGreaterThan: isGreaterThan,
      pageSize: _pageSize,
    );
  }
}
