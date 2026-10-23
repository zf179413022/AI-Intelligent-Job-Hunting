# HashMap 线程安全（E2E 验收夹具）

## HashMap 为什么线程不安全

HashMap 在并发场景下不安全，主要原因包括：

1. 多线程同时扩容时，JDK 7 头插法可能导致环形链表，从而死循环。
2. JDK 8 虽改为尾插，仍可能出现数据覆盖、size 不准等问题。
3. 读多写少或高并发写场景应使用 ConcurrentHashMap。

## ConcurrentHashMap

ConcurrentHashMap 通过分段或 CAS + synchronized 保证并发安全，适合替代 HashMap 做共享缓存映射。

## Spring Boot 自动配置

Spring Boot 通过 `@EnableAutoConfiguration` 与 `spring.factories` / `AutoConfiguration.imports`
加载自动配置类，按条件注解装配 DataSource、WebMvc 等组件。
