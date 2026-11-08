import React, { useEffect, useMemo, useState } from 'react';
import { api } from '../lib/api';
import { dmy, dmyHm, money, pctPlain, vnd } from '../lib/format';
import type { Check, CustomerRow, GroupDef, LookupResult, Meta, Results, Review, Rule, RuleCondition, RuleView, ServiceRow } from '../lib/types';
import { ErrorBox, Loading, Page, Stat, Table, Tabs, useLoad } from '../components/kit';
import { Icon, Modal, useToast } from '../components/ui';

type Sub = 'alerts' | 'lookup' | 'rules' | 'groups';

export default function Commission() {
  const [sub, setSub] = useState<Sub>('alerts');
  const meta = useLoad(() => api<Meta>('/commission/meta'));
  return (
    <Page kicker="Module hoa hồng · chỉ chức vụ Tư vấn" title="Hoa hồng tư vấn"
      lead={meta.data && <>Kiểm tra sau khi lên đơn: so số đã ghi trong phần mềm Spa với bảng luật mới. Áp dụng từ <b>{dmy(meta.data.effectiveDate)}</b>; đơn trước ngày này giữ cách tính cũ, không kiểm tra.</>}>
      <Tabs value={sub} onChange={setSub} items={[
        { id: 'alerts', label: 'Cảnh báo lệch' }, { id: 'lookup', label: 'Tra cứu % đúng' },
        { id: 'rules', label: 'Bảng luật' }, { id: 'groups', label: 'Nhóm dịch vụ' }]} />
      {sub === 'alerts' && <Alerts meta={meta.data} />}
      {sub === 'lookup' && <Lookup />}
      {sub === 'rules' && <Rules />}
      {sub === 'groups' && <Groups />}
    </Page>
  );
}

/* ======================================================= CẢNH BÁO LỆCH */

const VERDICT: Record<string, { label: string; tone: string }> = {
  OK: { label: 'Đúng', tone: 'good' }, OVER: { label: 'Ghi dư', tone: 'bad' }, UNDER: { label: 'Ghi thiếu', tone: 'warn' },
};

