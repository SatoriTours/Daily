import 'package:daily_satori/app/objectbox/session.dart';
import 'package:daily_satori/app/services/logger_service.dart';
import 'package:daily_satori/app/services/objectbox_service.dart';
import 'package:daily_satori/objectbox.g.dart';

/// Session仓库类
///
/// 用于管理会话数据的存储和检索
class SessionRepository {
  /// 获取Session Box
  static Box<SessionEntity> get _box => ObjectboxService.i.box<SessionEntity>();

  /// 根据会话ID查找会话
  static SessionEntity? findBySessionId(String sessionId) {
    final query = _box.query(SessionEntity_.sessionId.equals(sessionId)).build();
    try {
      return query.findFirst();
    } finally {
      query.close();
    }
  }

  /// 保存会话
  static int saveSession(SessionEntity session) {
    return _box.put(session);
  }

  /// 删除会话
  static bool removeSession(String sessionId) {
    final session = findBySessionId(sessionId);
    if (session != null) {
      return _box.remove(session.id);
    }
    return false;
  }

  /// 更新会话的最后访问时间
  static bool updateLastAccessedAt(String sessionId, DateTime lastAccessedAt) {
    final session = findBySessionId(sessionId);
    if (session != null) {
      session.lastAccessedAt = lastAccessedAt;
      _box.put(session);
      return true;
    }
    return false;
  }

  /// 设置会话的认证状态
  static bool authenticate(String sessionId, String username) {
    final session = findBySessionId(sessionId);
    if (session != null) {
      session.isAuthenticated = true;
      session.username = username;
      session.lastAccessedAt = DateTime.now();
      _box.put(session);
      return true;
    }
    return false;
  }

  /// 清除会话的认证状态
  static bool clearAuthentication(String sessionId) {
    final session = findBySessionId(sessionId);
    if (session != null) {
      session.isAuthenticated = false;
      session.username = null;
      session.lastAccessedAt = DateTime.now();
      _box.put(session);
      return true;
    }
    return false;
  }

  /// 清理过期会话 (30分钟不活动即过期)
  static void cleanExpiredSessions() {
    final expireTime = DateTime.now().subtract(const Duration(minutes: 30));

    // 查询过期的会话
    final query = _box.query().build();
    try {
      final allSessions = query.find();
      final expiredSessions = allSessions.where((session) => session.lastAccessedAt.isBefore(expireTime)).toList();

      if (expiredSessions.isNotEmpty) {
        // 删除过期会话
        for (var session in expiredSessions) {
          _box.remove(session.id);
        }
        logger.d('已清理${expiredSessions.length}个过期会话');
      }
    } finally {
      query.close();
    }
  }
}
