package vn.yellow.ai.commission.engine;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Đếm "khách làm nhóm dịch vụ này lần thứ mấy" (B.2, D.1, D.3).
 * <ul>
 *   <li>Đếm theo khách × nhóm dịch vụ, mọi chi nhánh, tính cả lẻ lẫn gói.</li>
 *   <li>Chỉ đếm dòng có trong phần mềm; người gọi phải bỏ trước các đơn đã hủy / hoàn / xóa.</li>
 *   <li>Thứ tự: ngày đơn → mã đơn → thời điểm tạo dòng → mã dòng. Dòng trước trong cùng đơn cũng được tính.</li>
 *   <li>Khách vãng lai (không có customer_id) luôn tính là lần đầu.</li>
 * </ul>
 */
public final class VisitCounter {

    private VisitCounter() {
    }

    public record HistoryLine(
            String itemId,
            String customerId,
            String groupCode,
            LocalDateTime orderDate,
            String orderId,
            LocalDateTime itemCreatedAt) {
    }

    private static final Comparator<HistoryLine> ORDER = Comparator
            .comparing(HistoryLine::orderDate, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(HistoryLine::orderId, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(HistoryLine::itemCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(HistoryLine::itemId, Comparator.nullsFirst(Comparator.naturalOrder()));

    /** @return itemId → lần thứ mấy (bắt đầu từ 1) */
    public static Map<String, Integer> assign(List<HistoryLine> lines) {
        Map<String, Integer> seen = new HashMap<>();
        Map<String, Integer> result = new HashMap<>();
        lines.stream().sorted(ORDER).forEach(l -> {
            if (l.customerId() == null || l.customerId().isBlank()) {
                result.put(l.itemId(), 1);
                return;
            }
            String key = l.customerId() + "|" + l.groupCode();
            int n = seen.merge(key, 1, Integer::sum);
            result.put(l.itemId(), n);
        });
        return result;
    }
}
