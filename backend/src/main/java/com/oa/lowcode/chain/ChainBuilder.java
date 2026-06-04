package com.oa.lowcode.chain;

import cn.hutool.core.util.StrUtil;
import com.oa.lowcode.entity.ApprovalNode;
import com.oa.lowcode.handler.ApproveHandler;
import com.oa.lowcode.mapper.ApprovalNodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 责任链构建器 —— 流程引擎的核心组件
 *
 * <p><b>职责：</b>读取 flow_schema 的 JSON 配置，动态组装审批责任链。</p>
 *
 * <p><b>工作流程：</b>
 * <ol>
 *   <li>解析 schema_json.nodes 数组</li>
 *   <li>对每个节点调用 ConditionEvaluator 评估条件分支
 *       <ul>
 *         <li>无条件 → 加入责任链</li>
 *         <li>条件满足且 action=SKIP → 跳过该节点</li>
 *         <li>条件满足且 action=REQUIRE → 加入责任链</li>
 *       </ul></li>
 *   <li>根据 approval_node 表的 handler_type 字段，反射实例化对应的 Handler Bean</li>
 *   <li>将 Handler 按顺序串联（setNext），返回链头</li>
 *   <li>生成 resolvedNodes 快照存入 process_instance.snapshot_json
 *       （用于后续审批流转时查找下一个节点，且不受后续 schema 变更影响）</li>
 * </ol></p>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 *   ChainBuilder.BuildResult result = chainBuilder.build(nodesConfig, formData);
 *   ApproveHandler head = result.chainHead();
 *   process.setSnapshotJson(result.snapshot());
 * }</pre></p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChainBuilder {

    private final ApplicationContext applicationContext;
    private final ApprovalNodeMapper approvalNodeMapper;

    /**
     * 构建责任链并生成流程快照
     *
     * @param nodesConfig flow_schema.schema_json.nodes 数组
     * @param formData    用户提交的表单数据（用于条件评估）
     * @return BuildResult 包含链头 Handler 和流程解析快照
     */
    @SuppressWarnings("unchecked")
    public BuildResult build(List<Map<String, Object>> nodesConfig,
                              Map<String, Object> formData) {
        List<Map<String, Object>> resolvedNodes = new ArrayList<>();
        ApproveHandler head = null;
        ApproveHandler tail = null;

        for (int i = 0; i < nodesConfig.size(); i++) {
            Map<String, Object> nodeConfig = nodesConfig.get(i);
            String nodeCode = (String) nodeConfig.get("nodeCode");
            String nodeName = (String) nodeConfig.get("nodeName");
            List<Map<String, Object>> conditions =
                    (List<Map<String, Object>>) nodeConfig.getOrDefault("conditions", List.of());

            boolean skipped = ConditionEvaluator.shouldSkip(conditions, formData);

            Map<String, Object> resolved = new LinkedHashMap<>();
            resolved.put("nodeId", nodeConfig.get("nodeId"));
            resolved.put("nodeCode", nodeCode);
            resolved.put("nodeName", nodeName);
            resolved.put("order", nodeConfig.getOrDefault("order", i + 1));
            resolved.put("required", !skipped);
            resolved.put("conditions", conditions);

            if (skipped) {
                resolved.put("skipReason", "条件不满足，跳过");
                resolvedNodes.add(resolved);
                log.info("节点 [{}] 条件不满足，跳过", nodeName);
                continue;
            }

            ApproveHandler handler = findHandler(nodeCode);
            if (handler == null) {
                resolved.put("skipReason", "未找到 Handler: " + nodeCode);
                resolvedNodes.add(resolved);
                log.warn("节点 [{}] 未找到 Handler: {}", nodeName, nodeCode);
                continue;
            }

            handler.setNodeConfig(nodeConfig);
            resolvedNodes.add(resolved);

            if (head == null) { head = handler; tail = handler; }
            else { tail.setNext(handler); tail = handler; }

            log.info("节点 [{}] → Handler: {}", nodeName, handler.getClass().getSimpleName());
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("resolvedNodes", resolvedNodes);
        snapshot.put("buildTime", System.currentTimeMillis());

        return new BuildResult(head, snapshot);
    }

    /**
     * 根据 nodeCode 查找对应的 Spring Bean Handler
     *
     * <p>通过 approval_node 表查找 handler_type（全限定类名），
     * 然后反射实例化对应的 Handler Bean。</p>
     */
    private ApproveHandler findHandler(String nodeCode) {
        ApprovalNode node = approvalNodeMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ApprovalNode>()
                        .eq(ApprovalNode::getNodeCode, nodeCode));
        if (node == null || StrUtil.isBlank(node.getHandlerType())) return null;
        try {
            Class<?> clazz = Class.forName(node.getHandlerType());
            return (ApproveHandler) applicationContext.getBean(clazz);
        } catch (Exception e) {
            log.error("反射实例化 Handler 失败: {}", node.getHandlerType(), e);
            return null;
        }
    }

    /**
     * 责任链构建结果
     * @param chainHead 责任链头节点（为 null 表示所有节点被跳过）
     * @param snapshot  流程解析快照，需存入 process_instance.snapshot_json
     */
    public record BuildResult(ApproveHandler chainHead, Map<String, Object> snapshot) {}
}
