import React, { useEffect, useMemo, useState } from 'react';
import { api } from '../lib/api';
import { iso } from '../lib/format';
import type { CustomerRow, ServiceRow, StaffRow } from '../lib/types';
import { ErrorBox, Loading, Page, Table, Tabs, useLoad } from '../components/kit';
import { Icon, useToast } from '../components/ui';

interface Question { id: string; group: string; label: string; entity: 'none' | 'customer' | 'staff' | 'service'; needsPeriod: boolean }
interface Answer { questionId: string; title: string; sentences: string[]; columns: { key: string; label: string; type: any }[]; rows: Record<string, any>[] }
interface Summary { month: string; paragraphs: string[]; advice: string[] }

const PERIODS: { id: string; label: string; range: () => [Date, Date] }[] = [
  { id: 'today', label: 'Hôm nay', range: () => [new Date(), new Date()] },
  { id: '7d', label: '7 ngày qua', range: () => { const f = new Date(); f.setDate(f.getDate() - 6); return [f, new Date()]; } },
  { id: 'month', label: 'Tháng này', range: () => { const t = new Date(); return [new Date(t.getFullYear(), t.getMonth(), 1), t]; } },
  { id: 'lastMonth', label: 'Tháng trước', range: () => { const t = new Date(); return [new Date(t.getFullYear(), t.getMonth() - 1, 1), new Date(t.getFullYear(), t.getMonth(), 0)]; } },
  { id: '90d', label: '90 ngày qua', range: () => { const f = new Date(); f.setDate(f.getDate() - 89); return [f, new Date()]; } },
  { id: 'year', label: 'Năm nay', range: () => { const t = new Date(); return [new Date(t.getFullYear(), 0, 1), t]; } },
];

export default function AskScreen() {
  const [sub, setSub] = useState<'ask' | 'summary'>('ask');
  return (
    <Page kicker="Bộ câu hỏi chọn sẵn · không dùng AI ở giai đoạn 1" title="Hỏi trợ lý"
      lead="Chọn muốn xem gì → của ai / cái gì → khoảng thời gian. Câu trả lời được tính từ dữ liệu phần mềm Spa.">
      <Tabs value={sub} onChange={setSub} items={[{ id: 'ask', label: 'Hỏi' }, { id: 'summary', label: 'Tóm tắt cuối tháng' }]} />
      {sub === 'ask' ? <AskFlow /> : <MonthlySummary />}
    </Page>
  );
}

function AskFlow() {
  const cat = useLoad(() => api<Question[]>('/ask/catalog'));
  const [qid, setQid] = useState('');
  const [entity, setEntity] = useState<{ id: string; label: string } | null>(null);
  const [period, setPeriod] = useState('month');
  const [ans, setAns] = useState<Answer | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const q = cat.data?.find((x) => x.id === qid);
  const groups = useMemo(() => Array.from(new Set((cat.data ?? []).map((x) => x.group))), [cat.data]);

  useEffect(() => { setEntity(null); setAns(null); }, [qid]);

  const ready = q && (q.entity === 'none' || entity);
  const run = async () => {
    if (!q) return;
    setBusy(true); setErr(null);
    const [f, t] = (PERIODS.find((p) => p.id === period) ?? PERIODS[2]).range();
    try {
      setAns(await api<Answer>('/ask/run', { body: { questionId: q.id, entityId: entity?.id ?? null, from: q.needsPeriod ? iso(f) : null, to: q.needsPeriod ? iso(t) : null } }));
    } catch (e: any) { setErr(e.message); } finally { setBusy(false); }
  };

  if (cat.loading) return <Loading />;
  if (cat.error) return <ErrorBox error={cat.error} retry={cat.reload} />;
  return (
    <>
      <section className="card pad ask-steps">
        <div className="step"><i>1</i>
          <label className="fld"><span>Muốn xem gì?</span>
            <select value={qid} onChange={(e) => setQid(e.target.value)}>
              <option value="">— Chọn câu hỏi —</option>
              {groups.map((g) => <optgroup key={g} label={g}>{cat.data!.filter((x) => x.group === g).map((x) => <option key={x.id} value={x.id}>{x.label}</option>)}</optgroup>)}
            </select>
          </label>
        </div>
        {q && q.entity !== 'none' && (
          <div className="step"><i>2</i><EntityPicker kind={q.entity} value={entity} onChange={setEntity} /></div>
        )}
        {q && q.needsPeriod && (
          <div className="step"><i>{q.entity !== 'none' ? 3 : 2}</i>
            <label className="fld"><span>Khoảng thời gian</span>
              <select value={period} onChange={(e) => setPeriod(e.target.value)}>{PERIODS.map((p) => <option key={p.id} value={p.id}>{p.label}</option>)}</select>
            </label>
          </div>
        )}
        <button className="btn" disabled={!ready || busy} onClick={run}><Icon name="spark" size={16} /> {busy ? 'Đang tính…' : 'Xem kết quả'}</button>
      </section>
      {err && <ErrorBox error={err} />}
      {ans && (
        <section className="card pad answer">
          <div className="kicker">{ans.title}</div>
          {ans.sentences.map((s, i) => <p key={i} className="ans-s">{s}</p>)}
          {ans.columns.length > 0 && <div style={{ marginTop: 14 }}><Table rows={ans.rows} cols={ans.columns} max={20} /></div>}
        </section>
      )}
    </>
  );
}