function Alerts({ meta }: { meta: Meta | null }) {
  const toast = useToast();
  const res = useLoad(() => api<Results>('/commission/results'));
  const [busy, setBusy] = useState(false);
  const [filter, setFilter] = useState<'todo' | 'wrong' | 'all'>('todo');
  const [staff, setStaff] = useState('');
  const [open, setOpen] = useState<{ check: Check; review: Review | null; groupLabel: string } | null>(null);

  const sync = async () => {
    setBusy(true);
    try {
      const r = await api<{ total: number; mismatch: number }>('/commission/sync', { body: {} });
      toast(`Đã kiểm tra ${r.total} dòng, ${r.mismatch} dòng lệch`);
      res.reload();
    } catch (e: any) {
      toast(e.message, 'info');
    } finally {
      setBusy(false);
    }
  };

  const rows = res.data?.rows ?? [];
  const staffs = useMemo(() => Array.from(new Set(rows.map((r) => r.check.staffName ?? ''))).filter(Boolean).sort(), [rows]);
  const shown = rows.filter((r) => {
    if (staff && r.check.staffName !== staff) return false;
    if (filter === 'todo') return r.check.verdict !== 'OK' && !r.review;
    if (filter === 'wrong') return r.check.verdict !== 'OK';
    return true;
  });
  const wrong = rows.filter((r) => r.check.verdict !== 'OK');
  const todo = wrong.filter((r) => !r.review);
  const over = wrong.filter((r) => r.check.verdict === 'OVER').reduce((a, r) => a + r.check.diffAmount, 0);
  const under = wrong.filter((r) => r.check.verdict === 'UNDER').reduce((a, r) => a - r.check.diffAmount, 0);

  return (
    <>
      <div className="card pad sync-box">
        <div>
          <b>Lấy dữ liệu từ phần mềm Spa & kiểm tra</b>
          <div className="muted small">
            {res.data?.run ? <>Lần gần nhất: {dmyHm(res.data.run.finishedAt)} · đơn từ {dmy(res.data.run.fromDate)} đến {dmy(res.data.run.toDate)}</> : 'Chưa kiểm tra lần nào.'}
            {meta && <> · Lệch dưới {vnd(meta.toleranceVnd)} coi như đúng.</>}
          </div>
        </div>
        <button className="btn" onClick={sync} disabled={busy}><Icon name="shield" size={16} /> {busy ? 'Đang kiểm tra…' : 'Kiểm tra ngay'}</button>
      </div>

      {res.loading && <Loading />}
      {res.error && <ErrorBox error={res.error} retry={res.reload} />}
      {res.data && res.data.run && (
        <>
          <div className="stats">
            <Stat label="Dòng Tư vấn đã kiểm tra" value={rows.length} />
            <Stat label="Dòng lệch luật" value={wrong.length} tone={wrong.length ? 'bad' : 'good'} sub={`${todo.length} dòng chưa xác nhận`} />
            <Stat label="Tổng ghi dư" value={money(over)} tone={over ? 'bad' : undefined} />
            <Stat label="Tổng ghi thiếu" value={money(under)} tone={under ? 'warn' : undefined} />
          </div>
          <div className="toolbar">
            <Tabs value={filter} onChange={setFilter} items={[
              { id: 'todo', label: 'Cần xác nhận', badge: todo.length }, { id: 'wrong', label: 'Tất cả dòng lệch' }, { id: 'all', label: 'Tất cả' }]} />
            <select value={staff} onChange={(e) => setStaff(e.target.value)}>
              <option value="">Mọi tư vấn viên</option>
              {staffs.map((s) => <option key={s}>{s}</option>)}
            </select>
          </div>
          <div className="card">
            <Table max={100} rows={shown} onRow={setOpen}
              rowClass={(r) => (r.check.verdict !== 'OK' && !r.review ? 'hl-' + r.check.verdict : r.review ? 'reviewed' : '')}
              empty={filter === 'todo' ? 'Không còn dòng lệch nào cần xác nhận 🎉' : 'Không có dòng nào'}
              cols={[
                { key: 'd', label: 'Ngày', render: (r) => dmy(r.check.orderDate) },
                { key: 'o', label: 'Đơn', render: (r) => <b>{r.check.orderCode}</b> },
                { key: 'c', label: 'Khách', render: (r) => r.check.customerName ?? 'Vãng lai' },
                { key: 's', label: 'Dịch vụ', render: (r) => <>{r.check.serviceName}<div className="muted small">{r.groupLabel} · {r.check.isPackage ? `gói ${r.check.sessions}+${r.check.bonus}` : 'lẻ'} · lần {r.check.visitNo} · SL {r.check.quantity}</div></> },
                { key: 'n', label: 'Tư vấn', render: (r) => r.check.staffName },
                { key: 'e', label: 'Đã ghi', align: 'right', render: (r) => <>{money(r.check.enteredAmount)}<div className="muted small">{pctPlain(r.check.enteredPct)}</div></> },
                { key: 'x', label: 'Đúng', align: 'right', render: (r) => <>{money(r.check.expectedAmount)}<div className="muted small">{r.check.expectedPct !== null ? pctPlain(r.check.expectedPct) : 'tiền cố định'}</div></> },
                { key: 'v', label: 'Kết luận', render: (r) => <>
                  <span className={`pill ${VERDICT[r.check.verdict].tone}`}>{VERDICT[r.check.verdict].label}{r.check.verdict !== 'OK' && ` ${money(r.check.diffAmount, { sign: true })}`}</span>
                  {r.review && <div className="muted small">{r.review.status === 'FIX_NEEDED' ? 'Đã xác nhận: cần điều chỉnh' : 'Đã xác nhận: chấp nhận'}</div>}
                </> },
              ]} />
          </div>
        </>
      )}
      {res.data && !res.data.run && !res.loading && (
        <div className="callout brand"><Icon name="spark" /><span>Bấm <b>Kiểm tra ngay</b> để đọc các đơn từ phần mềm Spa (chỉ đọc) và chấm hoa hồng theo bảng luật.</span></div>
      )}
      {open && <ReviewModal row={open} onClose={() => setOpen(null)} onSaved={() => { setOpen(null); res.reload(); }} />}
    </>
  );
}

