// SimpleRemote anonymous code statistics.
//   POST /v1/events   {"events":[{t,cat,brand,code,src,ok,step,mixed,days,phone,sdk,v,ts}, ...]}  (from the app)
//   GET  /v1/codes?key=...&cat=tv[&brand=LG]   success rate per code set (for you, to rank codes)
// No IPs or headers are stored.

const MAX_EVENTS = 100;
const str = (v, n) => (typeof v === "string" ? v.slice(0, n) : null);
const int = (v) => (Number.isInteger(v) ? v : null);

function clean(e) {
  if (!e || typeof e !== "object") return null;
  const row = {
    t: str(e.t, 16), cat: str(e.cat, 32), brand: str(e.brand, 64), code: str(e.code, 128), src: str(e.src, 16),
    ok: typeof e.ok === "boolean" ? (e.ok ? 1 : 0) : null, step: int(e.step), mixed: e.mixed === true ? 1 : 0,
    days: int(e.days), phone: str(e.phone, 64), sdk: int(e.sdk), v: str(e.v, 16), ts: int(e.ts),
  };
  if (!["setup", "deleted"].includes(row.t)) return null;
  if (!row.cat || !row.code || row.brand === null || row.src === null || !row.phone || row.sdk === null || !row.v || row.ts === null) return null;
  return row;
}

async function postEvents(request, env) {
  if (Number(request.headers.get("content-length") || 0) > 100_000) return new Response("too large", { status: 413 });
  let body;
  try { body = await request.json(); } catch { return new Response("bad json", { status: 400 }); }
  const rows = (Array.isArray(body?.events) ? body.events : []).slice(0, MAX_EVENTS).map(clean).filter(Boolean);
  if (rows.length) {
    const now = Math.floor(Date.now() / 1000);
    const stmt = env.DB.prepare(
      "INSERT INTO events (t,cat,brand,code,src,ok,step,mixed,days,phone,sdk,v,ts,received) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
    await env.DB.batch(rows.map((r) =>
      stmt.bind(r.t, r.cat, r.brand, r.code, r.src, r.ok, r.step, r.mixed, r.days, r.phone, r.sdk, r.v, r.ts, now)));
  }
  return Response.json({ stored: rows.length });
}

async function getCodes(url, env) {
  if (!env.STATS_READ_KEY || url.searchParams.get("key") !== env.STATS_READ_KEY) return new Response("forbidden", { status: 403 });
  const cat = url.searchParams.get("cat") || "tv";
  const brand = url.searchParams.get("brand");
  const { results } = await env.DB.prepare(
    `SELECT code, src, brand,
            SUM(t='setup' AND ok=1) AS worked, SUM(t='setup' AND ok=0) AS failed,
            SUM(t='deleted' AND days < 2) AS deleted_soon
       FROM events WHERE cat = ?1 AND (?2 IS NULL OR brand = ?2)
      GROUP BY code ORDER BY worked DESC, failed ASC LIMIT 500`).bind(cat, brand).all();
  return Response.json(results);
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "POST" && url.pathname === "/v1/events") return postEvents(request, env);
    if (request.method === "GET" && url.pathname === "/v1/codes") return getCodes(url, env);
    return new Response("not found", { status: 404 });
  },
};