function EntityPicker({ kind, value, onChange }: { kind: 'customer' | 'staff' | 'service'; value: { id: string; label: string } | null; onChange: (v: { id: string; label: string } | null) => void }) {
  const staff = useLoad(() => (kind === 'staff' ? api<StaffRow[]>('/spa/staff') : Promise.resolve([] as StaffRow[])), [kind]);
  const services = useLoad(() => (kind === 'service' ? api<ServiceRow[]>('/ask/services') : Promise.resolve([] as ServiceRow[])), [kind]);
  const [q, setQ] = useState('');
  const [found, setFound] = useState<CustomerRow[]>([]);
  useEffect(() => {
    if (kind !== 'customer' || q.trim().length < 2 || value?.label === q) { setFound([]); return; }
    const t = setTimeout(() => api<CustomerRow[]>('/spa/customers', { query: { q } }).then(setFound).catch(() => {}), 300);
    return () => clearTimeout(t);
  }, [q, kind, value]);

  if (kind === 'staff') {
    return (
      <label className="fld"><span>Nhân viên nào?</span>
        <select value={value?.id ?? ''} onChange={(e) => { const s = staff.data?.find((x) => x.id === e.target.value); onChange(s ? { id: s.id, label: s.name } : null); }}>
          <option value="">— Chọn nhân viên —</option>
          {staff.data?.map((s) => <option key={s.id} value={s.id}>{s.name} · {s.jobPosition}</option>)}
        </select>
      </label>
    );
  }
  if (kind === 'service') {
    return (
      <label className="fld"><span>Dịch vụ nào?</span>
        <select value={value?.id ?? ''} onChange={(e) => { const s = services.data?.find((x) => x.id === e.target.value); onChange(s ? { id: s.id, label: s.name } : null); }}>
          <option value="">— Chọn dịch vụ —</option>
          {services.data?.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
        </select>
      </label>
    );
  }
  return (
    <div className="fld-wrap">
      <label className="fld"><span>Khách nào? (tên, SĐT, mã)</span>
        <input value={q} onChange={(e) => { setQ(e.target.value); onChange(null); }} placeholder="Gõ ít nhất 2 ký tự…" />
      </label>
      {found.length > 0 && (
        <div className="suggest-list">
          {found.map((c) => <button key={c.id} onClick={() => { onChange({ id: c.id, label: c.name }); setQ(c.name); setFound([]); }}>{c.name} <span className="muted small">{c.phone}</span></button>)}
        </div>
      )}
    </div>
  );
}

function MonthlySummary() {
  const toast = useToast();
  const now = new Date();
  const options = Array.from({ length: 6 }, (_, i) => { const d = new Date(now.getFullYear(), now.getMonth() - i, 1); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`; });
  const [month, setMonth] = useState(options[1]);
  const s = useLoad(() => api<Summary>('/summary/monthly', { query: { month } }), [month]);
  const text = s.data ? [...s.data.paragraphs, '', 'Gợi ý:', ...s.data.advice.map((a) => '• ' + a)].join('\n') : '';
  return (
    <>
      <div className="toolbar">
        <select value={month} onChange={(e) => setMonth(e.target.value)}>{options.map((o) => <option key={o} value={o}>Tháng {Number(o.slice(5))}/{o.slice(0, 4)}{o === options[0] ? ' (đang chạy)' : ''}</option>)}</select>
        <button className="btn sm ghost" disabled={!s.data} onClick={() => navigator.clipboard?.writeText(text).then(() => toast('Đã chép tóm tắt'))}><Icon name="copy" size={15} /> Chép</button>
      </div>
      {s.loading && <Loading />}
      {s.error && <ErrorBox error={s.error} retry={s.reload} />}
      {s.data && (
        <section className="card pad answer">
          {s.data.paragraphs.map((p, i) => <p key={i} className="ans-s">{p}</p>)}
          <div className="card-t" style={{ marginTop: 16 }}>Gợi ý</div>
          <ul className="brief-list">{s.data.advice.map((a, i) => <li key={i}>{a}</li>)}</ul>
          <p className="muted small" style={{ marginTop: 12 }}>Tóm tắt dùng mẫu câu cố định, số liệu điền tự động. Mẫu câu có thể chỉnh theo góp ý của khách.</p>
        </section>
      )}
    </>
  );
}