function ReviewModal({ row, onClose, onSaved }: { row: { check: Check; review: Review | null; groupLabel: string }; onClose: () => void; onSaved: () => void }) {
  const toast = useToast();
  const c = row.check;
  const [note, setNote] = useState(row.review?.note ?? '');
  const [busy, setBusy] = useState(false);
  const save = async (status: 'FIX_NEEDED' | 'ACCEPTED' | null) => {
    setBusy(true);
    try {
      await api('/commission/review', { body: { orderItemStaffId: c.orderItemStaffId, status, note } });
      toast(status ? 'Đã lưu xác nhận' : 'Đã bỏ xác nhận');
      onSaved();
    } catch (e: any) { toast(e.message, 'info'); } finally { setBusy(false); }
  };
  return (
    <Modal open onClose={onClose} title={`Đơn ${c.orderCode} · ${c.serviceName ?? ''}`}>
      <div className="kv">
        <span>Ngày đơn</span><b>{dmyHm(c.orderDate)}</b>
        <span>Khách</span><b>{c.customerName ?? 'Vãng lai'}</b>
        <span>Tư vấn</span><b>{c.staffName}</b>
        <span>Nhóm dịch vụ</span><b>{row.groupLabel}</b>
        <span>Lẻ / gói</span><b>{c.isPackage ? `Gói ${c.sessions}+${c.bonus}` : 'Lẻ'}</b>
        <span>Khách làm nhóm này</span><b>lần {c.visitNo}</b>
        <span>Số lượng</span><b>{c.quantity}</b>
        <span>Căn cứ tính %</span><b>{vnd(c.baseAmount)}</b>
        <span>Đã ghi</span><b>{vnd(c.enteredAmount)} ({pctPlain(c.enteredPct)})</b>
        <span>Đúng theo luật</span><b>{vnd(c.expectedAmount)} ({c.expectedPct !== null ? pctPlain(c.expectedPct) : 'tiền cố định'})</b>
        <span>Chênh lệch</span><b className={c.verdict === 'OVER' ? 'bad-t' : c.verdict === 'UNDER' ? 'warn-t' : 'good-t'}>{vnd(c.diffAmount)}</b>
      </div>
      <div className="callout brand small" style={{ marginTop: 12 }}><Icon name="spark" /> {c.reason}</div>
      {c.verdict !== 'OK' && (
        <>
          <label className="fld" style={{ marginTop: 14 }}><span>Ghi chú của chủ (bắt buộc)</span>
            <textarea rows={3} value={note} onChange={(e) => setNote(e.target.value)} placeholder="Ví dụ: đã báo kế toán trừ lại khi chốt lương / chấp nhận vì khách VIP…" />
          </label>
          <div className="row-btns">
            <button className="btn" disabled={busy || !note.trim()} onClick={() => save('FIX_NEEDED')}>Xác nhận sai — cần điều chỉnh</button>
            <button className="btn ghost" disabled={busy || !note.trim()} onClick={() => save('ACCEPTED')}>Chấp nhận số đã ghi</button>
            {row.review && <button className="btn ghost" disabled={busy} onClick={() => save(null)}>Bỏ xác nhận</button>}
          </div>
          <p className="muted small" style={{ marginTop: 8 }}>App AI chỉ đọc phần mềm Spa, không sửa số trên đơn. Việc điều chỉnh hoa hồng làm trên phần mềm Spa hoặc khi chốt lương.</p>
        </>
      )}
    </Modal>
  );
}

