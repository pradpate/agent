# FriendLocator

A real-time friend location sharing Android application with Google Sign-In authentication, friend management, location tracking, and high-priority alert notifications.

## Features

### User Authentication
- Google Sign-In integration for secure authentication
- User profile stored in Firebase (display name, email, profile picture)
- Automatic FCM token management for push notifications

### Friend Management
- Search and add friends by Google email address
- Friend request system with pending, accepted, and declined states
- Push notifications for incoming friend requests
- View and manage friends list

### Location Sharing
- Real-time location tracking using Android's FusedLocationProvider
- Background location updates with foreground service
- Location data synced to Firebase Firestore
- Interactive map view showing all friends' locations
- Real-time marker updates as friends move

### Alert System
- Send page/alert notifications to friends
- High-priority notifications that bypass Do Not Disturb
- Alarm-level sound and vibration patterns
- Full-screen intent for urgent alerts

## Project Structure

```
agent/
├── app/
│   ├── src/main/
│   │   ├── java/com/friendlocator/app/
│   │   │   ├── auth/           # Authentication components
│   │   │   ├── data/           # Data models and repositories
│   │   │   ├── di/             # Dependency injection
│   │   │   ├── friends/        # Friend management UI
│   │   │   ├── location/       # Location service and map
│   │   │   ├── notifications/  # FCM and alert handling
│   │   │   ├── settings/       # Settings screen
│   │   │   └── ui/theme/       # App theming
│   │   ├── res/                # Android resources
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── firebase/
│   └── functions/              # Cloud Functions for notifications
├── firestore.rules             # Firestore security rules
├── firestore.indexes.json      # Firestore indexes
└── firebase.json               # Firebase configuration
```

## Setup Instructions

### 1. Firebase Project Setup

1. Create a new Firebase project at [Firebase Console](https://console.firebase.google.com)
2. Enable the following services:
   - Authentication (Google Sign-In provider)
   - Cloud Firestore
   - Cloud Messaging
   - Cloud Functions

### 2. Configure Google Sign-In

1. In Firebase Console, go to Authentication > Sign-in method
2. Enable Google Sign-In provider
3. Note the Web client ID (you'll need this)

### 3. Add Android App to Firebase

1. In Firebase Console, add an Android app
2. Package name: `com.friendlocator.app`
3. Download `google-services.json`
4. Place it in `app/` directory

### 4. Configure API Keys

1. Copy `secrets.properties.template` to `secrets.properties`
2. Add your Google Maps API key:
   ```
   MAPS_API_KEY=your_actual_api_key
   ```

3. Update `app/src/main/res/values/strings.xml`:
   ```xml
   <string name="default_web_client_id">YOUR_WEB_CLIENT_ID</string>
   ```

### 5. Enable Google Maps API

1. Go to [Google Cloud Console](https://console.cloud.google.com)
2. Enable Maps SDK for Android
3. Create an API key with Android restrictions

### 6. Deploy Firebase Functions

```bash
cd firebase/functions
npm install
firebase deploy --only functions
```

### 7. Deploy Firestore Rules and Indexes

```bash
firebase deploy --only firestore:rules
firebase deploy --only firestore:indexes
```

## Database Schema

### Users Collection (`/users/{userId}`)
```
{
  email: string,
  display_name: string,
  profile_picture_url: string,
  fcm_token: string,
  created_at: timestamp,
  last_active: timestamp,
  location_sharing_enabled: boolean
}
```

### Friend Requests Collection (`/friend_requests/{requestId}`)
```
{
  from_user_id: string,
  to_user_id: string,
  from_user_email: string,
  from_user_name: string,
  from_user_photo: string,
  to_user_email: string,
  status: "PENDING" | "ACCEPTED" | "DECLINED",
  created_at: timestamp,
  updated_at: timestamp
}
```

### Friendships Collection (`/friendships/{friendshipId}`)
```
{
  user_id: string,
  friend_id: string,
  friend_email: string,
  friend_name: string,
  friend_photo: string,
  created_at: timestamp
}
```

### Locations Collection (`/locations/{userId}`)
```
{
  user_id: string,
  geo_point: GeoPoint,
  latitude: number,
  longitude: number,
  accuracy: number,
  altitude: number,
  speed: number,
  bearing: number,
  updated_at: timestamp
}
```

### Alerts Collection (`/alerts/{alertId}`)
```
{
  from_user_id: string,
  to_user_id: string,
  from_user_name: string,
  from_user_photo: string,
  message: string,
  is_read: boolean,
  created_at: timestamp
}
```

## Building the App

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
```

## Required Permissions

- `INTERNET` - Network access
- `ACCESS_FINE_LOCATION` - Precise location
- `ACCESS_COARSE_LOCATION` - Approximate location
- `ACCESS_BACKGROUND_LOCATION` - Background location updates
- `FOREGROUND_SERVICE` - Foreground service for location
- `POST_NOTIFICATIONS` - Push notifications
- `VIBRATE` - Vibration for alerts
- `WAKE_LOCK` - Keep device awake for alerts
- `USE_FULL_SCREEN_INTENT` - Full-screen alerts
- `SCHEDULE_EXACT_ALARM` - Precise alarm scheduling

## Technology Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Architecture**: MVVM with Repository pattern
- **Dependency Injection**: Hilt
- **Backend**: Firebase (Auth, Firestore, FCM, Cloud Functions)
- **Maps**: Google Maps SDK
- **Location**: FusedLocationProvider
- **Async**: Kotlin Coroutines + Flow

## License

MIT License
