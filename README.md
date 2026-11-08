# Spa-assistant-management — AI Assistant cho Yellow Beauty Center

Ứng dụng trợ lý cho chủ spa, lấy prototype assistant làm khung, module **hoa hồng tư vấn** nằm bên trong.
**Chỉ đọc** DB phần mềm Spa, không sửa repo nào của `AI-Core-Labs`. Giai đoạn 1 **không dùng AI/LLM**: mọi số liệu tính bằng công thức và bộ luật cố định.

Bối cảnh và các quyết định: xem `HANDOFF.md` trong thư mục `Spa-enhance-suggestion`.

## Cấu trúc

```
api/   Spring Boot 3.5 (Java 21) — deploy Render Free (Docker)
  commission/engine/   Bộ luật hoa hồng (Java thuần, không phụ thuộc Spring) + test JUnit
  commission/          Đồng bộ & kiểm tra, tra cứu %, bảng luật + lịch sử, nhóm dịch vụ
  spa/                 LỚP ĐỌC DB SPA DUY NHẤT (chỉ SELECT, SQL chung Postgres/MySQL)
  assistant/           Hôm nay, Dòng tiền, Lợi nhuận, Công nợ, Rò rỉ, Hỏi trợ lý, Tóm tắt tháng, nhập tay
  mock/                V1: sinh dữ liệu GIẢ vào schema spa_mock
  resources/db/ai      Migration schema spa_ai (Postgres & MySQL)
  resources/db/mock    Schema spa_mock — tên bảng/cột giống DB Spa (nhánh staging 14/10/2026)
web/   React 19 + Vite — deploy Vercel
```

### Hai kết nối DB tách quyền

| Kết nối | V1 | V2 |
|---|---|---|
| `APP_AI_DB_*` (có ghi) | Supabase, schema `spa_ai` | MySQL container riêng |
| `APP_SPA_DB_*` (chỉ đọc) | Supabase, schema `spa_mock` (dữ liệu giả) | MySQL Spa `clinic_staging`, user chỉ có `SELECT` |

Kết nối Spa luôn mở ở chế độ read-only và có giới hạn thời gian mỗi câu query (`APP_SPA_QUERY_TIMEOUT_SEC`). Chỉ đọc khi bấm nút.

## Bộ test luật hoa hồng

`api/src/test/java/.../RuleEngineTest.java` — 35 tình huống, mỗi test có tên tiếng Việt mô tả quy tắc (2% / 1,5% / 0,5% / giữ cấu hình cũ / lần thứ mấy / căn cứ tính / làm tròn). Đây là căn cứ để duyệt logic với khách.

```bash
cd api && mvn test
```

## Chạy local

Cần Java 21, Maven, Node 20+, và một Postgres (có thể dùng luôn Supabase).

```bash
# API
cd api
export APP_AI_DB_URL="jdbc:postgresql://localhost:5432/postgres?currentSchema=spa_ai"
export APP_SPA_DB_URL="jdbc:postgresql://localhost:5432/postgres?currentSchema=spa_mock"
export APP_AI_DB_USER=postgres APP_AI_DB_PASSWORD=postgres APP_SPA_DB_USER=postgres APP_SPA_DB_PASSWORD=postgres
export APP_MOCK_ENABLED=true APP_RULES_EFFECTIVE_DATE=2026-09-01
mvn spring-boot:run

# Web (cửa sổ khác) — Vite chuyển /api sang localhost:8080
cd web && npm install && npm run dev
```

Đăng nhập: `admin` / `yellow123`.

## Deploy V1

### 1. Supabase (chỉ dùng làm Postgres)
1. Tạo project mới (region Singapore cho gần).
2. Vào **Connect → Session pooler** (IPv4, Render Free không có IPv6), lấy host dạng `aws-0-ap-southeast-1.pooler.supabase.com`, port `5432`, user `postgres.<project-ref>`.
3. Không cần tạo bảng: API tự tạo schema `spa_ai`, `spa_mock` khi khởi động lần đầu.

