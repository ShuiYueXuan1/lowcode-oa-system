package com.oa.lowcode.chain;

import cn.hutool.core.util.NumberUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * 条件分支评估器 —— 根据表单数据和节点条件配置判断节点是否需要审批
 *
 * <p><b>评估规则：</b>
 * <ol>
 *   <li>节点无 conditions（空数组或 null）→ 默认需要审批</li>
 *   <li>有 conditions → 逐条评估条件表达式
 *       <ul>
 *         <li>表达式为 true 且 action=REQUIRE → 需要审批（不跳过）</li>
 *         <li>表达式为 true 且 action=SKIP   → 跳过节点</li>
 *       </ul></li>
 *   <li>所有条件均不匹配 → 默认需要审批（不跳过）</li>
 * </ol></p>
 *
 * <p><b>支持的运算符：</b> &gt;  &gt;=  &lt; &lt;=  ==  !=
 * <br>数字类型自动识别比较，字符串类型精确匹配。</p>
 */
@Slf4j
public class ConditionEvaluator {

    /**
     * 判断节点是否应该跳过
     *
     * @param conditions 节点条件列表（来自 flow_schema.schema_json.nodes[i].conditions）
     * @param formData   用户提交的表单数据（如 {"days":5, "leave_type":"annual"}）
     * @return true=跳过该节点，不需要审批；false=需要审批
     */
    /**
     * 判断节点是否应该跳过
     *
     * <p><b>短路逻辑：</b>遍历条件列表，第一条表达式求值为 true 的条件立即生效：
     * <ul>
     *   <li>action=SKIP   → 返回 true（跳过该审批节点）</li>
     *   <li>action=REQUIRE → 返回 false（必须经过该节点）</li>
     * </ul>
     * 所有条件都不匹配 → 返回 false（默认需要审批，白名单跳过）</p>
     */
    @SuppressWarnings("unchecked")
    public static boolean shouldSkip(List<Map<String, Object>> conditions,
                                      Map<String, Object> formData) {
        // 无条件配置 → 默认不跳过（需要审批）
        if (conditions == null || conditions.isEmpty()) return false;

        // 遍历条件，第一个命中的立即生效（短路）
        for (Map<String, Object> cond : conditions) {
            String field = (String) cond.get("field");      // 表单字段 key，如 "days"
            String operator = (String) cond.get("operator"); // 比较运算符
            Object rawValue = cond.get("value");             // 条件阈值
            String action = (String) cond.getOrDefault("action", "REQUIRE");

            if (field == null || operator == null) continue;

            // 从用户提交的 formData 中取出对应字段的值
            Object fieldValue = formData.get(field);
            boolean conditionMet = evaluate(fieldValue, operator, rawValue);

            if (conditionMet) {
                log.info("条件匹配: field={}, operator={}, value={}, action={}", field, operator, rawValue, action);
                // action=SKIP 时跳过，action=REQUIRE 时不跳过
                return "SKIP".equals(action);
            }
        }
        // 所有条件都不匹配 → 默认需要审批
        return false;
    }

    /**
     * 执行单个条件表达式
     *
     * @param fieldValue 表单字段实际值
     * @param operator   运算符（> >= < <= == !=）
     * @param condValue  条件配置的目标值
     * @return true=条件成立
     */
    /**
     * 执行单个条件表达式
     *
     * <p><b>类型自动识别：</b>先用 Hutool NumberUtil 判断两边能否解析为数字，
     * 都能 → 转 double 数值比较（避免 "5" vs "10" 字典序错误）；
     * 否则 → 字符串精确匹配（仅支持 == 和 !=）。</p>
     */
    private static boolean evaluate(Object fieldValue, String operator, Object condValue) {
        if (fieldValue == null || condValue == null) return false;
        String fv = fieldValue.toString();
        String cv = condValue.toString();

        // 数字场景: 两边都能解析为数字 → 转 double 做数值比较
        // 避免字符串字典序问题: "5" > "10" 在字典序里是 true，但 5.0 > 10.0 是 false
        if (NumberUtil.isNumber(fv) && NumberUtil.isNumber(cv)) {
            double d1 = Double.parseDouble(fv);
            double d2 = Double.parseDouble(cv);
            return switch (operator) {
                case ">"  -> d1 > d2;
                case ">=" -> d1 >= d2;
                case "<"  -> d1 < d2;
                case "<=" -> d1 <= d2;
                case "==" -> d1 == d2;
                case "!=" -> d1 != d2;
                default   -> false;
            };
        }

        // 字符串场景: 仅支持精确相等/不等比较
        return switch (operator) {
            case "==" -> fv.equals(cv);
            case "!=" -> !fv.equals(cv);
            default   -> false;  // 非数字不能用 > < 等比较
        };
    }
}
