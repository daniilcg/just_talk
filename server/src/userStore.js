import fs from "node:fs";
import path from "node:path";

const DATA_PATH = process.env.USERS_DB_PATH
  ? path.resolve(process.env.USERS_DB_PATH)
  : path.resolve(process.cwd(), "users.json");

function defaultDb() {
  return {
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

export class UserStore {
  constructor() {
    this.db = readDb();
  }

  persist() {
    writeDb(this.db);
  }

  /** @returns {{uid:string, nickname:string, email:string|null}} */
  createUser({ nickname, email, passwordHash }) {
    const nick = String(nickname ?? "").trim();
    const nickLower = nick.toLowerCase();
    if (nickLower.length < 3) throw new Error("bad_nickname");
    if (!passwordHash || typeof passwordHash !== "string" || passwordHash.length < 10) throw new Error("bad_password_hash");

    // ensure unique nickname
    for (const u of Object.values(this.db.users)) {
      if (u.nicknameLower === nickLower) throw new Error("nickname_taken");
    }

    const uid = padUid(this.db.nextUid++);
    const emailStr = email ? String(email).trim() : "";
    const emailLower = emailStr ? emailStr.toLowerCase() : null;
    if (emailLower && !emailLower.includes("@")) throw new Error("bad_email");

    this.db.users[uid] = {
      uid,
      nickname: nick,
      nicknameLower: nickLower,
      email: emailLower ? emailStr : null,
      emailLower,
      passwordHash,
      fcmToken: null,
      createdAtMs: Date.now()
    };
    this.persist();
    return { uid, nickname: nick, email: emailLower ? emailStr : null };
  }

  /** @returns {{uid:string, nickname:string, email:string|null}|null} */
  getByUid(uid) {
    const u = this.db.users[String(uid ?? "")] ?? null;
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
    const nickLower = String(nickname ?? "").trim().toLowerCase();
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