### 2. Render (API)
1. **New → Blueprint**, chọn repo này (dùng `render.yaml`), hoặc **New → Web Service → Docker**, Root Directory `api`.
2. Nhập biến môi trường:

| Biến | Giá trị |
|---|---|
| `APP_AI_DB_URL` | `jdbc:postgresql://<pooler-host>:5432/postgres?currentSchema=spa_ai&sslmode=require` |
| `APP_SPA_DB_URL` | như trên nhưng `currentSchema=spa_mock` |
| `APP_AI_DB_USER`, `APP_SPA_DB_USER` | `postgres.<project-ref>` |
| `APP_AI_DB_PASSWORD`, `APP_SPA_DB_PASSWORD` | mật khẩu DB Supabase |
| `APP_MOCK_ENABLED` | `true` (V1) |
| `APP_RULES_EFFECTIVE_DATE` | `2026-09-01` để demo có đơn kiểm tra; go-live thật `2026-12-01` |
| `APP_CORS_ORIGINS` | `https://*.vercel.app` (hoặc domain Vercel cụ thể) |
| `APP_ADMIN_PASSWORD_HASH` | để trống = `yellow123`; hoặc BCrypt từ `java -jar app.jar hash <mat-khau>` |

3. Kiểm tra: `https://<service>.onrender.com/api/health` trả `{"status":"UP"}`. Lần đầu khởi động sẽ sinh dữ liệu giả (~30 giây).

Render Free ngủ sau 15 phút không dùng; lần mở kế tiếp chờ 1–2 phút. Khi demo có thể dùng cron-job.org ping `/api/health` mỗi 10 phút.

### 3. Vercel (web)
1. **Add New → Project**, chọn repo, **Root Directory = `web`**, Framework = Vite.
2. Biến môi trường `VITE_API_URL = https://<service>.onrender.com`.
3. Deploy. Thêm domain Vercel vào `APP_CORS_ORIGINS` trên Render nếu không dùng `*.vercel.app`.

### Sinh lại dữ liệu giả
Dữ liệu giả tính theo ngày hiện tại. Muốn làm mới: `POST /api/mock/reset` (cần đăng nhập, chỉ chạy khi `APP_MOCK_ENABLED=true`).

## Cấu hình hoa hồng (biến môi trường)

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `APP_RULES_EFFECTIVE_DATE` | `2026-12-01` | Ngày áp dụng luật mới (D.4). Đơn trước ngày này không kiểm tra |
| `APP_COMMISSION_TOLERANCE_VND` | `1000` | Lệch trong mức này coi là đúng |
| `APP_COMMISSION_PACKAGE_BASE` | `LIST_PRICE_X_SESSIONS` | Căn cứ tính % của gói: giá gốc 1 buổi × số buổi chính, hoặc `ORDER_UNIT_PRICE` (giá bán trên dòng) |
| `APP_CONSULTANT_ROLE_VALUES` | `Tư vấn,CONSULTANT` | Giá trị `order_item_staff.staff_role` được coi là Tư vấn |
| `APP_OLD_CONFIG_ROLE` | `CONSULTANT` | `commission.role` dùng làm "% cấu hình cũ" |

## Sang V2 (kết nối Spa thật)
1. Trên server Spa tạo user chỉ đọc, ví dụ:
   ```sql
   CREATE USER 'spa_ai_ro'@'%' IDENTIFIED BY '...';
   GRANT SELECT ON clinic_staging.* TO 'spa_ai_ro'@'%';
   ```
2. `APP_SPA_DB_URL=jdbc:mysql://<host>:3306/clinic_staging?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Ho_Chi_Minh`, `APP_MOCK_ENABLED=false`.
3. `spa_ai` chuyển sang MySQL container riêng: `APP_AI_DB_URL=jdbc:mysql://ai-mysql:3306/spa_ai…` (migration tự chọn kiểu `DATETIME`).
4. Chạy lại `mvn test` và kiểm tra thủ công màn Hoa hồng trên dữ liệu thật trước khi cho khách dùng.
