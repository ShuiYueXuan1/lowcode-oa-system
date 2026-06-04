package com.oa.lowcode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oa.lowcode.config.SchemaCacheManager;
import com.oa.lowcode.entity.AttendanceRecord;
import com.oa.lowcode.entity.LeaveInstance;
import com.oa.lowcode.mapper.AttendanceRecordMapper;
import com.oa.lowcode.mapper.LeaveInstanceMapper;
import com.oa.lowcode.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 考勤服务实现 —— 打卡判定引擎
 *
 * <p><b>核心职责：</b>
 * <ol>
 *   <li>签到/签退 — 实时判定打卡状态并持久化</li>
 *   <li>今日状态 — 查询当天是否已打卡及判定结果</li>
 *   <li>月度汇总 — 生成完整日历，每天标注状态</li>
 * </ol></p>
 *
 * <p><b>月度视图判定优先级：</b>休息日 → 已批准的请假 → 打卡记录 → 缺卡</p>
 *
 * <p><b>数据来源：</b>
 * <ul>
 *   <li>考勤规则 — Caffeine 缓存（SchemaCacheManager）</li>
 *   <li>打卡记录 — attendance_record 表</li>
 *   <li>请假记录 — leave_instance 表（status=APPROVED）</li>
 * </ul></p>
 *
 * <p><b>签到判定区间：</b>
 * <pre>
 *   workStart                          workStart+lateThreshold
 *      |--- flex ---|------ LATE ------|---- SERIOUS_LATE ----|
 *      NORMAL       (弹性, 阈值]        > 阈值
 * </pre></p>
 *
 * <p><b>签退判定区间：</b>
 * <pre>
 *   workEnd-earlyThreshold             workEnd
 *      |-- SERIOUS_EARLY --|-- EARLY --|- flex -|
 *      < 阈值              [阈值, 弹性)  NORMAL
 * </pre></p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {

    private final SchemaCacheManager cacheManager;
    private final AttendanceRecordMapper recordMapper;
    private final LeaveInstanceMapper leaveInstanceMapper;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 上班打卡
     *
     * <p><b>流程：</b>
     * <ol>
     *   <li>从缓存加载考勤规则</li>
     *   <li>判断今天是否需要上班（休息日/节假日直接返回）</li>
     *   <li>查找或创建今日打卡记录（防重复）</li>
     *   <li>根据 baseRule 判定：≤弹性→NORMAL | ≤阈值→LATE | ＞阈值→SERIOUS_LATE</li>
     *   <li>更新记录并返回</li>
     * </ol></p>
     *
     * @param userId   员工 ID
     * @param userName 员工姓名
     * @return { record, signInStatus, message }
     */
    @Override
    public Map<String, Object> signIn(Long userId, String userName) {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        Map<String, Object> ruleData = loadCurrentRule();
        if (ruleData == null) throw new IllegalArgumentException("未配置考勤规则");

        DayStatus dayStatus = checkDayStatus(today, ruleData);
        if (!dayStatus.needWork) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("message", dayStatus.reason);
            result.put("needWork", false);
            return result;
        }

        AttendanceRecord record = findOrCreateRecord(userId, userName, today);
        if (record.getSignInTime() != null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("message", "已打过上班卡: " + record.getSignInTime());
            result.put("record", record);
            return result;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> baseRule = (Map<String, Object>) ruleData.get("baseRule");
        String status = evaluateSignIn(now.toLocalTime(), baseRule);

        record.setSignInTime(now);
        record.setStatus(status);
        recordMapper.updateById(record);

        log.info("上班打卡: userId={}, time={}, status={}", userId, now, status);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("record", record);
        result.put("signInStatus", status);
        result.put("message", "打卡成功 - " + getStatusLabel(status));
        return result;
    }

    /**
     * 下班打卡
     *
     * <p><b>流程：</b>
     * <ol>
     *   <li>加载规则、判断工作日</li>
     *   <li>查找今日记录（未打上班卡则标记 MISSING）</li>
     *   <li>判定：≥弹性起点→NORMAL | ≥早退阈值→EARLY | ＜早退阈值→SERIOUS_EARLY</li>
     *   <li>上午已有迟到记录时不覆盖，保留迟到状态</li>
     * </ol></p>
     */
    @Override
    public Map<String, Object> signOut(Long userId, String userName) {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        Map<String, Object> ruleData = loadCurrentRule();
        if (ruleData == null) throw new IllegalArgumentException("未配置考勤规则");

        DayStatus dayStatus = checkDayStatus(today, ruleData);
        if (!dayStatus.needWork) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("message", dayStatus.reason);
            result.put("needWork", false);
            return result;
        }

        AttendanceRecord record = findOrCreateRecord(userId, userName, today);
        if (record.getSignOutTime() != null) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("message", "已打过下班卡: " + record.getSignOutTime());
            result.put("record", record);
            return result;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> baseRule = (Map<String, Object>) ruleData.get("baseRule");

        // 未打上班卡 → 缺卡；否则综合判定（上午有异常则保留上午状态）
        if (record.getSignInTime() == null) {
            record.setStatus("MISSING");
        } else {
            String outStatus = evaluateSignOut(now.toLocalTime(), baseRule);
            if (!"NORMAL".equals(record.getStatus())) {
                // 上午已有迟到/严重迟到，保持不变
            } else if (!"NORMAL".equals(outStatus)) {
                record.setStatus(outStatus);
            }
        }

        record.setSignOutTime(now);
        recordMapper.updateById(record);

        log.info("下班打卡: userId={}, time={}, status={}", userId, now, record.getStatus());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("record", record);
        result.put("signOutStatus", record.getStatus());
        result.put("message", "打卡成功 - " + getStatusLabel(record.getStatus()));
        return result;
    }

    /** 查询今日打卡状态（是否已签到/签退、判定结果、是否需要上班） */
    @Override
    public Map<String, Object> getTodayStatus(Long userId) {
        LocalDate today = LocalDate.now();
        Map<String, Object> ruleData = loadCurrentRule();
        DayStatus dayStatus = ruleData != null ? checkDayStatus(today, ruleData)
                : new DayStatus(true, "");
        AttendanceRecord record = recordMapper.selectOne(new LambdaQueryWrapper<AttendanceRecord>()
                .eq(AttendanceRecord::getUserId, userId).eq(AttendanceRecord::getRecordDate, today));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("today", today.format(DATE_FMT));
        result.put("needWork", dayStatus.needWork);
        result.put("dayReason", dayStatus.reason);
        result.put("record", record);
        result.put("currentTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        return result;
    }

    /**
     * 月度考勤汇总 —— 生成完整日历视图
     *
     * <p>返回指定年月的每一天（1号~今天），每天标注状态：
     * <ul>
     *   <li>休息日（节假日/周末）→ REST</li>
     *   <li>已批准的请假 → LEAVE</li>
     *   <li>有打卡记录 → 根据 DB 中的 status</li>
     *   <li>过去工作日无记录 → MISSING</li>
     *   <li>今天待完成 → PENDING</li>
     * </ul></p>
     *
     * <p>数据来源：打卡记录（attendance_record）+ 已批准请假（leave_instance）</p>
     */
    @Override
    public List<Map<String, Object>> getMonthlyRecords(Long userId, int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate today = LocalDate.now();
        LocalDate monthEnd = start.plusMonths(1).minusDays(1);
        LocalDate end = monthEnd.isBefore(today) ? monthEnd : today;

        Map<String, Object> ruleData = loadCurrentRule();

        // 当月打卡记录 → 建立日期索引
        List<AttendanceRecord> records = recordMapper.selectList(
                new LambdaQueryWrapper<AttendanceRecord>()
                        .eq(AttendanceRecord::getUserId, userId)
                        .between(AttendanceRecord::getRecordDate, start, end)
                        .orderByAsc(AttendanceRecord::getRecordDate));
        Map<LocalDate, AttendanceRecord> recordMap = new LinkedHashMap<>();
        for (AttendanceRecord r : records) recordMap.put(r.getRecordDate(), r);

        // 已批准的请假记录 → 请假日期标注
        List<LeaveInstance> leaves = leaveInstanceMapper.selectList(
                new LambdaQueryWrapper<LeaveInstance>()
                        .eq(LeaveInstance::getApplicantId, userId)
                        .eq(LeaveInstance::getStatus, "APPROVED"));

        List<Map<String, Object>> result = new ArrayList<>();
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("date", cursor.format(DATE_FMT));
            AttendanceRecord rec = recordMap.get(cursor);
            DayStatus dayStatus = ruleData != null ? checkDayStatus(cursor, ruleData)
                    : new DayStatus(cursor.getDayOfWeek().getValue() <= 5, "");

            if (!dayStatus.needWork) {                                                          // 优先级1: 休息日
                item.put("signInTime", null); item.put("signOutTime", null);
                item.put("status", "REST");
                item.put("statusLabel", dayStatus.reason.isEmpty() ? "休息" : dayStatus.reason);
            } else if (isOnApprovedLeave(cursor, leaves)) {                                     // 优先级2: 请假
                item.put("signInTime", rec != null ? rec.getSignInTime() : null);
                item.put("signOutTime", rec != null ? rec.getSignOutTime() : null);
                item.put("status", "LEAVE"); item.put("statusLabel", "请假");
            } else if (rec != null) {                                                            // 优先级3: 打卡
                item.put("signInTime", rec.getSignInTime()); item.put("signOutTime", rec.getSignOutTime());
                if (rec.getSignOutTime() != null) {
                    item.put("status", rec.getStatus()); item.put("statusLabel", getStatusLabel(rec.getStatus()));
                } else if (cursor.isBefore(today)) {
                    item.put("status", "MISSING"); item.put("statusLabel", "缺卡(未签退)");
                } else {
                    item.put("status", "PENDING"); item.put("statusLabel", "待签退");
                }
            } else {                                                                             // 优先级4: 缺卡
                item.put("signInTime", null); item.put("signOutTime", null);
                if (cursor.equals(today)) {
                    item.put("status", "PENDING"); item.put("statusLabel", "待打卡");
                } else {
                    item.put("status", "MISSING"); item.put("statusLabel", "缺卡");
                }
            }
            result.add(item);
            cursor = cursor.plusDays(1);
        }
        return result;
    }

    // ===== 私有方法 =====

    /** 判断某天是否在已批准的请假日期范围内（从 leave_instance.form_data 读取 start_date/end_date） */
    @SuppressWarnings("unchecked")
    private boolean isOnApprovedLeave(LocalDate date, List<LeaveInstance> leaves) {
        for (LeaveInstance leave : leaves) {
            Map<String, Object> formData = leave.getFormData();
            if (formData == null) continue;
            try {
                String startStr = (String) formData.get("start_date");
                String endStr = (String) formData.get("end_date");
                if (startStr == null || endStr == null) continue;
                if (!date.isBefore(LocalDate.parse(startStr)) && !date.isAfter(LocalDate.parse(endStr)))
                    return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    /**
     * 上班打卡状态判定
     * @param clockTime 实际打卡时间
     * @param baseRule  考勤规则
     * @return NORMAL | LATE | SERIOUS_LATE
     */
    private String evaluateSignIn(LocalTime clockTime, Map<String, Object> baseRule) {
        String workStartStr = (String) baseRule.getOrDefault("workStart", "09:00");
        int flexMinutes = toInt(baseRule.getOrDefault("flexMinutes", 0));
        int lateThreshold = toInt(baseRule.getOrDefault("lateThreshold", 30));
        LocalTime workStart = LocalTime.parse(workStartStr);
        LocalTime flexEnd = workStart.plusMinutes(flexMinutes);
        LocalTime lateEnd = workStart.plusMinutes(lateThreshold);
        if (!clockTime.isAfter(flexEnd)) return "NORMAL";
        else if (!clockTime.isAfter(lateEnd)) return "LATE";
        else return "SERIOUS_LATE";
    }

    /**
     * 下班打卡状态判定
     * @param clockTime 实际打卡时间
     * @param baseRule  考勤规则
     * @return NORMAL | EARLY | SERIOUS_EARLY
     */
    private String evaluateSignOut(LocalTime clockTime, Map<String, Object> baseRule) {
        String workEndStr = (String) baseRule.getOrDefault("workEnd", "18:00");
        int flexMinutes = toInt(baseRule.getOrDefault("flexMinutes", 0));
        int earlyThreshold = toInt(baseRule.getOrDefault("earlyThreshold", 30));
        LocalTime workEnd = LocalTime.parse(workEndStr);
        LocalTime flexStart = workEnd.minusMinutes(flexMinutes);
        LocalTime earlyStart = workEnd.minusMinutes(earlyThreshold);
        if (!clockTime.isBefore(flexStart)) return "NORMAL";
        else if (!clockTime.isBefore(earlyStart)) return "EARLY";
        else return "SERIOUS_EARLY";
    }

    /** 判断某天是否需要上班：特殊日 → 周末 → 默认工作日 */
    @SuppressWarnings("unchecked")
    private DayStatus checkDayStatus(LocalDate date, Map<String, Object> ruleData) {
        String dateStr = date.format(DATE_FMT);
        List<Map<String, Object>> specialDays =
                (List<Map<String, Object>>) ruleData.getOrDefault("specialDays", List.of());
        for (Map<String, Object> sd : specialDays) {
            if (dateStr.equals(sd.get("date"))) {
                if ("HOLIDAY".equals(sd.get("type")))
                    return new DayStatus(false, "节假日: " + sd.getOrDefault("desc", ""));
                if ("WORKDAY".equals(sd.get("type")))
                    return new DayStatus(true, "调休上班");
            }
        }
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) return new DayStatus(false, "周末休息");
        return new DayStatus(true, "工作日");
    }

    private Map<String, Object> loadCurrentRule() { return cacheManager.getAttendanceSchema(); }

    /** 查找或创建今日考勤记录（幂等） */
    private AttendanceRecord findOrCreateRecord(Long userId, String userName, LocalDate date) {
        AttendanceRecord record = recordMapper.selectOne(new LambdaQueryWrapper<AttendanceRecord>()
                .eq(AttendanceRecord::getUserId, userId).eq(AttendanceRecord::getRecordDate, date));
        if (record == null) {
            record = new AttendanceRecord();
            record.setUserId(userId); record.setUserName(userName); record.setRecordDate(date);
            recordMapper.insert(record);
        }
        return record;
    }

    /** 状态码 → 中文标签 */
    private String getStatusLabel(String status) {
        if (status == null) return "未打卡";
        return switch (status) {
            case "NORMAL" -> "正常"; case "LATE" -> "迟到"; case "SERIOUS_LATE" -> "严重迟到";
            case "EARLY" -> "早退"; case "SERIOUS_EARLY" -> "严重早退"; case "MISSING" -> "缺卡";
            case "REST" -> "休息"; case "LEAVE" -> "请假"; case "PENDING" -> "待处理";
            default -> status;
        };
    }

    private int toInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number n) return n.intValue();
        try { return Integer.parseInt(value.toString()); } catch (NumberFormatException e) { return 0; }
    }

    private record DayStatus(boolean needWork, String reason) {}
}
