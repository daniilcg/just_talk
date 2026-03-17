import { WebSocketServer } from "ws";
import { UserStore } from "./userStore.js";
import bcrypt from "bcryptjs";

const PORT = Number.parseInt(process.env.PORT ?? "8080", 10);

/**
 * Protocol (JSON messages):
 * - join: {type:"join", room:"<id>", peerId:"<random>"}
 * - signal: {type:"signal", room:"<id>", from:"<peerId>", to:"<peerId|null>", payload:{...}}
 * - leave: {type:"leave", room:"<id>", peerId:"<peerId>"}
 *
 * Directory (online-only) protocol:
 * - signup: {type:"signup", nickname:"nick", email:"a@b.com|null", password:"...", peerId:"<id>"} -> {type:"signup_ok", uid:"0000001", nickname:"nick", email:null|"a@b.com"}
 * - login: {type:"login", uid:"0000001", password:"...", peerId:"<id>"} -> {type:"login_ok", uid, nickname, email}
 * - lookup_uid: {type:"lookup_uid", uid:"0000001"} -> {type:"lookup_uid_result", uid, nickname, onlinePeerId:null|"<id>"}
 * - lookup_nickname: {type:"lookup_nickname", nickname:"nick"} -> {type:"lookup_nickname_result", nickname, uid:null|"0000001", onlinePeerId:null|"<id>"}
 * - invite_uid: {type:"invite_uid", from:"<peerId>", toUid:"0000001", room:"<roomId>"} -> to target: {type:"invite", from:"<peerId>", room:"<roomId>"}
 *
 * Server does NOT store chat/media; only relays signaling messages to peers in same room.
 */

/** @type {Map<string, Map<string, import("ws").WebSocket>>} */
const rooms = new Map();

/** @type {Map<string, string>} uid -> peerId (online only) */
const onlineByUid = new Map();
/** @type {Map<string, string>} nicknameLower -> uid (persistent lives in userStore) */
const onlineByNickLower = new Map(); // points to uid (online only)

/** @type {Map<string, import("ws").WebSocket>} peerId -> ws (for invites) */
const peers = new Map();

const userStore = new UserStore();

function getRoom(roomId) {
  let room = rooms.get(roomId);
  if (!room) {
    room = new Map();
    rooms.set(roomId, room);
  }
  return room;
}

function safeJsonParse(str) {
  try {
    return JSON.parse(str);
  } catch {
    return null;
  }
}

function send(ws, msg) {
  if (ws.readyState === ws.OPEN) ws.send(JSON.stringify(msg));
}

function broadcastExcept(room, exceptPeerId, msg) {
  for (const [peerId, peerWs] of room.entries()) {
    if (peerId === exceptPeerId) continue;
    send(peerWs, msg);
  }
}

const wss = new WebSocketServer({ port: PORT });

