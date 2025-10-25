import 'package:daily_satori/app/objectbox/image.dart';
import 'package:daily_satori/app/models/image_model.dart';
import 'package:daily_satori/app/models/article_model.dart';
import 'package:daily_satori/app/repositories/base_repository.dart';
import 'package:daily_satori/objectbox.g.dart';

/// 图片仓储类
///
/// 继承 BaseRepository 获取通用 CRUD 功能
/// 使用单例模式，通过 ImageRepository.instance 访问
class ImageRepository extends BaseRepository<Image, ImageModel> {
  // 私有构造函数
  ImageRepository._();

  // 单例
  static final ImageRepository instance = ImageRepository._();

  // 每页数量
  @override
  int get pageSize => 50;

  // ==================== BaseRepository 必须实现的方法 ====================

  @override
  ImageModel toModel(Image entity) {
    return ImageModel(entity);
  }

  @override
  Image toEntity(ImageModel model) {
    return model.entity;
  }

  // ==================== 特定业务方法 ====================

  /// 查找所有图片（返回Model）
  @override
  List<ImageModel> allModels() {
    return all().map((e) => ImageModel(e)).toList();
  }

  /// 根据ID查找图片（返回Model）
  @override
  ImageModel? findModel(int id) {
    final image = find(id);
    return image != null ? ImageModel(image) : null;
  }

  /// 根据路径查找图片
  ImageModel? findByPath(String path) {
    final image = findFirstByStringEquals(Image_.path, path);
    return image != null ? ImageModel(image) : null;
  }

  /// 使用数据创建图片模型
  ImageModel createWithData(Map<String, dynamic> data, ArticleModel articleModel) {
    final image = Image(url: data['url'], path: data['path']);
    image.article.target = articleModel.entity;
    return ImageModel(image);
  }

  /// 保存图片Model
  Future<int> createModel(ImageModel imageModel) async {
    return await box.putAsync(imageModel.entity);
  }

  /// 更新图片Model
  @override
  Future<int> updateModel(ImageModel imageModel) async {
    return await box.putAsync(imageModel.entity);
  }

  /// 删除图片（旧方法名兼容）
  bool destroy(int id) {
    return remove(id);
  }

  /// 根据URL查找图片
  ImageModel? findByUrl(String url) {
    final image = findFirstByStringEquals(Image_.url, url);
    return image != null ? ImageModel(image) : null;
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
