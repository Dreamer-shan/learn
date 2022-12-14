

#### ThreadLocal、MDC和InheritableThreadLocal

在trade_order项目中看到有一个AppContext类，里面用到了MDC和InheritableThreadLocal

##### 概述

`ThreadLocal` 叫做本地线程变量，意思是说，`ThreadLocal` 中填充的的是当前线程的变量，该变量对其他线程而言是封闭且隔离的，`ThreadLocal` 为变量在每个线程中创建了一个副本，这样每个线程都可以访问自己内部的副本变量

##### 使用场景

1. 在进行对象跨层传递的时候，使用ThreadLocal可以避免多次传递，打破层次间的约束
2. 线程间数据隔离
3. 进行事务操作，用于存储线程事务信息
4. 数据库连接，`Session`会话管理

在trade_order项目中有个`AppContext`类，这是APP级别的上下文，里面就有很多`InheritableThreadLocal`，这是一些通用信息，如代理商，订单号等等，有些是类级别的上下文。

有多个用户请求打进来的时候，ThreadLocal就绑定了每个用户线程，从而做到了线程间的数据隔离。在上下文中随时可以取到ThreadLocal中存的信息，例如需要打印日志需要订单号`ORDER_NO`，这样就随时可以取到。

还有个代理商信息，在项目中是分库的，每个代理商使用不同的库，那么获取Connection的时候，需要根据代理商的信息获取不同的库的Connection，也可以通过ThreadLocal做到

```java
public class AppContext {
    // 代理商
    private static InheritableThreadLocal<String> SITE_CONTEXT = new InheritableThreadLocal<>();
    // 订单号
    private static InheritableThreadLocal<String> ORDER_NO_CONTEXT = new InheritableThreadLocal<>();
    public static void setOrderNoForLog(String orderNo) {
        ORDER_NO_CONTEXT.set(orderNo);
        if (StringUtils.isNotBlank(orderNo)) {
            MDC.put("orderNo", orderNo);
        }
    }
}
```

可以看到`setOrderNoForLo()`方法中还有一个`MDC.put()`方法，把订单号放了进去，这里说一下MDC机制

#### MDC

##### 背景

在开发过程中，经常会使用log记录一下当前请求的参数，过程和结果，以便帮助定位问题。在并发量小的情况下，日志打印不会剧增，可以很快就能通过打印的日志查看执行的情况。但是在高并发大量请求的场景下，日志也会频繁打印，刷新，通过查看日志来定位问题时就会变得很难，因为无法确定打印的日志是哪一条请求时打印的，从而影响问题的定位速度

**通过 MDC 机制，将请求的 trace-id 放入到MDC中，在日志打印时，通过 MDC 中的 trace-id 将同一个请求的每一条日志串联起来。因为 MDC 是线程隔离且安全的。**

MDC（Mapped Diagnostic Context，映射调试上下文），即将一些运行时的上下文数据通过logback打印出来，是 一种方便在多线程条件下记录日志的功能。和SiftingAppender一起，可以实现根据运行时的上下文数据，将日志保存到不同的文件中。

MDC是以单个线程为单位进行访问的，即对MDC数据的操作（如put, get）只对当前线程有效，所以也永远是线程安全的。

##### 原理

MDC类基本原理是其内部持有一个`ThreadLocal`实例，用于保存context数据，MDC提供了put/get/clear等几个核心接口，用于操作ThreadLocal中的数据；ThreadLocal中的K-V，可以在logback.xml中声明，最终将会打印在日志中。

**1. 在请求刚到达网关层时，添加一个过滤器，每个请求在过滤器时，在 logback 的MDC 中放入 trace-id。**

```java
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.slf4j.MDC;
 
import javax.servlet.*;
import java.io.IOException;
 
public class LogFilter implements Filter {
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        // 使用雪花算法生成一个请求Id
        String traceId = Long.toString(IdWorker.getId());
        MDC.put("trace-id",traceId);
    }
}
```

  **2. 在logback.xml 中配置 log 打印格式，并打印 trace-id**

```xml
<!-- 控制台输出 -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder charset="UTF-8">
            <!-- 输出日志记录格式,并打印 trace-id -->
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%trace-id] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>
```

##### 需要注意的地方

在使用MDC时需要注意一些问题，这些问题通常也是ThreadLocal引起的，比如我们**需要在线程退出之前清除（clear）MDC中的数据**；在线程池中使用MDC时，那么**需要在子线程退出之前清除数据；可以调用MDC.clear()方法**。

#### InheritableThreadLocal

 如果希望当前线程的ThreadLocal能够被子线程使用，实现方式就会相当困难（需要用户自己在代码中传递）。在此背景下，InheritableThreadLocal应运而生

逐步看Thread的创建过程

```java
Thread thread = new Thread();
```

```java
public Thread() {
init(null, null, "Thread-" + nextThreadNum(), 0);
}
```

```java
/**
 * Initializes a Thread with the current AccessControlContext.
 * @see #init(ThreadGroup,Runnable,String,long,AccessControlContext,boolean)
 */
private void init(ThreadGroup g, Runnable target, String name, long stackSize) {
    init(g, target, name, stackSize, null, true);
}
```

```java
/**
     * 初始化一个线程.
     * 此函数有两处调用，
     * 1、上面的 init()，不传AccessControlContext，inheritThreadLocals=true
     * 2、传递AccessControlContext，inheritThreadLocals=false
     */
private void init(ThreadGroup g, Runnable target, String name,
                  long stackSize, AccessControlContext acc,
                  boolean inheritThreadLocals) {
    ......（其他代码）

        if (inheritThreadLocals && parent.inheritableThreadLocals != null)
            this.inheritableThreadLocals =
            ThreadLocal.createInheritedMap(parent.inheritableThreadLocals);
    ......（其他代码）
}
```

可以看到，采用默认方式产生子线程时，inheritThreadLocals=true；若此时父线程inheritableThreadLocals不为空，则将父线程inheritableThreadLocals传递至子线程。

```java
static ThreadLocalMap createInheritedMap(ThreadLocalMap parentMap) {
    return new ThreadLocalMap(parentMap);
}
```

```java
/**
* 构建一个包含所有parentMap中Inheritable ThreadLocals的ThreadLocalMap
* 该函数只被 createInheritedMap() 调用.
 */
private ThreadLocalMap(ThreadLocalMap parentMap) {
    Entry[] parentTable = parentMap.table;
    int len = parentTable.length;
    setThreshold(len);
    // ThreadLocalMap 使用 Entry[] table 存储ThreadLocal
    table = new Entry[len];

    // 逐一复制 parentMap 的记录
    for (int j = 0; j < len; j++) {
        Entry e = parentTable[j];
        if (e != null) {
            @SuppressWarnings("unchecked")
            ThreadLocal<Object> key = (ThreadLocal<Object>) e.get();
            if (key != null) {
                // 可能会有同学好奇此处为何使用childValue，而不是直接赋值，
                // 毕竟childValue内部也是直接将e.value返回；
                // 个人理解，主要为了减轻阅读代码的难度
                Object value = key.childValue(e.value);
                Entry c = new Entry(key, value);
                int h = key.threadLocalHashCode & (len - 1);
                while (table[h] != null)
                    h = nextIndex(h, len);
                table[h] = c;
                size++;
            }
        }
    }
}
```

##### InheritableThreadLocal总结

InheritableThreadLocal主要用于子线程创建时，需要自动继承父线程的ThreadLocal变量，方便必要信息的进一步传递