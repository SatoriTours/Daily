import 'package:daily_satori/app/objectbox/session.dart';
import 'package:daily_satori/app/repositories/base_repository.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/objectbox.g.dart';

/// Session仓库类
///
/// 继承 BaseRepository 获取通用 CRUD 功能
class SessionRepository extends BaseRepository<SessionEntity> {
  // 私有构造函数
  SessionRepository._();

  // 单例
  static final SessionRepository instance = SessionRepository._();

  // 获取Box实例
  @override
  Box<SessionEntity> get box => ObjectboxService.i.box<SessionEntity>();

  // 每页数量
  @override
  int get pageSize => 50;

  /// 根据会话ID查找会话
  SessionEntity? findBySessionId(String sessionId) {
    return findFirstByStringEquals(SessionEntity_.sessionId, sessionId);
  }

  /// 删除会话
  bool removeBySessionId(String sessionId) {
    final session = findBySessionId(sessionId);
    if (session != null) {
      return remove(session.id);
    }
    return false;
  }

  /// 更新会话的最后访问时间
  bool updateLastAccessedAt(String sessionId, DateTime lastAccessedAt) {
    final session = findBySessionId(sessionId);
    if (session != null) {
      session.lastAccessedAt = lastAccessedAt;
      save(session);
      return true;
    }
    return false;
  }

  /// 设置会话的认证状态
  bool authenticate(String sessionId, String username) {
    final session = findBySessionId(sessionId);
    if (session != null) {
      session.isAuthenticated = true;
      session.username = username;
      session.lastAccessedAt = DateTime.now();
      save(session);
      return true;
    }
    return false;
  }

  /// 清除会话的认证状态
  bool clearAuthentication(String sessionId) {
    final session = findBySessionId(sessionId);
    if (session != null) {
      session.isAuthenticated = false;
      session.username = null;
      session.lastAccessedAt = DateTime.now();
      save(session);
      return true;
    }
    return false;
  }

  /// 清理过期会话 (30分钟不活动即过期)
  void cleanExpiredSessions() {
    final expireTime = DateTime.now().subtract(const Duration(minutes: 30));

    // 查询所有会话
    final allSessions = all();
    final expiredSessions = allSessions.where((session) => session.lastAccessedAt.isBefore(expireTime)).toList();

    if (expiredSessions.isNotEmpty) {
      // 删除过期会话
      final expiredIds = expiredSessions.map((s) => s.id).toList();
      removeMany(expiredIds);
      logger.d('已清理${expiredSessions.length}个过期会话');
    }
  }
}
