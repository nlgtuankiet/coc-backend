const functions = require("firebase-functions");
const admin = require("firebase-admin");
const axios = require("axios").default;

admin.initializeApp();

const messageDocumentPath = "/chats/{chatId}/messages/{messageId}";

exports.forwardNewMessageCreate = functions.firestore
    .document(messageDocumentPath)
    .onCreate((snapshot, context) => {
      const forwardUrl = process.env.FORWARD_URL;
      if (!forwardUrl) {
        throw new Error("env FORWARD_URL missing");
      }
      return forwardEvent(
          "onCreate",
          messageDocumentPath,
          snapshot,
          context,
          forwardUrl,
      );
    });

function forwardEvent(
    eventName,
    documentPath,
    snapshot,
    context,
    url,
) {
  // metadata
  const payload = {};
  payload.eventName = eventName;
  payload.documentPath = documentPath;

  // snapshot
  const snapshotPayload = {};
  snapshotPayload.createTime = snapshot.createTime?.toMillis();
  snapshotPayload.exists = snapshot.exists;
  snapshotPayload.id = snapshot.id;
  snapshotPayload.readTime = snapshot.readTime?.toMillis();
  snapshotPayload.refPath = snapshot.ref.path;
  snapshotPayload.data = snapshot.data();

  payload.snapshot = snapshotPayload;
  return axios.post(url, snapshotPayload);
}
