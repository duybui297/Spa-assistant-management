// Kiểu dữ liệu trả về từ API (khớp record Java).
export type Verdict = 'OK' | 'OVER' | 'UNDER';

export interface Check {
  runId: string; orderItemStaffId: string; orderId: string; orderCode: string; orderDate: string; branchId: string | null;
  orderItemId: string; itemType: string; serviceId: string | null; serviceName: string | null;
  customerId: string | null; customerName: string | null; staffId: string | null; staffName: string | null;
  groupCode: string; isPackage: boolean; sessions: number | null; bonus: number | null; visitNo: number; quantity: number;
  listPrice: number | null; unitPrice: number | null; baseAmount: number; enteredAmount: number; enteredPct: number | null;
  expectedPct: number | null; expectedAmount: number; diffAmount: number; verdict: Verdict; source: string;
  ruleId: string | null; reason: string;
}
export interface Review { orderItemStaffId: string; status: 'FIX_NEEDED' | 'ACCEPTED'; note: string; reviewedBy: string; reviewedAt: string }
export interface Run { id: string; startedAt: string; finishedAt: string | null; fromDate: string; toDate: string; totalLines: number; mismatchLines: number; status: string; errorMessage: string | null; triggeredBy: string }
export interface Results { run: Run | null; rows: { check: Check; review: Review | null; groupLabel: string }[]; effectiveDate: string }

export type Kind = 'ANY' | 'SINGLE' | 'PACKAGE';
export type VisitCond = 'ANY' | 'FIRST' | 'REPEAT';
export interface RuleCondition { groups: string[]; kind: Kind; sessions: number | null; bonus: number | null; visit: VisitCond; qtyMin: number | null; qtyMax: number | null }
export interface Rule { id: string; label: string; percent: number; active: boolean; priority: number; conditions: RuleCondition[] }
export interface RuleView { rule: Rule; conditionText: string[] }
export interface GroupDef { code: string; label: string; unit: string | null; sortOrder: number; active: boolean }
export interface ServiceRow { id: string; code: string; name: string; categoryName: string | null; price: number; totalSessions: number | null; blocked: boolean }
export interface CustomerRow { id: string; code: string; name: string; phone: string; dateOfBirth: string | null; consultantPerson: string | null }
export interface StaffRow { id: string; code: string; name: string; jobPosition: string; active: boolean }

export interface Evaluation { source: string; ruleId: string | null; ruleLabel: string | null; percent: number | null; base: number; expectedAmount: number; reason: string }
export interface LookupResult {
  groupCode: string; groupLabel: string; unit: string; visit: number; listPrice: number; base: number; evaluation: Evaluation;
  alsoMatched: string[]; history: { orderCode: string; orderDate: string; itemName: string; itemType: string; quantity: number; sessions: number | null; bonus: number | null }[];
  beforeEffectiveDate: boolean; effectiveDate: string;
}
export interface Meta { effectiveDate: string; toleranceVnd: number; packageBase: string; consultantRoles: string[]; today: string; mock: boolean }
