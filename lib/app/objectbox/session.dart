import 'package:objectbox/objectbox.dart';

@Entity()
class SessionEntity {
  @Id()
  int id = 0;

  @Unique()
  String sessionId;

  bool isAuthenticated;
  String? username;

  @Property(type: PropertyType.date)
  DateTime createdAt;

  @Property(type: PropertyType.date)
  DateTime lastAccessedAt;

  SessionEntity({
    this.id = 0,
    required this.sessionId,
    this.isAuthenticated = false,
    this.username,
    required this.createdAt,
    required this.lastAccessedAt,
  });
}
