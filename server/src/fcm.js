import admin from "firebase-admin";

let messaging = null;

function tryInit() {
  if (messaging) return messaging;
  try {
    if (admin.apps.length === 0) {
      // Uses GOOGLE_APPLICATION_CREDENTIALS if set.
      admin.initializeApp();
    }
    messaging = admin.messaging();
    return messaging;
  } catch {
    return null;
  }
}

/**
 * @param {{token:string, data: Record<string,string>}} args
 * @returns {Promise<boolean>}
 */
export async function sendPush({ token, data }) {
  const m = tryInit();
  if (!m) return false;
  try {
    await m.send({
      token,
      data
    });
    return true;
  } catch {
    return false;
  }
}

