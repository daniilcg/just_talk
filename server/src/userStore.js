import fs from "node:fs";
import path from "node:path";

const DATA_PATH = process.env.USERS_DB_PATH
  ? path.resolve(process.env.USERS_DB_PATH)
  : path.resolve(process.cwd(), "users.json");

function defaultDb() {
  return {
    // Legacy numeric uid allocator (kept for backwards compatibility with old dbs)
    nextUid: 1,
    // uid -> { uid, nicknameLower, nickname, emailLower|null, email|null, passwordHash, createdAtMs }
    users: {}
  };
}

function readDb() {
  try {
    const raw = fs.readFileSync(DATA_PATH, "utf8");
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== "object") return defaultDb();
    if (!parsed.users || typeof parsed.users !== "object") parsed.users = {};
    if (!Number.isInteger(parsed.nextUid) || parsed.nextUid < 1) parsed.nextUid = 1;
    return parsed;
  } catch {
    return defaultDb();
  }
}

function writeDb(db) {
  const tmp = `${DATA_PATH}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(db, null, 2), "utf8");
  fs.renameSync(tmp, DATA_PATH);
}

function padUid(n) {
  return String(n).padStart(7, "0");
}

function normalizeHandle(input) {
  const raw = String(input ?? "").trim();
  const lower = raw.toLowerCase();
  // Allow: a-z 0-9 _ . -
  const ok = /^[a-z0-9_.-]{3,20}$/.test(lower);
  return ok ? lower : null;
}

export class UserStore {
  constructor() {
    this.db = readDb();
  }

  persist() {
    writeDb(this.db);
  }

  /** @returns {{uid:string, nickname:string, email:string|null}} */
  createUser({ nickname, email, passwordHash }) {
    // New behavior: UID == nickname handle (immutable)
    const handle = normalizeHandle(nickname);
    if (!handle) throw new Error("bad_handle");
    if (!passwordHash || typeof passwordHash !== "string" || passwordHash.length < 10) throw new Error("bad_password_hash");

    // Ensure unique handle (case-insensitive)
    for (const u of Object.values(this.db.users)) {
      if (String(u.nicknameLower ?? "").toLowerCase() === handle) throw new Error("nickname_taken");
      if (String(u.uid ?? "").toLowerCase() === handle) throw new Error("uid_taken");
    }

    const uid = handle;
    const emailStr = email ? String(email).trim() : "";
    const emailLower = emailStr ? emailStr.toLowerCase() : null;
    if (emailLower && !emailLower.includes("@")) throw new Error("bad_email");

    this.db.users[uid] = {
      uid,
      nickname: uid,
      nicknameLower: uid,
      email: emailLower ? emailStr : null,
      emailLower,
      passwordHash,
      fcmToken: null,
      createdAtMs: Date.now()
    };
    this.persist();
    return { uid, nickname: uid, email: emailLower ? emailStr : null };
  }

  /** @returns {{uid:string, nickname:string, email:string|null}|null} */
  getByUid(uid) {
    const keyRaw = String(uid ?? "").trim();
    const key = normalizeHandle(keyRaw) ?? keyRaw; // allow legacy numeric ids too
    let u = this.db.users[key] ?? null;
    if (!u) {
      // Backward compatibility: allow logging in by nickname for legacy numeric uid accounts
      const n = normalizeHandle(keyRaw);
      if (n) u = this.getByNickname(n);
    }
    if (!u) return null;
    return {
      uid: u.uid,
      nickname: u.nickname,
      email: u.email ?? null,
      passwordHash: u.passwordHash ?? null,
      fcmToken: u.fcmToken ?? null
    };
  }

  /** @returns {{uid:string, nickname:string, email:string|null}|null} */
  getByNickname(nickname) {
    const nickLower = normalizeHandle(nickname);
    if (!nickLower) return null;
    for (const u of Object.values(this.db.users)) {
      if (u.nicknameLower === nickLower)
        return {
          uid: u.uid,
          nickname: u.nickname,
          email: u.email ?? null,
          passwordHash: u.passwordHash ?? null,
          fcmToken: u.fcmToken ?? null
        };
    }
    return null;
  }

  setFcmToken(uid, token) {
    const id = String(uid ?? "").trim();
    const t = String(token ?? "").trim();
    const u = this.db.users[id];
    if (!u) return false;
    u.fcmToken = t || null;
    this.persist();
    return true;
  }
}

