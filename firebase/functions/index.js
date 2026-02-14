const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

const db = admin.firestore();
const messaging = admin.messaging();

/**
 * Send notification when a friend request is created
 */
exports.onFriendRequestCreated = functions.firestore
  .document("friend_requests/{requestId}")
  .onCreate(async (snapshot, context) => {
    const request = snapshot.data();
    const requestId = context.params.requestId;

    if (!request || !request.to_user_id) {
      console.log("Invalid friend request data");
      return null;
    }

    try {
      // Get the target user's FCM token
      const targetUserDoc = await db.collection("users").doc(request.to_user_id).get();
      const targetUser = targetUserDoc.data();

      if (!targetUser || !targetUser.fcm_token) {
        console.log("Target user not found or no FCM token");
        return null;
      }

      // Send push notification
      const message = {
        token: targetUser.fcm_token,
        data: {
          type: "friend_request",
          request_id: requestId,
          from_user_id: request.from_user_id || "",
          from_user_name: request.from_user_name || "Someone",
          from_user_email: request.from_user_email || "",
          from_user_photo: request.from_user_photo || "",
        },
        android: {
          priority: "high",
          notification: {
            channelId: "friend_requests",
            title: "New Friend Request",
            body: `${request.from_user_name || "Someone"} wants to be your friend`,
            icon: "ic_person_add",
          },
        },
      };

      const response = await messaging.send(message);
      console.log("Friend request notification sent:", response);
      return response;
    } catch (error) {
      console.error("Error sending friend request notification:", error);
      return null;
    }
  });

/**
 * Send notification when a friend request is accepted
 */
exports.onFriendRequestAccepted = functions.firestore
  .document("friend_requests/{requestId}")
  .onUpdate(async (change, context) => {
    const before = change.before.data();
    const after = change.after.data();

    // Only trigger if status changed to ACCEPTED
    if (before.status === after.status || after.status !== "ACCEPTED") {
      return null;
    }

    try {
      // Get the original sender's FCM token
      const senderDoc = await db.collection("users").doc(after.from_user_id).get();
      const sender = senderDoc.data();

      if (!sender || !sender.fcm_token) {
        console.log("Sender not found or no FCM token");
        return null;
      }

      // Get the acceptor's info
      const acceptorDoc = await db.collection("users").doc(after.to_user_id).get();
      const acceptor = acceptorDoc.data();

      // Send notification to original sender
      const message = {
        token: sender.fcm_token,
        data: {
          type: "friend_accepted",
          friend_id: after.to_user_id || "",
          friend_name: acceptor?.display_name || "Someone",
          friend_email: acceptor?.email || "",
        },
        android: {
          priority: "high",
          notification: {
            channelId: "friend_requests",
            title: "Friend Request Accepted",
            body: `${acceptor?.display_name || "Someone"} accepted your friend request!`,
            icon: "ic_person_add",
          },
        },
      };

      const response = await messaging.send(message);
      console.log("Friend accepted notification sent:", response);
      return response;
    } catch (error) {
      console.error("Error sending friend accepted notification:", error);
      return null;
    }
  });

/**
 * Send high-priority alert notification
 */
exports.onAlertCreated = functions.firestore
  .document("alerts/{alertId}")
  .onCreate(async (snapshot, context) => {
    const alert = snapshot.data();
    const alertId = context.params.alertId;

    if (!alert || !alert.to_user_id) {
      console.log("Invalid alert data");
      return null;
    }

    try {
      // Get the target user's FCM token
      const targetUserDoc = await db.collection("users").doc(alert.to_user_id).get();
      const targetUser = targetUserDoc.data();

      if (!targetUser || !targetUser.fcm_token) {
        console.log("Target user not found or no FCM token");
        return null;
      }

      // Send high-priority push notification
      const message = {
        token: targetUser.fcm_token,
        data: {
          type: "alert",
          alert_id: alertId,
          from_user_id: alert.from_user_id || "",
          from_user_name: alert.from_user_name || "A friend",
          from_user_photo: alert.from_user_photo || "",
          message: alert.message || "is trying to reach you!",
        },
        android: {
          priority: "high",
          ttl: 0, // Immediate delivery
          notification: {
            channelId: "alerts",
            title: `🚨 Alert from ${alert.from_user_name || "A friend"}`,
            body: `${alert.from_user_name || "A friend"} ${alert.message || "is trying to reach you!"}`,
            icon: "ic_alert",
            sound: "default",
            priority: "max",
            visibility: "public",
            defaultSound: true,
            defaultVibrateTimings: true,
          },
        },
      };

      const response = await messaging.send(message);
      console.log("Alert notification sent:", response);
      return response;
    } catch (error) {
      console.error("Error sending alert notification:", error);
      return null;
    }
  });

/**
 * Cleanup old locations (runs daily)
 * Removes location data older than 24 hours for privacy
 */
exports.cleanupOldLocations = functions.pubsub
  .schedule("every 24 hours")
  .onRun(async (context) => {
    const cutoff = admin.firestore.Timestamp.fromDate(
      new Date(Date.now() - 24 * 60 * 60 * 1000)
    );

    try {
      const snapshot = await db.collection("locations")
        .where("updated_at", "<", cutoff)
        .get();

      const batch = db.batch();
      snapshot.docs.forEach((doc) => {
        batch.delete(doc.ref);
      });

      await batch.commit();
      console.log(`Cleaned up ${snapshot.size} old location records`);
      return null;
    } catch (error) {
      console.error("Error cleaning up locations:", error);
      return null;
    }
  });

/**
 * Update user's last active timestamp on location update
 */
exports.onLocationUpdate = functions.firestore
  .document("locations/{userId}")
  .onWrite(async (change, context) => {
    const userId = context.params.userId;

    if (!change.after.exists) {
      return null; // Document was deleted
    }

    try {
      await db.collection("users").doc(userId).update({
        last_active: admin.firestore.FieldValue.serverTimestamp(),
      });
      return null;
    } catch (error) {
      console.error("Error updating last active:", error);
      return null;
    }
  });
