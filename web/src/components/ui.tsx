import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';

/* ---------- Icons (inline, không cần thư viện) ---------- */
const P = (d: string) => <path d={d} />;
const ICONS: Record<string, React.ReactNode> = {
  sun: <>{P('M12 3v2M12 19v2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M3 12h2M19 12h2M5.6 18.4 7 17M17 7l1.4-1.4')}<circle cx="12" cy="12" r="4" /></>,
  wave: <>{P('M3 17l4-6 4 3 4-7 6 9')}{P('M3 21h18')}</>,
  pie: <>{P('M12 3v9l7.8 4.5A9 9 0 1 1 12 3z')}{P('M15 3.5A9 9 0 0 1 20.5 9H15z')}</>,
  drop: <>{P('M12 3s6 6.5 6 11a6 6 0 0 1-12 0c0-4.5 6-11 6-11z')}{P('M9.5 14.5a2.5 2.5 0 0 0 2.5 2.5')}</>,
  scale: <>{P('M12 3v18M5 21h14M6 7h12')}{P('M6 7l-3 7a3 3 0 0 0 6 0zM18 7l-3 7a3 3 0 0 0 6 0z')}</>,
  sliders: <>{P('M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12M20 18h0')}<circle cx="16" cy="6" r="2" /><circle cx="10" cy="12" r="2" /><circle cx="18" cy="18" r="2" /></>,
  spark: <>{P('M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z')}{P('M19 16l.8 2.2L22 19l-2.2.8L19 22l-.8-2.2L16 19l2.2-.8z')}</>,
  phone: P('M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2'),
  arrowR: P('M5 12h14M13 6l6 6-6 6'),
  check: P('M5 12l5 5 9-10'),
  x: P('M6 6l12 12M18 6 6 18'),
  send: P('M4 12 20 4l-6 16-3-7z'),
  copy: <><rect x="8" y="8" width="12" height="12" rx="2" />{P('M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2')}</>,
  alert: <>{P('M12 4 2 20h20z')}{P('M12 10v4M12 17v.5')}</>,
  bell: <>{P('M6 16V11a6 6 0 0 1 12 0v5l2 2H4z')}{P('M10 20a2 2 0 0 0 4 0')}</>,
  calendar: <><rect x="3" y="5" width="18" height="16" rx="2" />{P('M3 10h18M8 3v4M16 3v4')}</>,
  shield: P('M12 3 4 6v6c0 5 3.5 8 8 9 4.5-1 8-4 8-9V6z'),
  trend: P('M3 17l6-6 4 4 8-8M15 7h6v6'),
  mic: <><rect x="9" y="3" width="6" height="11" rx="3" />{P('M5 11a7 7 0 0 0 14 0M12 18v3')}</>,
};

export function Icon({ name, size = 18, stroke = 1.8, className }: { name: keyof typeof ICONS | string; size?: number; stroke?: number; className?: string }) {
  return (
    <svg className={className} width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={stroke} strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      {ICONS[name]}
    </svg>
  );
}

/* ---------- Ring gauge ---------- */
export function Ring({ value, size = 128, label, sub }: { value: number; size?: number; label: string; sub: string }) {
  const r = size / 2 - 10;
  const c = 2 * Math.PI * r;
  const [v, setV] = useState(0);
  useEffect(() => { const t = setTimeout(() => setV(value), 80); return () => clearTimeout(t); }, [value]);
  const color = value >= 75 ? 'var(--good)' : value >= 50 ? 'var(--warn)' : 'var(--bad)';
  return (
    <div className="ring" style={{ width: size, height: size }}>
      <svg width={size} height={size}>
        <circle cx={size / 2} cy={size / 2} r={r} stroke="var(--line)" strokeWidth="9" fill="none" />
        <circle cx={size / 2} cy={size / 2} r={r} stroke={color} strokeWidth="9" fill="none" strokeLinecap="round"
          strokeDasharray={c} strokeDashoffset={c * (1 - v / 100)} transform={`rotate(-90 ${size / 2} ${size / 2})`}
          style={{ transition: 'stroke-dashoffset 1.2s cubic-bezier(.2,.8,.2,1)', filter: `drop-shadow(0 0 8px ${color})` }} />
      </svg>
      <div className="ring-c">
        <b>{label}</b>
        <span>{sub}</span>
      </div>
    </div>
  );
}

/* ---------- Toast ---------- */
type Toast = { id: number; text: string; tone: 'good' | 'info' };
const ToastCtx = createContext<(t: string, tone?: Toast['tone']) => void>(() => {});
export const useToast = () => useContext(ToastCtx);

export function ToastProvider({ children }: { children: React.ReactNode }) {
  const [items, setItems] = useState<Toast[]>([]);
  const show = useCallback((text: string, tone: Toast['tone'] = 'good') => {
    const id = Date.now() + Math.random();
    setItems((x) => [...x, { id, text, tone }]);
    setTimeout(() => setItems((x) => x.filter((t) => t.id !== id)), 2800);
  }, []);
  return (
    <ToastCtx.Provider value={show}>
      {children}
      <div className="toasts">
        {items.map((t) => (
          <div key={t.id} className={`toast ${t.tone}`}><Icon name={t.tone === 'good' ? 'check' : 'bell'} size={16} />{t.text}</div>
        ))}
      </div>
    </ToastCtx.Provider>
  );
}

/* ---------- Modal ---------- */
export function Modal({ open, onClose, title, children }: { open: boolean; onClose: () => void; title: string; children: React.ReactNode }) {
  useEffect(() => {
    const k = (e: KeyboardEvent) => e.key === 'Escape' && onClose();
    window.addEventListener('keydown', k);
    return () => window.removeEventListener('keydown', k);
  }, [onClose]);
  if (!open) return null;
  return (
    <div className="modal-bg" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()} role="dialog" aria-label={title}>
        <div className="modal-h">
          <h3>{title}</h3>
          <button className="icon-btn" onClick={onClose} aria-label="Đóng"><Icon name="x" /></button>
        </div>
        {children}
      </div>
    </div>
  );
}

export function SectionTitle({ kicker, title, right }: { kicker?: string; title: string; right?: React.ReactNode }) {
  return (
    <div className="sec-title">
      <div>
        {kicker && <div className="kicker">{kicker}</div>}
        <h2>{title}</h2>
      </div>
      {right}
    </div>
  );
}
