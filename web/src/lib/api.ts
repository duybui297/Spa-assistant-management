// Gọi API. Token đăng nhập lưu ở localStorage (domain riêng của app AI), phiên giữ đến khi bấm Đăng xuất.
const BASE = (import.meta.env.VITE_API_URL as string | undefined)?.replace(/\/$/, '') ?? '';
const KEY = 'spa-ai-token';

export const auth = {
  token: () => { try { return localStorage.getItem(KEY); } catch { return null; } },
  set: (t: string) => { try { localStorage.setItem(KEY, t); } catch { /* bỏ qua */ } },
  clear: () => { try { localStorage.removeItem(KEY); } catch { /* bỏ qua */ } },
};

export class ApiError extends Error {
  constructor(public status: number, message: string) { super(message); }
}

let onUnauthorized: () => void = () => {};
export const setUnauthorizedHandler = (f: () => void) => { onUnauthorized = f; };

export async function api<T>(path: string, opts: { method?: string; body?: unknown; query?: Record<string, string | number | undefined | null> } = {}): Promise<T> {
  const q = opts.query
    ? '?' + Object.entries(opts.query).filter(([, v]) => v !== undefined && v !== null && v !== '')
      .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`).join('&')
    : '';
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  const t = auth.token();
  if (t) headers.Authorization = `Bearer ${t}`;
  let res: Response;
  try {
    res = await fetch(`${BASE}/api${path}${q}`, {
      method: opts.method ?? (opts.body !== undefined ? 'POST' : 'GET'),
      headers,
      body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
    });
  } catch {
    throw new ApiError(0, 'Không kết nối được máy chủ. Bản miễn phí có thể đang "ngủ", chờ 1–2 phút rồi thử lại.');
  }
  if (res.status === 401 && path !== '/auth/login') { auth.clear(); onUnauthorized(); }
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) throw new ApiError(res.status, data?.error ?? `Lỗi ${res.status}`);
  return data as T;
}
