package com.oa.lowcode.service;

import java.util.List;
import java.util.Map;

/**
 * 考勤打卡服务接口
 *
 * <p><b>核心功能：</b>
 * <ul>
 *   <li>签到/签退：实时判定打卡状态，持久化到 attendance_record 表</li>
 *   <li>今日状态：查询当天是否已打卡及判定结果</li>
 *   <li>月度汇总：生成完整日历，每天标注状态</li>
 * </ul></p>
 *
 * <p><b>判定规则（签到）：</b>
 * <pre>
 *   实际时间 ≤ workStart + flexMinutes          → NORMAL
 *   实际时间 ≤ workStart + lateThreshold        → LATE
 *   实际时间 >  workStart + lateThreshold        → SERIOUS_LATE
 * </pre></p>
 *
 * <p><b>判定规则（签退）：</b>
 * <pre>
 *   实际时间 ≥ workEnd - flexMinutes            → NORMAL
 *   实际时间 ≥ workEnd - earlyThreshold         → EARLY
 *   实际时间 <  workEnd - earlyThreshold         → SERIOUS_EARLY
 * </pre></p>
 *
 * <p><b>月度视图优先级：</b>休息日 → 已批准请假 → 打卡记录 → 缺卡</p>
 */
public interface AttendanceService {

    /** 上班打卡 */
    Map<String, Object> signIn(Long userId, String userName);

    /** 下班打卡（未打上班卡则标记 MISSING） */
    Map<String, Object> signOut(Long userId, String userName);

    /** 查询今日打卡状态 */
    Map<String, Object> getTodayStatus(Long userId);

    /**
     * 月度考勤汇总
     * <p>返回完整日历（1号~今天），每天标注：休息/请假/正常/迟到/早退/缺卡。
     * 数据来源：打卡记录 + 已批准请假记录。</p>
     */
    List<Map<String, Object>> getMonthlyRecords(Long userId, int year, int month);
}
