import React, { useEffect, useState } from 'react';
import { Icon, ToastProvider } from './components/ui';
import { api, auth, setUnauthorizedHandler } from './lib/api';
import Login from './screens/Login';
import Today from './screens/Today';
import Cashflow from './screens/Cashflow';
import Profit from './screens/Profit';
import Debts from './screens/Debts';
import Leaks from './screens/Leaks';
import Simulator from './screens/Simulator';
import Ask from './screens/Ask';
import Commission from './screens/Commission';
import Expenses from './screens/Expenses';

export type Tab = 'today' | 'commission' | 'cash' | 'profit' | 'debts' | 'leaks' | 'sim' | 'ask' | 'expenses';

const NAV: { id: Tab; label: string; short: string; icon: string }[] = [
  { id: 'today', label: 'Hôm nay', short: 'Hôm nay', icon: 'sun' },
  { id: 'commission', label: 'Hoa hồng tư vấn', short: 'Hoa hồng', icon: 'shield' },
  { id: 'cash', label: 'Dòng tiền', short: 'Tiền', icon: 'wave' },
  { id: 'profit', label: 'Lợi nhuận', short: 'Lãi', icon: 'pie' },
  { id: 'debts', label: 'Công nợ', short: 'Nợ', icon: 'scale' },
  { id: 'leaks', label: 'Rò rỉ tiền', short: 'Rò rỉ', icon: 'drop' },
  { id: 'sim', label: 'Mô phỏng', short: 'Nếu…', icon: 'sliders' },
  { id: 'ask', label: 'Hỏi trợ lý', short: 'Hỏi', icon: 'spark' },
  { id: 'expenses', label: 'Nhập chi phí', short: 'Chi phí', icon: 'calendar' },
];
const MOBILE_NAV: Tab[] = ['today', 'commission', 'debts', 'leaks', 'ask'];

export default function App() {
  const [logged, setLogged] = useState<boolean>(() => !!auth.token());
  const [user, setUser] = useState<string>('');
  const [mock, setMock] = useState(false);
  const [tab, setTab] = useState<Tab>(() => {
    const h = window.location.hash.slice(1) as Tab;
    return NAV.some((n) => n.id === h) ? h : 'today';
  });

  useEffect(() => { setUnauthorizedHandler(() => setLogged(false)); }, []);
  useEffect(() => {
    if (!logged) return;
    api<{ username: string }>('/auth/me').then((m) => setUser(m.username)).catch(() => {});
    api<{ mock: boolean }>('/commission/meta').then((m) => setMock(m.mock)).catch(() => {});
  }, [logged]);
  useEffect(() => {
    try { history.replaceState(null, '', `#${tab}`); } catch { /* sandbox */ }
    window.scrollTo({ top: 0 });
  }, [tab]);

  const logout = async () => {
    try { await api('/auth/logout', { method: 'POST' }); } catch { /* vẫn đăng xuất phía trình duyệt */ }
    auth.clear();
    setLogged(false);
  };

  if (!logged) return <ToastProvider><Login onDone={() => setLogged(true)} /></ToastProvider>;

  return (
    <ToastProvider>
      <div className="app">
        <aside className="side">
          <div className="brandbox">
            <div className="logo">YB</div>
            <div><b>AI Assistant</b><span>Yellow Beauty Center</span></div>
          </div>
          <nav>
            {NAV.map((n) => (
              <button key={n.id} className={`nav ${tab === n.id ? 'on' : ''}`} onClick={() => setTab(n.id)}>
                <Icon name={n.icon} />
                <span>{n.label}</span>
              </button>
            ))}
          </nav>
          <div className="side-foot">
            {mock && <div className="pill warn" style={{ marginBottom: 8 }}>Dữ liệu giả · V1</div>}
            <div className="muted small">Đăng nhập: <b>{user || '…'}</b></div>
            <button className="btn sm ghost" style={{ marginTop: 8 }} onClick={logout}>Đăng xuất</button>
          </div>
        </aside>

        <main className="main" key={tab}>
          {tab === 'today' && <Today go={setTab} />}
          {tab === 'commission' && <Commission />}
          {tab === 'cash' && <Cashflow go={setTab} />}
          {tab === 'profit' && <Profit go={setTab} />}
          {tab === 'debts' && <Debts />}
          {tab === 'leaks' && <Leaks go={setTab} />}
          {tab === 'sim' && <Simulator />}
          {tab === 'ask' && <Ask />}
          {tab === 'expenses' && <Expenses />}
        </main>

        <nav className="bottom">
          {MOBILE_NAV.map((id) => {
            const n = NAV.find((x) => x.id === id)!;
            return (
              <button key={id} className={tab === id ? 'on' : ''} onClick={() => setTab(id)}>
                <Icon name={n.icon} size={20} />
                <span>{n.short}</span>
              </button>
            );
          })}
        </nav>
        <div className="quick">
          {(['cash', 'profit', 'sim', 'expenses'] as Tab[]).map((id) => {
            const n = NAV.find((x) => x.id === id)!;
            return <button key={id} className={tab === id ? 'on' : ''} onClick={() => setTab(id)}><Icon name={n.icon} size={15} />{n.label}</button>;
          })}
          <button onClick={logout}>Đăng xuất</button>
        </div>
      </div>
    </ToastProvider>
  );
}
