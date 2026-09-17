# Changelog

## 1.2.0

* Restore future pending Android alarms after reboot and after exact-alarm access is granted.
* Mark alarms missed while the device was powered off as `DONE` without firing them.
* Reject exact-alarm scheduling safely when permission is unavailable and roll back failed inserts.
* Persist screen wake duration and retain Room data across schema mismatches instead of deleting it.

## 1.1.0

* Added `moveToBackground()` functionality to send the app to background on Android.

## 1.0.0

* Implemented background alarm trigger with broadcast receiver from android
* Implemented room persistance library to store alarms
* Added flutter method channel bindings for communication
* Added example app with basic features Implemented
* Updated README & Changelog

## 1.0.1

* Updated README for permission code sample
