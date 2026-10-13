package com.git.hui.jobclaw.test;


import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * @date 2025/10/14
 */
public class ReTest {

    public static void main(String[] args) {
        // 1. 创建一个Sinks.One, 用于发射单个值
        Sinks.One<String> sink = Sinks.one();
        // 2. 获取对应的Mono视图
        Mono<String> mono = sink.asMono();

        // 3. 订阅
        mono.subscribe(
                value -> System.out.println("Received: " + value),
                error -> System.err.println("Error: " + error),
                () -> System.out.println("Completed")
        );

        // 4. 发射一个值
//        Sinks.EmitResult result = sink.tryEmitValue("Hello, Reactor!");
//
//        // 5. 检查结果
//        if (result.isSuccess()) {
//            System.out.println("Value emitted successfully.");
//        } else {
//            System.out.println("Failed to emit value.");
//        }

        // 6. 尝试再次发射失败,因为Sinks.One只能发射一次
//        Sinks.EmitResult secondResult = sink.tryEmitValue("Another value");
//        System.out.println("Second emit result: " + secondResult);

        // 7. 多次订阅数据呢
        for (int i = 0; i < 10; i++) {
            int finalI = i;
            Mono<String> ans = sink.asMono().flatMap(s -> Mono.just("Processed: " + finalI + " > " + s));
            String tt = ans.block();
            System.out.println(finalI + " > " + tt);
        }
        System.out.println("over");
    }
}
