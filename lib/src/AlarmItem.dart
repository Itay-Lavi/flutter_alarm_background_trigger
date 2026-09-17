// ignore_for_file: constant_identifier_names

import 'dart:convert';

enum AlarmStatus { PENDING, DONE }

enum AlarmArgKey { TIME, PAYLOAD, UID, ID, SCREEN_WAKE_DURATION }

class AlarmItem {
  int? id;
  DateTime? time;
  Map<String, dynamic>? payload;
  String? uid;
  AlarmStatus status = AlarmStatus.PENDING;

  // Extras
  Duration? screenWakeDuration;

  AlarmItem(
      {this.id,
      this.time,
      this.payload,
      this.uid,
      this.status = AlarmStatus.PENDING,
      this.screenWakeDuration});

  factory AlarmItem.fromJson(Map<String, dynamic> data) {
    return AlarmItem(
      id: data['id'] ?? data['a'],
      time: DateTime.fromMillisecondsSinceEpoch(data['time'] ?? data['b']),
      payload: jsonDecode(data['payload'] ?? data['c'] ?? 'null'),
      uid: data['userUid'] ?? data['d'],
      status: AlarmStatus.values.firstWhere(
        (e) => e.name == (data['status'] ?? data['f']),
      ),
      screenWakeDuration:
          Duration(milliseconds: data['screenWakeDuration'] ?? data['g']),
    );
  }

  static List<AlarmItem> fromJsonList(List<dynamic> list) {
    List<AlarmItem> alarmItems = [];
    alarmItems.addAll(list.map((e) => AlarmItem.fromJson(e)));
    return alarmItems;
  }

  Map<String, dynamic> toMap() {
    Map<String, dynamic> map = {};
    map[AlarmArgKey.ID.name] = id;
    map[AlarmArgKey.TIME.name] = time?.millisecondsSinceEpoch;
    map[AlarmArgKey.PAYLOAD.name] =
        payload != null ? jsonEncode(payload) : null;
    map[AlarmArgKey.UID.name] = uid;
    map[AlarmArgKey.SCREEN_WAKE_DURATION.name] =
        screenWakeDuration?.inMilliseconds;
    return map;
  }
}
