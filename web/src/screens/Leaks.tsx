import React, { useState } from 'react';
import { api } from '../lib/api';
import { dmy, iso, money } from '../lib/format';
import { ErrorBox, Loading, Page, Table, useLoad, type Col } from '../components/kit';
import type { Tab } from '../App';

interface Leak { kind: string; title: string; detail: string; amount: number; severity: 'ok' | 'warn' | 'bad'; rows: Record<string, any>[] }
interface Leaks { from: string; to: string; items: Leak[]; total: number }

const COLS: Record<string, Col<any>[]> = {
  discount: [{ key: 'date', label: 'Ngày', type: 'date' }, { key: 'orderCode', label: 'Đơn' }, { key: 'customer', label: 'Khách' }, { key: 'item', label: 'Dòng' }, { key: 'percent', label: '% giảm', type: 'percent' }, { key: 'amount', label: 'Tiền giảm', type: 'money' }],
  commission: [{ key: 'date', label: 'Ngày', type: 'date' }, { key: 'orderCode', label: 'Đơn' }, { key: 'staff', label: 'Tư vấn' }, { key: 'item', label: 'Dịch vụ' }, { key: 'amount', label: 'Ghi dư', type: 'money' }, { key: 'reviewed', label: 'Đã xác nhận', render: (r) => (r.reviewed ? 'Rồi' : 'Chưa') }],
  refund: [{ key: 'date', label: 'Ngày', type: 'date' }, { key: 'orderCode', label: 'Đơn' }, { key: 'customer', label: 'Khách' }, { key: 'note', label: 'Lý do' }, { key: 'amount', label: 'Hoàn', type: 'money' }],
  stock: [{ key: 'product', label: 'Sản phẩm' }, { key: 'expiry', label: 'Hạn dùng', type: 'date' }, { key: 'days', label: 'Còn (ngày)', render: (r) => (r.days < 0 ? <b className="bad-t">Hết hạn {-r.days} ngày</b> : r.days) }, { key: 'quantity', label: 'SL nhập', type: 'number' }, { key: 'amount', label: 'Giá trị nhập', type: 'money' }],
  cards: [{ key: 'customer', label: 'Khách' }, { key: 'phone', label: 'SĐT' }, { key: 'service', label: 'Dịch vụ' }, { key: 'remaining', label: 'Còn buổi', type: 'number' }, { key: 'expiry', label: 'Hết hạn', type: 'date' }, { key: 'amount', label: 'Giá trị còn lại', type: 'money' }],
  baddebt: [{ key: 'customer', label: 'Khách' }, { key: 'orderCode', label: 'Đơn' }, { key: 'date', label: 'Ngày', type: 'date' }, { key: 'days', label: 'Tuổi nợ', type: 'number' }, { key: 'amount', label: 'Còn nợ', type: 'money' }],
};

export default function LeaksScreen({ go }: { go: (t: Tab) => void }) {
  const [days, setDays] = useState(30);
  const d = useLoad(() => {
    const to = new Date();
    const from = new Date(); from.setDate(from.getDate() - days);
    return api<Leaks>('/assistant/leaks', { query: { from: iso(from), to: iso(to) } });
  }, [days]);
  const [open, setOpen] = useState<string | null>(null);

  return (
    <Page kicker="Phát hiện bằng quy tắc" title="Rò rỉ tiền"
      lead="Các khoản có dấu hiệu thất thoát hoặc rủi ro, tính bằng quy tắc cố định trên dữ liệu phần mềm Spa."
      right={<div className="seg">{[7, 30, 90].map((n) => <button key={n} className={days === n ? 'on' : ''} onClick={() => setDays(n)}>{n} ngày</button>)}</div>}>
      {d.loading && <Loading />}
      {d.error && <ErrorBox error={d.error} retry={d.reload} />}
      {d.data && (
        <>
          <div className="card pad leak-total">
            <span className="muted">Tổng giá trị cần xem lại ({dmy(d.data.from)} – {dmy(d.data.to)})</span>
            <b>{money(d.data.total)}</b>
          </div>
          {d.data.items.map((l) => (
            <section key={l.kind} className={`card leak-card ${l.severity}`}>
              <button className="leak-head" onClick={() => setOpen(open === l.kind ? null : l.kind)}>
                <span className={`dot-s ${l.severity}`} />
                <div><b>{l.title}</b><div className="muted small">{l.detail}</div></div>
                <b className="leak-amt">{money(l.amount)}</b>
              </button>
              {open === l.kind && (
                <>
                  <Table rows={l.rows} cols={COLS[l.kind] ?? []} max={30} empty="Không có" />
                  {l.kind === 'commission' && <div className="pad-t"><button className="btn sm ghost" onClick={() => go('commission')}>Mở màn Hoa hồng</button></div>}
                </>
              )}
            </section>
          ))}
        </>
      )}
    </Page>
  );
}
