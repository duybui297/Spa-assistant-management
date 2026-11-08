import React, { useState } from 'react';
import { api } from '../lib/api';
import { dmy, money } from '../lib/format';
import { ErrorBox, Loading, Page, Stat, Table, Tabs, useLoad } from '../components/kit';
import { Icon, Modal, useToast } from '../components/ui';

interface Receivable { orderId: string; orderCode: string; orderDate: string; customerName: string; phone: string; total: number; paid: number; debt: number; ageDays: number; bucket: string; reminder: string }
interface SupplierDebt { id: string; supplierName: string; description: string; amount: number; paidAmount: number; invoiceDate: string | null; dueDate: string | null; critical: boolean }
interface Payable { debt: SupplierDebt; outstanding: number; daysToDue: number; priority: number; advice: string }
interface Debts { receivables: Receivable[]; aging: Record<string, number>; receivableTotal: number; payables: Payable[]; payableTotal: number; notes: string[] }

export default function DebtsScreen() {
  const [sub, setSub] = useState<'in' | 'out'>('in');
  const d = useLoad(() => api<Debts>('/assistant/debts'));
  const [remind, setRemind] = useState<Receivable | null>(null);
  const [edit, setEdit] = useState<SupplierDebt | null>(null);
  const toast = useToast();

  return (
    <Page kicker="Phải thu & phải trả" title="Công nợ" lead="Phải thu đọc từ phần mềm Spa. Phải trả nhà cung cấp là số nhập tay.">
      <Tabs value={sub} onChange={setSub} items={[{ id: 'in', label: 'Khách nợ mình' }, { id: 'out', label: 'Mình nợ nhà cung cấp' }]} />
      {d.loading && <Loading />}
      {d.error && <ErrorBox error={d.error} retry={d.reload} />}
      {d.data && sub === 'in' && (
        <>
          <div className="stats">
            <Stat label="Tổng khách đang nợ" value={money(d.data.receivableTotal)} sub={`${d.data.receivables.length} đơn`} />
            {Object.entries(d.data.aging).map(([k, v]) => (
              <Stat key={k} label={`Tuổi nợ ${k}`} value={money(v)} tone={k.startsWith('0') ? undefined : v > 0 ? (k.startsWith('>') || k.startsWith('61') ? 'bad' : 'warn') : undefined} />
            ))}
          </div>
          <section className="card">
            <Table rows={d.data.receivables} max={50} rowClass={(r) => (r.ageDays > 60 ? 'hl-OVER' : r.ageDays > 30 ? 'hl-UNDER' : '')} cols={[
              { key: 'customerName', label: 'Khách' }, { key: 'phone', label: 'SĐT' }, { key: 'orderCode', label: 'Đơn' },
              { key: 'orderDate', label: 'Ngày đơn', type: 'date' }, { key: 'ageDays', label: 'Tuổi nợ (ngày)', type: 'number' },
              { key: 'debt', label: 'Còn nợ', type: 'money' },
              { key: 'x', label: '', render: (r) => <button className="btn sm ghost" onClick={(e) => { e.stopPropagation(); setRemind(r); }}>Soạn tin nhắc</button> }]} />
          </section>
        </>
      )}
      {d.data && sub === 'out' && (
        <>
          <div className="toolbar">
            <Stat label="Tổng còn phải trả" value={money(d.data.payableTotal)} />
            <button className="btn sm" onClick={() => setEdit({ id: '', supplierName: '', description: '', amount: 0, paidAmount: 0, invoiceDate: null, dueDate: null, critical: false })}>+ Thêm khoản nợ NCC</button>
          </div>
          <section className="card">
            <div className="card-t pad-t">Thứ tự nên trả: khoản bắt buộc → quá hạn → hạn gần</div>
            <Table rows={d.data.payables} onRow={(p) => setEdit(p.debt)} empty="Chưa nhập khoản nợ nhà cung cấp nào" cols={[
              { key: 'r', label: '#', render: (p) => d.data!.payables.indexOf(p) + 1 },
              { key: 'n', label: 'Nhà cung cấp', render: (p) => <><b>{p.debt.supplierName}</b>{p.debt.critical && <span className="pill bad" style={{ marginLeft: 6 }}>Bắt buộc</span>}<div className="muted small">{p.debt.description}</div></> },
              { key: 'due', label: 'Hạn trả', render: (p) => dmy(p.debt.dueDate) },
              { key: 'o', label: 'Còn nợ', align: 'right', render: (p) => money(p.outstanding, { full: true }) },
              { key: 'a', label: 'Gợi ý', render: (p) => <span className={p.daysToDue < 0 ? 'bad-t' : p.daysToDue <= 7 ? 'warn-t' : 'muted'}>{p.advice}</span> }]} />
          </section>
        </>
      )}
      {d.data && <ul className="notes">{d.data.notes.map((n, i) => <li key={i}>{n}</li>)}</ul>}

      {remind && (
        <Modal open onClose={() => setRemind(null)} title={`Nhắc nợ · ${remind.customerName}`}>
          <textarea className="msg-box" rows={6} defaultValue={remind.reminder} id="remind-text" />
          <div className="row-btns">
            <button className="btn" onClick={() => {
              const t = (document.getElementById('remind-text') as HTMLTextAreaElement).value;
              navigator.clipboard?.writeText(t).then(() => toast('Đã chép tin nhắn, dán vào Zalo để gửi')).catch(() => toast('Không chép được, hãy bôi đen và copy', 'info'));
            }}><Icon name="copy" size={16} /> Chép tin nhắn</button>
            {remind.phone && <a className="btn ghost" href={`https://zalo.me/${remind.phone}`} target="_blank" rel="noreferrer">Mở Zalo</a>}
          </div>
          <p className="muted small">App không tự gửi tin. Anh/chị chép và gửi qua Zalo.</p>
        </Modal>
      )}
      {edit && <SupplierModal value={edit} onClose={() => setEdit(null)} onSaved={() => { setEdit(null); d.reload(); }} />}
    </Page>
  );
}

