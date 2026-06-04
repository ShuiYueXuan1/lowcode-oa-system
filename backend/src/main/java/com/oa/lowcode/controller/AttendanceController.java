package com.oa.lowcode.controller;

import com.oa.lowcode.common.Result;
import com.oa.lowcode.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 考勤打卡控制器
 *
 * <p><b>核心功能：</b>
 * <ul>
 *   <li>签到/签退 — 实时判定打卡状态并持久化</li>
 *   <li>今日状态 — 查询当天是否已打卡</li>
 *   <li>月度汇总 — 生成完整日历（含休息日、请假、缺卡标注）</li>
 * </ul></p>
 */
@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    /**
     * 上班打卡
     *
     * <p>判定逻辑：
     * <ul>
     *   <li>实际时间 ≤ (上班时间 + 弹性分钟) → NORMAL</li>
     *   <li>实际时间 ≤ (上班时间 + 迟到阈值) → LATE</li>
     *   <li>实际时间 &gt; (上班时间 + 迟到阈值) → SERIOUS_LATE</li>
     * </ul></p>
     */
    @PostMapping("/sign-in")
    public Result<Map<String, Object>> signIn(@RequestBody Map<String, Object> body) {
        Long userId = toLong(body.get("userId"));
        String userName = (String) body.getOrDefault("userName", "员工");
        return Result.ok(attendanceService.signIn(userId, userName));
    }

    /**
     * 下班打卡
     *
     * <p>判定逻辑：
     * <ul>
     *   <li>实际时间 ≥ (下班时间 - 弹性分钟) → NORMAL</li>
     *   <li>实际时间 ≥ (下班时间 - 早退阈值) → EARLY</li>
     *   <li>实际时间 &lt; (下班时间 - 早退阈值) → SERIOUS_EARLY</li>
     * </ul></p>
     */
    @PostMapping("/sign-out")
    public Result<Map<String, Object>> signOut(@RequestBody Map<String, Object> body) {
        Long userId = toLong(body.get("userId"));
        String userName = (String) body.getOrDefault("userName", "员工");
        return Result.ok(attendanceService.signOut(userId, userName));
    }

    /** 查询今日打卡状态（是否已签到/签退、判定结果、是否需要上班） */
    @GetMapping("/today")
    public Result<Map<String, Object>> today(@RequestParam(defaultValue = "1") Long userId) {
        return Result.ok(attendanceService.getTodayStatus(userId));
    }

    /**
     * 月度考勤汇总
     *
     * <p>返回完整日历（1号~今天），每天标注状态：
     * <ul>
     *   <li>REST — 休息日（周末/节假日）</li>
     *   <li>LEAVE — 已批准的请假</li>
     *   <li>NORMAL / LATE / EARLY — 打卡状态</li>
     *   <li>MISSING — 缺卡</li>
     *   <li>PENDING — 待打卡/待签退</li>
     * </ul></p>
     */
    @GetMapping("/monthly")
    public Result<List<Map<String, Object>>> monthly(
            @RequestParam(defaultValue = "1") Long userId,
            @RequestParam int year,
            @RequestParam int month) {
        return Result.ok(attendanceService.getMonthlyRecords(userId, year, month));
    }

    private Long toLong(Object value) {
        if (value == null) return 1L;
        if (value instanceof Number n) return n.longValue();
        try { return Long.parseLong(value.toString()); }
        catch (NumberFormatException e) { return 1L; }
    }
}