/* ======================================================= TRA CỨU % ĐÚNG */

function Lookup() {
  const [q, setQ] = useState('');
  const [customers, setCustomers] = useState<CustomerRow[]>([]);
  const [cust, setCust] = useState<CustomerRow | null>(null);
  const services = useLoad(() => api<{ service: ServiceRow; groupCode: string }[]>('/commission/services'));
  const [serviceId, setServiceId] = useState('');
  const [pkg, setPkg] = useState(false);
  const [sessions, setSessions] = useState(10);
  const [bonus, setBonus] = useState(2);
  const [qty, setQty] = useState(1);
  const [res, setRes] = useState<LookupResult | null>(null);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    if (cust && q === cust.name) return;
    const t = setTimeout(() => {
      if (q.trim().length < 2) { setCustomers([]); return; }
      api<CustomerRow[]>('/spa/customers', { query: { q } }).then(setCustomers).catch(() => {});
    }, 300);
    return () => clearTimeout(t);
  }, [q, cust]);

  useEffect(() => {
    if (!serviceId) { setRes(null); return; }
    setErr(null);
    api<LookupResult>('/commission/lookup', { body: { customerId: cust?.id ?? null, serviceId, isPackage: pkg, sessions, bonus, quantity: qty } })
      .then(setRes).catch((e) => setErr(e.message));
  }, [cust, serviceId, pkg, sessions, bonus, qty]);

  return (
    <div className="grid-2">
      <div className="card pad form">
        <div className="card-t">Nhập thông tin như lúc lên đơn</div>
        <label className="fld"><span>Khách hàng (tên, SĐT hoặc mã)</span>
          <input value={q} onChange={(e) => { setQ(e.target.value); setCust(null); }} placeholder="Gõ ít nhất 2 ký tự…" />
        </label>
        {!cust && customers.length > 0 && (
          <div className="suggest-list">
            {customers.map((c) => <button key={c.id} onClick={() => { setCust(c); setQ(c.name); setCustomers([]); }}>{c.name} <span className="muted small">{c.phone} · {c.code}</span></button>)}
          </div>
        )}
        {cust && <div className="muted small">Đã chọn: <b>{cust.name}</b> · {cust.phone}</div>}
        {!cust && <div className="muted small">Không chọn khách = khách mới (tính lần đầu).</div>}
        <label className="fld"><span>Dịch vụ</span>
          <select value={serviceId} onChange={(e) => setServiceId(e.target.value)}>
            <option value="">— Chọn dịch vụ —</option>
            {services.data?.map((s) => <option key={s.service.id} value={s.service.id}>{s.service.name} · {money(s.service.price)}</option>)}
          </select>
        </label>
        <div className="seg">
          <button className={!pkg ? 'on' : ''} onClick={() => setPkg(false)}>Lẻ</button>
          <button className={pkg ? 'on' : ''} onClick={() => setPkg(true)}>Bán gói</button>
        </div>
        {pkg && (
          <div className="row2">
            <label className="fld"><span>Số buổi chính</span><input type="number" min={1} value={sessions} onChange={(e) => setSessions(+e.target.value)} /></label>
            <label className="fld"><span>Buổi tặng</span><input type="number" min={0} value={bonus} onChange={(e) => setBonus(+e.target.value)} /></label>
          </div>
        )}
        <label className="fld"><span>Số lượng {res?.unit ? `(${res.unit})` : ''}</span><input type="number" min={1} value={qty} onChange={(e) => setQty(Math.max(1, +e.target.value))} /></label>
      </div>

      <div className="card pad">
        {!serviceId && <div className="muted">Chọn dịch vụ để xem % đúng.</div>}
        {err && <ErrorBox error={err} />}
        {res && (
          <>
            <div className="kicker">{res.groupLabel} · khách làm lần {res.visit}</div>
            <div className="big-pct">{res.evaluation.percent !== null ? pctPlain(res.evaluation.percent) : 'Tiền cố định'}</div>
            <div className="lead">Hoa hồng tư vấn đúng: <b>{vnd(res.evaluation.expectedAmount)}</b></div>
            <div className="muted small">Căn cứ: giá gốc {vnd(res.listPrice)}{res.evaluation.source === 'RULE' || res.evaluation.source === 'OLD_PERCENT' ? ` → ${vnd(res.base)}` : ''}</div>
            <div className={`callout ${res.evaluation.source === 'RULE' ? 'brand' : 'bad'} small`} style={{ marginTop: 12 }}>
              {res.evaluation.source === 'RULE' ? <>Khớp tiêu chí <b>{res.evaluation.ruleLabel}</b>.</> : <>Không khớp tiêu chí nào trong bảng mới → giữ cấu hình cũ của dịch vụ.</>}
              <div className="muted">{res.evaluation.reason}</div>
            </div>
            {res.groupCode === 'KHAC' && <div className="callout bad small" style={{ marginTop: 8 }}>Dịch vụ chưa được gán nhóm. Vào tab <b>Nhóm dịch vụ</b> để gán.</div>}
            {res.alsoMatched.length > 0 && <div className="muted small" style={{ marginTop: 8 }}>Cũng khớp (nhưng xếp sau): {res.alsoMatched.join(', ')}</div>}
            {res.beforeEffectiveDate && <div className="muted small" style={{ marginTop: 8 }}>Lưu ý: luật mới áp dụng từ {dmy(res.effectiveDate)}.</div>}
            {res.history.length > 0 && (
              <>
                <div className="card-t" style={{ marginTop: 18 }}>Lịch sử nhóm {res.groupLabel} của khách</div>
                <Table rows={res.history} max={8} cols={[
                  { key: 'orderDate', label: 'Ngày', type: 'date' }, { key: 'orderCode', label: 'Đơn' },
                  { key: 'itemName', label: 'Dịch vụ' }, { key: 'quantity', label: 'SL', type: 'number' }]} />
              </>
            )}
          </>
        )}
      </div>
    </div>
  );
}

