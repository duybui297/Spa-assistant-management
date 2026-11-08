import React, { useState } from 'react';
import { api, auth } from '../lib/api';

export default function Login({ onDone }: { onDone: () => void }) {
  const [username, setU] = useState('');
  const [password, setP] = useState('');
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setErr(null);
    try {
      const r = await api<{ token: string }>('/auth/login', { body: { username, password } });
      auth.set(r.token);
      onDone();
    } catch (x: any) {
      setErr(x.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="login-wrap">
      <form className="card pad login" onSubmit={submit}>
        <div className="brandbox" style={{ marginBottom: 18 }}>
          <div className="logo">YB</div>
          <div><b>AI Assistant</b><span>Yellow Beauty Center</span></div>
        </div>
        <label className="fld"><span>Tên đăng nhập</span>
          <input autoFocus value={username} onChange={(e) => setU(e.target.value)} autoComplete="username" />
        </label>
        <label className="fld"><span>Mật khẩu</span>
          <input type="password" value={password} onChange={(e) => setP(e.target.value)} autoComplete="current-password" />
        </label>
        {err && <div className="callout bad small">{err}</div>}
        <button className="btn" disabled={busy || !username || !password}>{busy ? 'Đang đăng nhập…' : 'Đăng nhập'}</button>
        <p className="muted small" style={{ marginTop: 12 }}>Phiên đăng nhập được giữ đến khi bấm Đăng xuất. Lần mở đầu tiên có thể chờ 1–2 phút do máy chủ miễn phí đang khởi động.</p>
      </form>
    </div>
  );
}
