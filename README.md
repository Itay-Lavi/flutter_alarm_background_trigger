# Flutter background alarm trigger

A flutter plugin for Android to launch app from background at specific time just like stock alarm app in Android

#### Installation

```yaml
flutter_alarm_background_trigger: ^1.0.0
# or
flutter pub add flutter_alarm_background_trigger
```

#### Initialization

```dart
void main() {
  // Very important to call before initialize since it 
  // ensures the binding is available and ready before 
  // any native call
  WidgetsFlutterBinding.ensureInitialized();

  // initialize Required for alarm events to bind with flutter method channel
  FlutterAlarmBackgroundTrigger.initialize();

  runApp(const MyApp());
}

```

#### Android exact-alarm setup

This plugin schedules exact alarms. Apps targeting Android 12 (API 31) or
newer must declare the exact-alarm access that is appropriate for the app in
their Android manifest. For example, apps that ask the user for special app
access can declare:

```xml
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
```

Grant this access before calling `addAlarm`. If Android does not currently
allow the app to schedule exact alarms, `addAlarm` fails with the platform
error code `EXACT_ALARM_PERMISSION_DENIED` and no alarm is saved.

The plugin declares `RECEIVE_BOOT_COMPLETED` and restores future pending
alarms from its local database after a device reboot. Android delivers the
boot broadcast only after the user has launched the installed app at least
once. Alarms whose scheduled time passed while the device was powered off are
marked `DONE`; they are not fired or deleted. Granting exact-alarm access later
also causes future pending alarms to be restored.

#### Create instance

```dart
var alarmPlugin = FlutterAlarmBackgroundTrigger();
```

#### Set Alarm

```dart
alarmPlugin.addAlarm(
      // Required
      DateTime.now().add(Duration(seconds: 10)),

      //Optional
      uid: "YOUR_APP_ID_TO_IDENTIFY",
      payload: {"YOUR_EXTRA_DATA":"FOR_ALARM"},

      // screenWakeDuration: For how much time you want 
      // to make screen awake when alarm triggered
      screenWakeDuration: Duration(minutes: 1)
  )
```

#### Receive event when alarm trigger

```dart
alarmPlugin.requestPermission().then((isGranted){
  if(isGranted){
    alarmPlugin.onForegroundAlarmEventHandler((alarm){
      // Perform your action here such as navigation
      // This event will be triggered on both cases, 
      // when app is in foreground or background!
      print(alarm.id)
    })
  }
})
```

## Additional methods

#### Request permission to draw over other apps

```dart
Future<bool> requestPermission()
```

This method requests overlay permission only. Exact-alarm access must be
handled by the application as described above.

#### Add

```dart
Future<AlarmItem> addAlarm(
  DateTime time, 
  {
    String? uid, 
    Map<String, dynamic>? payload, 
    Duration screenWakeDuration
  }
)
```

#### Get alarm by uid

```dart
Future<List<AlarmItem>> getAlarmByUid(String uid)
```

#### Get all scheduled alarms

```dart
Future<List<AlarmItem>> getAllAlarms()
```

#### Get single alarm

```dart
Future<AlarmItem> getAlarm(int id)
```

#### Get alarm by payload

```dart
Future<List<AlarmItem>> getAlarmByPayload(Map<String, dynamic> payload)
```

#### Get alarm by time

```dart
Future<List<AlarmItem>> getAlarmByTime(DateTime time)
```

#### Alarm trigger event

```dart
void onForegroundAlarmEventHandler(OnForegroundAlarmEvent alarmEvent)
```

#### Delete single alarm

```dart
Future<void> deleteAlarm(int id)
```

#### Delete by payload

```dart
Future<void> deleteAlarmsByPayload(Map<String, dynamic> payload)
```

#### Delete by time

```dart
Future<void> deleteAlarmsByTime(DateTime dateTime)
```

#### Delete by uid

```dart
Future<void> deleteAlarmsByUid(String uid)
```

#### Delete all scheduled alarms

```dart
Future<void> deleteAllAlarms()
```