/* ======================================================= BẢNG LUẬT */

const KIND_LABEL: Record<string, string> = { ANY: 'Lẻ hoặc gói', SINGLE: 'Lẻ', PACKAGE: 'Gói' };
const VISIT_LABEL: Record<string, string> = { ANY: 'Mọi lần', FIRST: 'Lần đầu', REPEAT: 'Từ lần 2' };

function Rules() {
  const rules = useLoad(() => api<RuleView[]>('/commission/rules'));
  const groups = useLoad(() => api<GroupDef[]>('/commission/groups'));
  const [edit, setEdit] = useState<{ rule: Rule; isNew: boolean } | null>(null);
  const [hist, setHist] = useState(false);

  if (rules.loading || groups.loading) return <Loading />;
  if (rules.error) return <ErrorBox error={rules.error} retry={rules.reload} />;
  const list = rules.data ?? [];
  const blank: Rule = { id: '', label: '', percent: 1, active: true, priority: (list.at(-1)?.rule.priority ?? 0) + 10, conditions: [{ groups: [], kind: 'ANY', sessions: null, bonus: null, visit: 'ANY', qtyMin: null, qtyMax: null }] };

  return (
    <>
      <div className="toolbar">
        <div className="muted small">Tiêu chí đứng trên được ưu tiên khi một dòng khớp nhiều tiêu chí. Không khớp tiêu chí nào → giữ % cấu hình cũ của dịch vụ.</div>
        <div className="row-btns">
          <button className="btn sm ghost" onClick={() => setHist(true)}>Lịch sử thay đổi</button>
          <button className="btn sm" onClick={() => setEdit({ rule: blank, isNew: true })}>+ Thêm tiêu chí</button>
        </div>
      </div>
      <div className="card">
        <Table rows={list} onRow={(r) => setEdit({ rule: r.rule, isNew: false })} rowClass={(r) => (r.rule.active ? '' : 'off')} cols={[
          { key: 'p', label: 'Thứ tự', render: (r) => r.rule.priority },
          { key: 'pct', label: '%', render: (r) => <b className="big-cell">{pctPlain(r.rule.percent)}</b> },
          { key: 'l', label: 'Tiêu chí', render: (r) => <><b>{r.rule.label}</b><div className="muted small">{r.conditionText.join(' · hoặc · ')}</div></> },
          { key: 'a', label: 'Trạng thái', render: (r) => <span className={`pill ${r.rule.active ? 'good' : 'warn'}`}>{r.rule.active ? 'Đang dùng' : 'Đã tắt'}</span> },
        ]} />
      </div>
      {edit && groups.data && <RuleEditor rule={edit.rule} isNew={edit.isNew} groups={groups.data} allRules={list.map((x) => x.rule)}
        onClose={() => setEdit(null)} onSaved={() => { setEdit(null); rules.reload(); }} />}
      {hist && <HistoryModal onClose={() => setHist(false)} />}
    </>
  );
}

