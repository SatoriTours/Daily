import 'package:daily_satori/app/objectbox/image.dart';
import 'package:daily_satori/app/models/image_model.dart';
import 'package:daily_satori/app/models/article_model.dart';
import 'package:daily_satori/app/repositories/base_repository.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 图片仓储类
///
/// 继承 BaseRepository 获取通用 CRUD 功能
/// 使用单例模式，通过 ImageRepository.i 访问
class ImageRepository extends BaseRepository<Image, ImageModel> {
  // 私有构造函数
  ImageRepository._();

  // 单例
  static final i = ImageRepository._();

  // ==================== BaseRepository 必须实现的方法 ====================

  @override
  ImageModel toModel(Image entity) {
    return ImageModel(entity);
  }

  // ==================== 特定业务方法 ====================

  /// 根据路径查找图片
  ImageModel? findByPath(String path) {
    return findFirstByStringEquals(Image_.path, path);
  }

  /// 使用数据创建图片模型
  ImageModel createWithData(Map<String, dynamic> data, ArticleModel articleModel) {
    final image = Image(url: data['url'], path: data['path']);
    image.article.target = articleModel.entity;
    return ImageModel(image);
  }

  /// 删除图片（旧方法名兼容）
  bool destroy(int id) {
    return remove(id);
  }

  /// 根据URL查找图片
  ImageModel? findByUrl(String url) {
    return findFirstByStringEquals(Image_.url, url);
  }

  /// 根据文章ID删除图片
  int deleteByArticleId(int articleId) {
    final query = box.query(Image_.article.equals(articleId)).build();
    try {
      final images = query.find();
      if (images.isEmpty) return 0;
      return removeMany(images.map((image) => image.id).toList());
    } finally {
      query.close();
    }
  }
}
