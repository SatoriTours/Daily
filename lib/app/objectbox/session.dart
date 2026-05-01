import 'package:objectbox/objectbox.dart';
import 'package:daily_satori/app/objectbox/base/base_entity.dart';

@Entity()
class SessionEntity implements BaseEntity {
  @override
  @Id()
  int id = 0;

  @override
  @Property(type: PropertyType.date)
  late DateTime createdAt;

  @override
  @Property(type: PropertyType.date)
  late DateTime updatedAt;

  @Unique()
  String sessionId;

  bool isAuthenticated;
  String? username;

  @Property(type: PropertyType.date)
  DateTime lastAccessedAt;

  SessionEntity({
    this.id = 0,
    required this.sessionId,
    this.isAuthenticated = false,
    this.username,
    DateTime? createdAt,
    required this.lastAccessedAt,
  }) {
    this.createdAt = createdAt ?? DateTime.now();
    updatedAt = lastAccessedAt;
  }
}
