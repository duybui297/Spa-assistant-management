import React from 'react';
import { api } from '../lib/api';
import { dm, money, pct, wdDmy } from '../lib/format';
import { ErrorBox, Loading, Table, useLoad } from '../components/kit';
import { Icon, Ring } from '../components/ui';
import type { Tab } from '../App';

interface Today {
  date: string;
  kpis: { key: string; label: string; value: number; compare: number | null; changePct: number | null; note: string }[];
  briefing: string[];
  actions: { title: string; detail: string; tab: Tab; tone: string }[];
  healthScore: number;
  health: { label: string; score: number; max: number; note: string }[];
  appointments: { id: string; time: string; customerName: string; phone: string; service: string; performedBy: string; status: string }[];
  birthdays: { customerId: string; name: string; phone: string; date: string; inDays: number }[];
  expiringCards: { id: string; customerName: string; customerPhone: string; serviceName: string; remainingSessions: number; expiryDate: string }[];
}

const APPT: Record<string, [string, string]> = {
  CONFIRMED: ['Đã xác nhận', 'good'], ARRIVED: ['Đã đến', 'good'], UNCONFIRMED: ['Chưa xác nhận', 'warn'],
  ONLINE_BOOKING: ['Đặt online', 'brand'], NO_SHOW: ['Không đến', 'bad'], CANCELLED: ['Đã hủy', 'bad'],
};

export default function TodayScreen({ go }: { go: (t: Tab) => void }) {
  const d = useLoad(() => api<Today>('/assistant/today'));
  if (d.loading) return <div className="screen"><Loading text="Đang đọc số liệu hôm nay từ phần mềm Spa…" /></div>;
  if (d.error || !d.data) return <div className="screen"><ErrorBox error={d.error ?? ''} retry={d.reload} /></div>;
  const t = d.data;
  const hour = new Date().getHours();
  const hello = hour < 11 ? 'Chào buổi sáng' : hour < 14 ? 'Chào buổi trưa' : hour < 18 ? 'Chào buổi chiều' : 'Chào buổi tối';
  const tone = t.healthScore >= 75 ? 'good' : t.healthScore >= 50 ? 'warn' : 'bad';

  return (
    <div className="screen">
      <header className="hello">
        <div>
          <div className="kicker">{wdDmy(t.date)} · Yellow Beauty Center by Dr. Hạnh</div>
          <h1>{hello} <span className="wave-hand">👋</span></h1>
          <p className="lead">Hôm nay có <b>{t.actions.length} việc nên làm</b> và <b>{t.appointments.length} lịch hẹn</b>.</p>
        </div>
      </header>

      <section className="hero card glow">
        <div className="hero-ring">
          <Ring value={t.healthScore} label={`${t.healthScore}`} sub="Sức khỏe" />
          <div className="hero-ring-t">
            <div className={`pill ${tone}`}>{tone === 'good' ? 'Ổn định' : tone === 'warn' ? 'Cần chú ý' : 'Cần xử lý'}</div>
            <div className="health-parts">
              {t.health.map((h) => (
                <div key={h.label} className="hp"><span>{h.label}</span><b>{h.score}/{h.max}</b><i className="muted small">{h.note}</i></div>
              ))}
            </div>
          </div>
        </div>
        <div className="hero-stats">
          {t.kpis.map((k) => (
            <div key={k.key} className="hstat">
              <span>{k.label}</span>
              <b>{money(k.value)}</b>
              <em className={k.changePct === null ? 'muted' : k.changePct >= 0 ? 'good-t' : 'bad-t'}>
                {k.changePct !== null ? `${pct(k.changePct)} ` : ''}{k.note}
              </em>
            </div>
          ))}
        </div>
      </section>

      <div className="grid-2">
        <section className="card pad">
          <div className="card-t">Bản tin hôm nay</div>
          <ul className="brief-list">{t.briefing.map((b, i) => <li key={i}>{b}</li>)}</ul>
        </section>
        <section className="card pad">
          <div className="card-t">Việc nên làm</div>
          {t.actions.length === 0 && <div className="muted">Không có việc gấp. 🎉</div>}
          <div className="actions-list">
            {t.actions.map((a, i) => (
              <button key={i} className={`act ${a.tone}`} onClick={() => go(a.tab)}>
                <b>{a.title}</b><span className="muted small">{a.detail}</span>
                <Icon name="arrowR" size={16} />
              </button>
            ))}
          </div>
        </section>
      </div>

      <section className="card">
        <div className="card-t pad-t">Lịch hẹn hôm nay</div>
        <Table rows={t.appointments} max={12} empty="Hôm nay chưa có lịch hẹn" cols={[
          { key: 'time', label: 'Giờ' }, { key: 'customerName', label: 'Khách' }, { key: 'phone', label: 'SĐT' },
          { key: 'service', label: 'Dịch vụ' }, { key: 'performedBy', label: 'Người làm' },
          { key: 'status', label: 'Trạng thái', render: (r) => { const s = APPT[r.status] ?? [r.status, 'brand']; return <span className={`pill ${s[1]}`}>{s[0]}</span>; } },
        ]} />
      </section>

      <div className="grid-2">
        <section className="card">
          <div className="card-t pad-t">Sinh nhật 7 ngày tới</div>
          <Table rows={t.birthdays} empty="Không có khách sinh nhật" cols={[
            { key: 'name', label: 'Khách' }, { key: 'phone', label: 'SĐT' },
            { key: 'date', label: 'Ngày', render: (r) => (r.inDays === 0 ? <b className="good-t">Hôm nay</b> : dm(r.date)) }]} />
        </section>
        <section className="card">
          <div className="card-t pad-t">Thẻ liệu trình hết hạn trong 30 ngày</div>
          <Table rows={t.expiringCards} max={8} empty="Không có thẻ sắp hết hạn" cols={[
            { key: 'customerName', label: 'Khách' }, { key: 'serviceName', label: 'Dịch vụ' },
            { key: 'remainingSessions', label: 'Còn buổi', type: 'number' }, { key: 'expiryDate', label: 'Hết hạn', type: 'date' }]} />
        </section>
      </div>
    </div>
  );
}