function SupplierModal({ value, onClose, onSaved }: { value: SupplierDebt; onClose: () => void; onSaved: () => void }) {
  const toast = useToast();
  const [v, setV] = useState<SupplierDebt>(value);
  const save = async () => {
    try { await api('/manual/supplier-debts', { body: v }); toast('Đã lưu'); onSaved(); } catch (e: any) { toast(e.message, 'info'); }
  };
  const del = async () => {
    if (!confirmDelete()) return;
    try { await api(`/manual/supplier-debts/${v.id}`, { method: 'DELETE' }); toast('Đã xóa'); onSaved(); } catch (e: any) { toast(e.message, 'info'); }
  };
  return (
    <Modal open onClose={onClose} title={v.id ? 'Sửa khoản nợ NCC' : 'Thêm khoản nợ NCC'}>
      <div className="form">
        <label className="fld"><span>Nhà cung cấp</span><input value={v.supplierName} onChange={(e) => setV({ ...v, supplierName: e.target.value })} /></label>
        <label className="fld"><span>Nội dung</span><input value={v.description ?? ''} onChange={(e) => setV({ ...v, description: e.target.value })} /></label>
        <div className="row2">
          <label className="fld"><span>Tổng tiền (đ)</span><input type="number" value={v.amount || ''} onChange={(e) => setV({ ...v, amount: Number(e.target.value) })} /></label>
          <label className="fld"><span>Đã trả (đ)</span><input type="number" value={v.paidAmount || ''} onChange={(e) => setV({ ...v, paidAmount: Number(e.target.value) })} /></label>
        </div>
        <div className="row2">
          <label className="fld"><span>Ngày hóa đơn</span><input type="date" value={v.invoiceDate ?? ''} onChange={(e) => setV({ ...v, invoiceDate: e.target.value || null })} /></label>
          <label className="fld"><span>Hạn trả</span><input type="date" value={v.dueDate ?? ''} onChange={(e) => setV({ ...v, dueDate: e.target.value || null })} /></label>
        </div>
        <label className="fld check"><input type="checkbox" checked={v.critical} onChange={(e) => setV({ ...v, critical: e.target.checked })} /><span>Khoản bắt buộc trả đúng hạn (thuốc, vật tư chính, trả góp máy…)</span></label>
        <div className="row-btns">
          <button className="btn" onClick={save}>Lưu</button>
          {v.id && <button className="btn ghost" onClick={del}>Xóa</button>}
        </div>
      </div>
    </Modal>
  );
}

// Không dùng window.confirm (một số trình duyệt nhúng chặn hộp thoại) — xác nhận bằng nhấn lần 2.
let armed = 0;
function confirmDelete() {
  const now = Date.now();
  if (now - armed < 3000) { armed = 0; return true; }
  armed = now;
  alertSoft('Bấm Xóa lần nữa trong 3 giây để xác nhận');
  return false;
}
function alertSoft(t: string) {
  const el = document.createElement('div');
  el.className = 'toast info soft';
  el.textContent = t;
  document.body.appendChild(el);
  setTimeout(() => el.remove(), 2500);
}
