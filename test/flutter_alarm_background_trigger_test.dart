import 'package:flutter_alarm_background_trigger/flutter_alarm_background_trigger_method_channel.dart';
import 'package:flutter_alarm_background_trigger/flutter_alarm_background_trigger_platform_interface.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('uses the method channel implementation by default', () {
    expect(FlutterAlarmBackgroundTriggerPlatform.instance,
        isInstanceOf<MethodChannelFlutterAlarmBackgroundTrigger>());
  });
}
