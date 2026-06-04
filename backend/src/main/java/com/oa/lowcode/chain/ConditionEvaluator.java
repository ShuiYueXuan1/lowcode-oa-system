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
    @SuppressWarnings("unchecked")
    public static boolean shouldSkip(List<Map<String, Object>> conditions,
                                      Map<String, Object> formData) {
        if (conditions == null || conditions.isEmpty()) return false;

        for (Map<String, Object> cond : conditions) {
            String field = (String) cond.get("field");
            String operator = (String) cond.get("operator");
            Object rawValue = cond.get("value");
            String action = (String) cond.getOrDefault("action", "REQUIRE");

            if (field == null || operator == null) continue;

            Object fieldValue = formData.get(field);
            boolean conditionMet = evaluate(fieldValue, operator, rawValue);

            if (conditionMet) {
                log.info("条件匹配: field={}, operator={}, value={}, action={}", field, operator, rawValue, action);
                return "SKIP".equals(action);
            }
        }
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
    private static boolean evaluate(Object fieldValue, String operator, Object condValue) {
        if (fieldValue == null || condValue == null) return false;
        String fv = fieldValue.toString();
        String cv = condValue.toString();

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

        return switch (operator) {
            case "==" -> fv.equals(cv);
            case "!=" -> !fv.equals(cv);
            default   -> false;
        };
    }
}
