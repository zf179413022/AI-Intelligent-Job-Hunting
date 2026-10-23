# HashMap 与 ConcurrentHashMap

这段文档用来验收知识库解析：表格应保持 Markdown 表格，代码块应保留围栏和正文。

## 对比表

| 结构 | 线程安全 | 适用场景 |
| --- | --- | --- |
| HashMap | 否 | 单线程缓存 |
| ConcurrentHashMap | 是 | 高并发读写 |
| Hashtable | 是，整表锁 | 遗留代码 |

单元格里的竖线要保留，不能被拆成新列：`a \| b`，行内代码 `put(k\|v)`。

## 示例代码

下面这段不是标题，链接也不该被改写。

```java
# 这行是注释，不是标题
public class Demo {
    public static void main(String[] args) {
        String url = "[文档](https://example.com)";
        System.out.println(url);
    }
}
```

## SQL 片段

```sql
select id, title
from knowledge_document
where status = 'PARSED';
```