wss.on("connection", (ws) => {
  /** @type {{roomId?: string, peerId?: string, uid?: string, nicknameLower?: string}} */
  const state = {};

  ws.on("message", (data) => {
    const text = typeof data === "string" ? data : data.toString("utf8");
    const msg = safeJsonParse(text);
    if (!msg || typeof msg.type !== "string") {
      send(ws, { type: "error", code: "bad_json" });
      return;
    }

    if (msg.type === "join") {
      const roomId = String(msg.room ?? "");
      const peerId = String(msg.peerId ?? "");
      if (!roomId || !peerId) {
        send(ws, { type: "error", code: "bad_join" });
        return;
      }

      const room = getRoom(roomId);
      if (room.has(peerId)) {
        send(ws, { type: "error", code: "peer_id_taken" });
        return;
      }

      state.roomId = roomId;
      state.peerId = peerId;
      room.set(peerId, ws);

      send(ws, {
        type: "joined",
        room: roomId,
        peerId,
        peers: Array.from(room.keys()).filter((p) => p !== peerId)
      });

      broadcastExcept(room, peerId, { type: "peer_joined", room: roomId, peerId });
      return;
    }

    if (msg.type === "signup") {
      const nickname = String(msg.nickname ?? "").trim();
      const email = msg.email === null || msg.email === undefined ? null : String(msg.email ?? "").trim();
      const password = String(msg.password ?? "");
      const peerId = String(msg.peerId ?? "").trim();
      if (nickname.length < 3 || password.length < 6 || !peerId) {
        send(ws, { type: "error", code: "bad_signup" });
        return;
      }
      try {
        const passwordHash = bcrypt.hashSync(password, 10);
        const created = userStore.createUser({ nickname, email, passwordHash });
        state.uid = created.uid;
        state.nicknameLower = created.nickname.toLowerCase();
        state.peerId = peerId;
        onlineByUid.set(created.uid, peerId);
        onlineByNickLower.set(state.nicknameLower, created.uid);
        peers.set(peerId, ws);
        send(ws, { type: "signup_ok", ...created });
      } catch (e) {
        const code = e instanceof Error ? e.message : "signup_failed";
        send(ws, { type: "error", code });
      }
      return;
    }

    if (msg.type === "login") {
      const uid = String(msg.uid ?? "").trim();
      const password = String(msg.password ?? "");
      const peerId = String(msg.peerId ?? "").trim();
      if (!uid || password.length < 6 || !peerId) {
        send(ws, { type: "error", code: "bad_login" });
        return;
      }
      const u = userStore.getByUid(uid);
      if (!u) {
        send(ws, { type: "error", code: "unknown_uid" });
        return;
      }
      if (!u.passwordHash || !bcrypt.compareSync(password, u.passwordHash)) {
        send(ws, { type: "error", code: "bad_password" });
        return;
      }
      state.uid = u.uid;
      state.nicknameLower = u.nickname.toLowerCase();
      state.peerId = peerId;
      onlineByUid.set(u.uid, peerId);
      onlineByNickLower.set(state.nicknameLower, u.uid);
      peers.set(peerId, ws);
      send(ws, { type: "login_ok", ...u });
      return;
    }

    if (msg.type === "lookup_uid") {
      const uid = String(msg.uid ?? "").trim();
      if (!uid) {
        send(ws, { type: "error", code: "bad_lookup_uid" });
        return;
      }
      const u = userStore.getByUid(uid);
      if (!u) {
        send(ws, { type: "lookup_uid_result", uid, nickname: null, onlinePeerId: null });
        return;
      }
      const onlinePeerId = onlineByUid.get(u.uid) ?? null;
      send(ws, { type: "lookup_uid_result", uid: u.uid, nickname: u.nickname, onlinePeerId });
      return;
    }

    if (msg.type === "lookup_nickname") {
      const nickname = String(msg.nickname ?? "").trim();
      if (!nickname) {
        send(ws, { type: "error", code: "bad_lookup_nickname" });
        return;
      }
      const u = userStore.getByNickname(nickname);
      if (!u) {
        send(ws, { type: "lookup_nickname_result", nickname, uid: null, onlinePeerId: null });
        return;
      }
      const onlinePeerId = onlineByUid.get(u.uid) ?? null;
      send(ws, { type: "lookup_nickname_result", nickname: u.nickname, uid: u.uid, onlinePeerId });
      return;
    }

    if (msg.type === "invite_uid") {
      const from = String(msg.from ?? state.peerId ?? "").trim();
      const toUid = String(msg.toUid ?? "").trim();
      const room = String(msg.room ?? "").trim();
      if (!from || !toUid || !room) {
        send(ws, { type: "error", code: "bad_invite" });
        return;
      }
      const toPeerId = onlineByUid.get(toUid);
      if (!toPeerId) {
        send(ws, { type: "invite_result", ok: false, reason: "not_online" });
        return;
      }
      const targetWs = peers.get(toPeerId);
      if (!targetWs) {
        send(ws, { type: "invite_result", ok: false, reason: "not_connected" });
        return;
      }
      send(targetWs, { type: "invite", from, room });
      send(ws, { type: "invite_result", ok: true, toPeerId });
      return;
    }

    if (msg.type === "signal") {
      const roomId = String(msg.room ?? state.roomId ?? "");
      const from = String(msg.from ?? state.peerId ?? "");
      const to = msg.to === null || msg.to === undefined ? null : String(msg.to);
      const payload = msg.payload ?? null;

      if (!roomId || !from || payload === null) {
        send(ws, { type: "error", code: "bad_signal" });
        return;
      }
      const room = rooms.get(roomId);
      if (!room || !room.has(from)) {
        send(ws, { type: "error", code: "not_in_room" });
        return;
      }

      if (to) {
        const target = room.get(to);
        if (!target) {
          send(ws, { type: "error", code: "target_not_found", to });
          return;
        }
        send(target, { type: "signal", room: roomId, from, to, payload });
      } else {
        // broadcast to all peers except sender
        broadcastExcept(room, from, { type: "signal", room: roomId, from, to: null, payload });
      }
      return;
    }

    if (msg.type === "leave") {
      const roomId = String(msg.room ?? state.roomId ?? "");
      const peerId = String(msg.peerId ?? state.peerId ?? "");
      if (roomId && peerId) {
        const room = rooms.get(roomId);
        if (room) {
          room.delete(peerId);
          if (room.size === 0) rooms.delete(roomId);
          else broadcastExcept(room, peerId, { type: "peer_left", room: roomId, peerId });
        }
      }
      send(ws, { type: "left" });
      ws.close();
      return;
    }

    send(ws, { type: "error", code: "unknown_type" });
  });

  ws.on("close", () => {
    const peerId = state.peerId;
    if (peerId) peers.delete(peerId);
    const uid = state.uid;
    if (uid) {
      const mapped = onlineByUid.get(uid);
      if (mapped === peerId) onlineByUid.delete(uid);
    }
    const nickLower = state.nicknameLower;
    if (nickLower) onlineByNickLower.delete(nickLower);

    const roomId = state.roomId;
    if (!roomId || !peerId) return;
    const room = rooms.get(roomId);
    if (!room) return;
    room.delete(peerId);
    if (room.size === 0) rooms.delete(roomId);
    else broadcastExcept(room, peerId, { type: "peer_left", room: roomId, peerId });
  });
});

console.log(`[justtalk-signaling] ws://0.0.0.0:${PORT}`);

