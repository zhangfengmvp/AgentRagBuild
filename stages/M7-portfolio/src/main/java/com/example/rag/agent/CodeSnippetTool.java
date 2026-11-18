package com.example.rag.agent;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * T2 代码片段检索工具：按主题返回 Spring / 分布式相关的示例代码。
 * 用于 Agent 在回答中补充可运行的代码实现（如 Spring 事务、Redis 分布式锁、接口幂等）。
 * 示例片段内存维护（学习版），生产可接代码检索服务。
 */
@Service
public class CodeSnippetTool implements Tool {

    private final Map<String, String> snippets = new LinkedHashMap<>();

    public CodeSnippetTool() {
        snippets.put("spring-transaction", """
                @Service
                public class OrderService {
                    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
                    public void createOrder(Order order) {
                        orderMapper.insert(order);
                        inventoryService.deduct(order.getSkuId(), order.getCount());
                    }
                }""");

        snippets.put("redis-distributed-lock", """
                public boolean tryLock(String key, String token, long expireMs) {
                    Boolean ok = redisTemplate.opsForValue()
                            .setIfAbsent(key, token, Duration.ofMillis(expireMs));
                    return Boolean.TRUE.equals(ok);
                }
                public void unlock(String key, String token) {
                    // 用 Lua 脚本保证「值匹配才删」，避免误删别人的锁
                    String script = "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end";
                    redisTemplate.execute(new DefaultRedisScript<>(script, Long.class), List.of(key), token);
                }""");

        snippets.put("idempotency", """
                // 前端/网关下发唯一请求号，写入 Redis 原子占位，存在则视为重复
                String token = request.getHeader("Idempotency-Key");
                Boolean first = redisTemplate.opsForValue()
                        .setIfAbsent("idem:" + token, "1", Duration.ofMinutes(10));
                if (Boolean.FALSE.equals(first)) {
                    throw new RepeatSubmitException("请勿重复提交");
                }""");
    }

    @Override
    public String name() {
        return "codeSnippetSearch";
    }

    @Override
    public String description() {
        return "按主题返回 Spring / 分布式相关的示例代码（如 spring-transaction / redis-distributed-lock / idempotency）。" +
                "当用户需要看代码实现或示例时使用。";
    }

    @Override
    public String inputSchema() {
        return """
                {"type":"object","properties":{"topic":{"type":"string","description":"代码主题，如 spring-transaction / redis-distributed-lock / idempotency"}},"required":["topic"]}""";
    }

    @Override
    public String execute(Map<String, Object> args) {
        String topic = String.valueOf(args.getOrDefault("topic", "")).trim().toLowerCase();
        String code = snippets.get(topic);
        if (code == null) {
            for (Map.Entry<String, String> e : snippets.entrySet()) {
                if (e.getKey().contains(topic) || topic.contains(e.getKey())) {
                    code = e.getValue();
                    break;
                }
            }
        }
        if (code == null) {
            return "未找到主题「" + topic + "」的示例代码。可用主题：" + String.join(", ", snippets.keySet());
        }
        return "【示例代码：" + topic + "】\n```\n" + code + "\n```";
    }
}
