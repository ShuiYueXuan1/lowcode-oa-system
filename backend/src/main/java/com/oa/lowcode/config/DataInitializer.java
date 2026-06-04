package com.oa.lowcode.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.oa.lowcode.entity.AttendanceSchema;
import com.oa.lowcode.entity.FlowSchema;
import com.oa.lowcode.entity.FormSchema;
import com.oa.lowcode.mapper.AttendanceSchemaMapper;
import com.oa.lowcode.mapper.FlowSchemaMapper;
import com.oa.lowcode.mapper.FormSchemaMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据初始化器 —— 应用启动时自动插入演示数据
 *
 * <p>仅在数据库中对应表为空时才插入，保证幂等（不会重复插入）。</p>
 *
 * <p><b>插入内容：</b>
 * <ul>
 *   <li>请假表单 Schema（leave_apply，已发布，含 5 种请假类型）</li>
 *   <li>审批流程 Schema（leave_apply，已发布，直属主管 → 部门经理(条件:days&gt;3) → 人事）</li>
 *   <li>考勤规则 Schema（09:00-18:00，弹性 15 分钟，当前生效）</li>
 * </ul></p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final FormSchemaMapper formSchemaMapper;
    private final FlowSchemaMapper flowSchemaMapper;
    private final AttendanceSchemaMapper attendanceSchemaMapper;

    @Override
    public void run(String... args) {
        initFormSchema();
        initFlowSchema();
        initAttendanceSchema();
    }

    /**
     * 初始化请假表单 Schema（已发布，status=2）
     *
     * <p>表单字段：
     * <ul>
     *   <li>leave_type — 下拉选择（年假/事假/病假/婚假/调休）</li>
     *   <li>start_date / end_date — 日期选择</li>
     *   <li>days — 数字输入</li>
     *   <li>reason — 文本域</li>
     * </ul></p>
     */
    private void initFormSchema() {
        if (formSchemaMapper.selectCount(new LambdaQueryWrapper<>()) > 0) {
            log.info("表单 Schema 已存在，跳过初始化");
            return;
        }
        FormSchema schema = new FormSchema();
        schema.setName("请假申请表");
        schema.setCode("leave_apply");
        schema.setVersion(1);
        schema.setStatus(2);

        List<Map<String, Object>> fields = List.of(
                field("leave_type", "select", "请假类型", "请选择",
                        List.of(rule("请选择请假类型")),
                        List.of(opt("年假", "annual"), opt("事假", "personal"),
                                opt("病假", "sick"), opt("婚假", "marriage"),
                                opt("调休", "compensatory"))),
                field("start_date", "date", "开始日期", "请选择开始日期",
                        List.of(rule("请选择开始日期")), null),
                field("end_date", "date", "结束日期", "请选择结束日期",
                        List.of(rule("请选择结束日期")), null),
                field("days", "number", "请假天数", "请输入天数",
                        List.of(rule("请输入天数")), null),
                field("reason", "textarea", "请假原因", "请输入请假原因",
                        List.of(rule("请输入原因")), null));
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("labelWidth", 100);
        json.put("fields", fields);
        schema.setSchemaJson(json);
        formSchemaMapper.insert(schema);
        log.info("初始化表单 Schema: leave_apply");
    }

    /**
     * 初始化审批流程 Schema（已发布，status=2）
     *
     * <p>审批链：
     * <ol>
     *   <li>直属主管 — 无条件，始终需要审批</li>
     *   <li>部门经理 — 条件分支：days &gt; 3 时才需要审批</li>
     *   <li>人事 — 无条件，始终需要审批</li>
     * </ol></p>
     */
    private void initFlowSchema() {
        if (flowSchemaMapper.selectCount(new LambdaQueryWrapper<>()) > 0) {
            log.info("流程 Schema 已存在，跳过初始化");
            return;
        }
        FlowSchema schema = new FlowSchema();
        schema.setName("请假审批流程");
        schema.setCode("leave_apply");
        schema.setVersion(1);
        schema.setStatus(2);
        List<Map<String, Object>> nodes = List.of(
                flowNode("n1", "DIRECT_LEADER", "直属主管", 1, List.of()),
                flowNode("n2", "DEPT_MANAGER", "部门经理", 2,
                        List.of(flowCond("days", ">", 3, "REQUIRE"))),
                flowNode("n3", "HR", "人事", 3, List.of()));
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("nodes", nodes);
        schema.setSchemaJson(json);
        flowSchemaMapper.insert(schema);
        log.info("初始化流程 Schema: leave_apply");
    }

    /**
     * 初始化考勤规则（当前生效，is_current=1）
     *
     * <p>规则参数：
     * <ul>
     *   <li>上班 09:00 / 下班 18:00</li>
     *   <li>弹性 15 分钟</li>
     *   <li>迟到阈值 30 分钟</li>
     *   <li>早退阈值 30 分钟</li>
     * </ul></p>
     */
    private void initAttendanceSchema() {
        if (attendanceSchemaMapper.selectCount(new LambdaQueryWrapper<>()) > 0) {
            log.info("考勤规则已存在，跳过初始化");
            return;
        }
        AttendanceSchema schema = new AttendanceSchema();
        schema.setName("2025 标准考勤规则");
        schema.setIsCurrent(1);
        Map<String, Object> base = new LinkedHashMap<>();
        base.put("workStart", "09:00");
        base.put("workEnd", "18:00");
        base.put("flexMinutes", 15);
        base.put("lateThreshold", 30);
        base.put("earlyThreshold", 30);
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("baseRule", base);
        json.put("specialDays", List.of());
        schema.setSchemaJson(json);
        attendanceSchemaMapper.insert(schema);
        log.info("初始化考勤规则: 09:00-18:00, 弹性15分钟");
    }

    // ===== 构建辅助方法（生成 JSON 结构的便捷工厂） =====

    private Map<String, Object> field(String key, String type, String label, String placeholder,
                                       List<Map<String, Object>> rules, List<Map<String, Object>> options) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("key", key);
        f.put("type", type);
        f.put("label", label);
        f.put("placeholder", placeholder);
        f.put("rules", rules);
        if (options != null) f.put("options", options);
        return f;
    }

    private Map<String, Object> rule(String msg) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("required", true);
        r.put("message", msg);
        return r;
    }

    private Map<String, Object> opt(String label, String value) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("label", label);
        o.put("value", value);
        return o;
    }

    private Map<String, Object> flowNode(String id, String code, String name, int order,
                                          List<Map<String, Object>> conditions) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("nodeId", id);
        n.put("nodeCode", code);
        n.put("nodeName", name);
        n.put("order", order);
        n.put("conditions", conditions);
        return n;
    }

    private Map<String, Object> flowCond(String field, String operator, Object value, String action) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("field", field);
        c.put("operator", operator);
        c.put("value", value);
        c.put("action", action);
        return c;
    }
}