const numOrNull = (v: string) => (v === '' ? null : Number(v));

function RuleEditor({ rule, isNew, groups, allRules, onClose, onSaved }: { rule: Rule; isNew: boolean; groups: GroupDef[]; allRules: Rule[]; onClose: () => void; onSaved: () => void }) {
  const toast = useToast();
  const [r, setR] = useState<Rule>(JSON.parse(JSON.stringify(rule)));
  const [note, setNote] = useState('');
  const [dry, setDry] = useState<any>(null);
  const [busy, setBusy] = useState(false);
  const setCond = (i: number, patch: Partial<RuleCondition>) => setR({ ...r, conditions: r.conditions.map((c, j) => (j === i ? { ...c, ...patch } : c)) });
  const proposed = () => (isNew ? [...allRules, r] : allRules.map((x) => (x.id === r.id ? r : x)));

  const runDry = async () => {
    setBusy(true);
    try { setDry(await api('/commission/rules/dry-run', { body: { rules: proposed() } })); } catch (e: any) { toast(e.message, 'info'); } finally { setBusy(false); }
  };
  const save = async () => {
    setBusy(true);
    try {
      if (isNew) await api('/commission/rules', { body: { rule: r, note } });
      else await api(`/commission/rules/${r.id}`, { method: 'PUT', body: { rule: r, note } });
      toast('Đã lưu tiêu chí. Bấm "Kiểm tra ngay" để chấm lại theo luật mới.');
      onSaved();
    } catch (e: any) { toast(e.message, 'info'); } finally { setBusy(false); }
  };

  return (
    <Modal open onClose={onClose} title={isNew ? 'Thêm tiêu chí' : `Sửa tiêu chí ${r.id}`}>
      <div className="form">
        <div className="row2">
          <label className="fld"><span>Mã (chữ in hoa, số, _)</span><input value={r.id} disabled={!isNew} onChange={(e) => setR({ ...r, id: e.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, '') })} /></label>
          <label className="fld"><span>% hoa hồng</span><input type="number" step="0.1" value={r.percent} onChange={(e) => setR({ ...r, percent: Number(e.target.value) })} /></label>
        </div>
        <label className="fld"><span>Tên tiêu chí</span><input value={r.label} onChange={(e) => setR({ ...r, label: e.target.value })} /></label>
        <div className="row2">
          <label className="fld"><span>Thứ tự ưu tiên (nhỏ = ưu tiên trước)</span><input type="number" value={r.priority} onChange={(e) => setR({ ...r, priority: Number(e.target.value) })} /></label>
          <label className="fld check"><input type="checkbox" checked={r.active} onChange={(e) => setR({ ...r, active: e.target.checked })} /><span>Đang dùng</span></label>
        </div>
        <div className="card-t">Điều kiện (khớp một trong các điều kiện là khớp tiêu chí)</div>
        {r.conditions.map((c, i) => (
          <div key={i} className="cond">
            <div className="chips">
              {groups.filter((g) => g.code !== 'KHAC').map((g) => (
                <button key={g.code} className={`chip ${c.groups.includes(g.code) ? 'on' : ''}`}
                  onClick={() => setCond(i, { groups: c.groups.includes(g.code) ? c.groups.filter((x) => x !== g.code) : [...c.groups, g.code] })}>{g.label}</button>
              ))}
            </div>
            <div className="row4">
              <label className="fld"><span>Lẻ / gói</span><select value={c.kind} onChange={(e) => setCond(i, { kind: e.target.value as any })}>{Object.entries(KIND_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></label>
              <label className="fld"><span>Lần</span><select value={c.visit} onChange={(e) => setCond(i, { visit: e.target.value as any })}>{Object.entries(VISIT_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></label>
              <label className="fld"><span>Gói: số buổi</span><input type="number" value={c.sessions ?? ''} disabled={c.kind !== 'PACKAGE'} onChange={(e) => setCond(i, { sessions: numOrNull(e.target.value) })} /></label>
              <label className="fld"><span>Gói: buổi tặng</span><input type="number" value={c.bonus ?? ''} disabled={c.kind !== 'PACKAGE'} onChange={(e) => setCond(i, { bonus: numOrNull(e.target.value) })} /></label>
              <label className="fld"><span>SL tối thiểu</span><input type="number" value={c.qtyMin ?? ''} onChange={(e) => setCond(i, { qtyMin: numOrNull(e.target.value) })} /></label>
              <label className="fld"><span>SL tối đa</span><input type="number" value={c.qtyMax ?? ''} onChange={(e) => setCond(i, { qtyMax: numOrNull(e.target.value) })} /></label>
            </div>
            {r.conditions.length > 1 && <button className="btn sm ghost" onClick={() => setR({ ...r, conditions: r.conditions.filter((_, j) => j !== i) })}>Xóa điều kiện</button>}
          </div>
        ))}
        <button className="btn sm ghost" onClick={() => setR({ ...r, conditions: [...r.conditions, { groups: [], kind: 'ANY', sessions: null, bonus: null, visit: 'ANY', qtyMin: null, qtyMax: null }] })}>+ Thêm điều kiện</button>
        <p className="muted small">Để trống ô số = không xét. Không chọn nhóm nào = mọi nhóm. Gói 10+2: chọn "Gói", số buổi 10, buổi tặng 2.</p>

        <div className="row-btns">
          <button className="btn ghost" disabled={busy} onClick={runDry}>Chạy thử trên dữ liệu 1 tháng gần nhất</button>
        </div>
        {dry && (
          <div className="callout brand small">
            {dry.changedLines} / {dry.totalLines} dòng sẽ đổi kết quả. Tổng hoa hồng đúng: {money(dry.expectedBefore)} → <b>{money(dry.expectedAfter)}</b>.
            {dry.samples.length > 0 && <ul className="dry">{dry.samples.slice(0, 8).map((s: any) => <li key={s.before.orderItemStaffId}>{s.before.orderCode} · {s.before.serviceName}: {money(s.before.expectedAmount)} → {money(s.after.expectedAmount)}</li>)}</ul>}
          </div>
        )}
        <label className="fld"><span>Lý do thay đổi (lưu vào lịch sử, bắt buộc)</span><input value={note} onChange={(e) => setNote(e.target.value)} /></label>
        <div className="row-btns">
          <button className="btn" disabled={busy || !note.trim() || !r.id || !r.label} onClick={save}>Lưu</button>
          <button className="btn ghost" onClick={onClose}>Hủy</button>
        </div>
      </div>
    </Modal>
  );
}

function HistoryModal({ onClose }: { onClose: () => void }) {
  const h = useLoad(() => api<any[]>('/commission/rules/history'));
  const TYPE: Record<string, string> = { CREATE: 'Tạo', UPDATE: 'Sửa', DISABLE: 'Tắt', ENABLE: 'Bật' };
  const desc = (j: string | null) => { if (!j) return '—'; try { const r = JSON.parse(j); return `${r.percent}% · ${r.label}${r.active ? '' : ' (tắt)'}`; } catch { return j; } };
  return (
    <Modal open onClose={onClose} title="Lịch sử thay đổi bảng luật">
      {h.loading && <Loading />}
      {h.data && <Table rows={h.data} max={50} cols={[
        { key: 'changedAt', label: 'Lúc', render: (r) => dmyHm(r.changedAt) },
        { key: 'ruleId', label: 'Tiêu chí' },
        { key: 'changeType', label: 'Việc', render: (r) => TYPE[r.changeType] ?? r.changeType },
        { key: 'x', label: 'Trước → Sau', render: (r) => <span className="small">{desc(r.beforeJson)} → <b>{desc(r.afterJson)}</b></span> },
        { key: 'note', label: 'Lý do' }, { key: 'changedBy', label: 'Người sửa' }]} />}
    </Modal>
  );
}

/* ======================================================= NHÓM DỊCH VỤ */

function Groups() {
  const toast = useToast();
  const groups = useLoad(() => api<GroupDef[]>('/commission/groups'));
  const svcs = useLoad(() => api<{ service: ServiceRow; groupCode: string }[]>('/commission/services'));
  const [q, setQ] = useState('');
  const [onlyOther, setOnlyOther] = useState(false);
  const assign = async (serviceId: string, groupCode: string) => {
    try {
      await api(`/commission/services/${serviceId}/group`, { method: 'PUT', body: { groupCode } });
      svcs.setData((svcs.data ?? []).map((s) => (s.service.id === serviceId ? { ...s, groupCode } : s)));
      toast('Đã gán nhóm');
    } catch (e: any) { toast(e.message, 'info'); }
  };
  if (svcs.loading || groups.loading) return <Loading />;
  if (svcs.error) return <ErrorBox error={svcs.error} retry={svcs.reload} />;
  const list = (svcs.data ?? []).filter((s) => (!onlyOther || s.groupCode === 'KHAC') && (s.service.name + ' ' + s.service.code).toLowerCase().includes(q.toLowerCase()));
  const other = (svcs.data ?? []).filter((s) => s.groupCode === 'KHAC').length;
  return (
    <>
      <div className="callout brand small"><Icon name="spark" /> "Lần đầu" được đếm theo nhóm dịch vụ (D.1). Dịch vụ chưa gán nhóm thuộc "Khác" và luôn giữ % cấu hình cũ. Hiện có <b>{other}</b> dịch vụ chưa gán.</div>
      <div className="toolbar">
        <input placeholder="Tìm dịch vụ…" value={q} onChange={(e) => setQ(e.target.value)} />
        <label className="fld check"><input type="checkbox" checked={onlyOther} onChange={(e) => setOnlyOther(e.target.checked)} /><span>Chỉ dịch vụ chưa gán</span></label>
      </div>
      <div className="card">
        <Table rows={list} max={200} rowClass={(s) => (s.groupCode === 'KHAC' ? 'hl-UNDER' : '')} cols={[
          { key: 'c', label: 'Mã', render: (s) => s.service.code },
          { key: 'n', label: 'Dịch vụ', render: (s) => <>{s.service.name}<div className="muted small">{s.service.categoryName}</div></> },
          { key: 'p', label: 'Giá gốc', align: 'right', render: (s) => money(s.service.price) },
          { key: 'g', label: 'Nhóm', render: (s) => (
            <select value={s.groupCode} onChange={(e) => assign(s.service.id, e.target.value)}>
              {groups.data!.map((g) => <option key={g.code} value={g.code}>{g.label}</option>)}
            </select>) },
        ]} />
      </div>
    </>
  );
}
