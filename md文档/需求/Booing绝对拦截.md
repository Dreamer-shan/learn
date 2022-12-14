##### 背景

恶意订单通常都是支付后生编，但是如果支付后生编失败，此时会去换报价兜底，**而用户已经支付完，不可能再付一次，此时换报价就可能导致亏损**，因此某些时候，把订单标记为非恶意，走支付前生编，这样失败了的话就可以在支付前进行兜底，在用户还没支付的时候换供应导致的变价，可以给用户弹窗，降低亏损。

1、报价：

异步BOOKING tag中"asyncBookingStatus": "BOOKING_ABSOLUTE_INTERCEPT"，

（1）现在的逻辑，报价返回此状态码的异步booking信息，传给交易，交易给主站返回错误状态码，主站弹窗给用户：抱歉，该价格的机票已经售完，请重新搜索预订。

（2）现将此逻辑改为：报价识别到异步BOOKING tag中"asyncBookingStatus": "BOOKING_ABSOLUTE_INTERCEPT"，此时，将此状态码更改为新定义的状态码，同时将同步BOOKING tag信息复制到异步BOOKING tag中，传给交易。

2、交易：

（1）交易在收到有新状态码的异步BOOKING tag时，在此处生编订单打标，不走恶意判断，使用支付前生编逻辑，其余继续使用同步的数据走当前机票生单流程，在生单和支付前校验失败，走兜底询价逻辑。

```xml
<qflow:component id="asynBookingTagCheckComp" desc="异步BookingTag参数校验"/>
....
<qflow:component id="initAsynDataComp" desc="初始化异步数据"/>

```

分析：相当于接收一个新的状态码，**如果是这种状态码，就给订单打上非恶意订单的标识**

判断状态码是在异步BookingTag参数校验的时候进行，而判断恶意是在初始化异步数据的时候进行com.qunar.flight.trade.core.component.base.impl.OrderJudgeServiceImpl#doPnrMeanOrderCheck，**中间隔了很长的流程组**，因此需要把状态带到initAsynDataComp阶段有两种方案：

**一是选择放到Appcontext(ThreadLocal)里面，二是选择放到extMap里面，这两者都基本相当于一个全局的变量。但是考虑到放ThreadLocal里面可能不保险，所以选择了放到extMap里**

```java
if (AsyncBookingStatusEnum.BOOKING_SEARCH_INVALID == asynBookingTag.getAsyncBookingStatus()) {
    log.info("状态码为booking搜索兜底,打上非恶意订单标签");
    QMonitor.recordOne(QMonitorConts.ASYNCBOOKING_FALLBACK);
    ExtUtils.setExt(receiveOrder, ExtKey.IS_NOT_MEAN_ORDER,Boolean.TRUE.toString());
    return OrderResultUtil.buildSuccessResult(createOrderBean);
}
```

##### 坑点：

1. 发完代码后，发现生单失败率特别高，点击去支付就显示生单失败了，最初以为是选择的报价有问题，后面发现确实是跟报价有关系。**虽然改动的是trade_core的代码，其实在trade_order那边就已经取不到bookingTag了，导致生单失败，所以出现异常情况时，可以去打开主流程trade_order、trade_core的error日志都去看一看，虽然改动的是trade_core的代码，但是在trade_order就已经取不到BookingTag了。**
2. **在做需求的时候需要有全局的概念，因为系统其实都是互相有关联的。如果有异常，几个系统都要看看。不要一直做无用功。**
3. 发API导致的报错，tts_core的api有改动，新增了状态码`BOOKING_SEARCH_FALLBACK(112, "booking搜索兜底")`就去发了，项目主要代码是在trade_core中进行开发的，**所以trade_core中的pom文件改成了snapshot版本，但是我一直以为没有动trade_order的代码，所以trade_order的pom文件忘记改了**，**这才是一点生单就失败的最终原因**

```
[2022-11-25 10:19:01.486 QTraceId[ops_slugger_221125.101900.10.86.32.138.32130.1684558037_1]-QSpanId[1.22.1] ERROR com.qunar.flight.tts.ttsorder.util.SerializeUtils:31] [Dubbo-thread-200] [xep221125101901021] [hWQAAAEWj0Z+] [xep.trade.qunar.com] [DOMESTIC_ONE_WAY] ERROR SerializeUtils - error in Serialize ByteToObject:
java.io.InvalidObjectException: enum constant BOOKING_SEARCH_FALLBACK does not exist in class com.qunar.flight.tts.api.enums.AsyncBookingStatusEnum
```

**当时没仔细看代码抛的异常，一直在换报价生单，一直失败，做无用功**

从代码可以看到，枚举值`BOOKING_SEARCH_FALLBACK`不在`AsyncBookingStatusEnum`中，说明没有更新API，所以去取BookingTag的时候一直报错。

![image-20221125204026966](https://shan-edu.oss-cn-chengdu.aliyuncs.com/img/202211252040169.png)

图中可以看到调用栈，调用redis取bookingTag后，有一个序列化操作，就是这个时候报了错。

```java
private BookingTagNew getBookingTagFromRedis(String bookingTag) {
    Stopwatch stopwatch = Stopwatch.createStarted();
    try {
        Object obj = ttsBookingRedisService.get(bookingTag, Object.class);
        if (obj == null) {
            logger.error("get bookingTag from cache null:{}", bookingTag);
            return null;
        } else if (!(obj instanceof BookingTagNew)) {
            logger.error("bookingTag Serializable fail:{},{}", bookingTag, ToStringBuilder.reflectionToString(obj));
            return null;
        } else {
            logger.info("key:["+ bookingTag +"] get bookingTag");
            return BookingTagNew.class.cast(obj);
        }
    }finally {
        QMonitor.recordQuantile("getBookingTag_total", stopwatch.elapsed(TimeUnit.MILLISECONDS));
    }
}
```

从昨晚到今天上午一次测试，所以**出现了颇为壮观的异常次数。**所以导致取不到BookingTag

```java
[2022-11-25 10:19:01.420 QTraceId[ops_slugger_221125.101900.10.86.32.138.32130.1684558037_1]-QSpanId[1.22.1]  INFO com.qunar.flight.tts.ttsorder.service.TtsBookingRedisService:39] [] [Dubbo-thread-200] [xep221125101901021] [hWQAAAEWj0Z+] [xep.trade.qunar.com] [DOMESTIC_ONE_WAY] INFO  TtsBookingRedisService - SerializeUtils get bookingTag is null
```

```java
[2022-11-25 14:36:20.782 QTraceId[ops_slugger_221125.143359.10.86.32.138.32139.7204441898_1]-QSpanId[1.22.1] ERROR com.qunar.flight.tts.ttsorder.util.SerializeUtils:31] [Dubbo-thread-164] [xep221125143400083] [hWQAAAFSa/SV] [xep.trade.qunar.com] [DOMESTIC_ONE_WAY] ERROR SerializeUtils - error in Serialize ByteToObject:
```



```java
[2022-11-25 10:19:01.422 QTraceId[ops_slugger_221125.101900.10.86.32.138.32130.1684558037_1]-QSpanId[1.22.1] ERROR com.qunar.flight.tts.ttsorder.service.BookingTagManageService:451] [Dubbo-thread-200] [xep221125101901021] [hWQAAAEWj0Z+] [xep.trade.qunar.com] [DOMESTIC_ONE_WAY] ERROR BookingTagManageService - get bookingTag from cache null:Y4Al9QpdhWQAAAEXnqKnqcsVoU6Ce5XFzmrTiQ==
[2022-11-25 10:19:01.424 QTraceId[ops_slugger_221125.101900.10.86.32.138.32130.1684558037_1]-QSpanId[1.22.1] ERROR com.qunar.flight.tts.ttsorder.service.BookingTagManageService:381] [Dubbo-thread-200] [xep221125101901021] [hWQAAAEWj0Z+] [xep.trade.qunar.com] [DOMESTIC_ONE_WAY] ERROR BookingTagManageService - asyncBookingTag null and timeOut, asyncBookingTagKey=Y4Al9QpdhWQAAAEXnqKnqcsVoU6Ce5XFzmrTiQ==
[2022-11-25 10:19:01.440 QTraceId[ops_slugger_221125.101900.10.86.32.138.32130.1684558037_1]-QSpanId[1.22.1] ERROR com.qunar.flight.tts.ttsorder.service.impl.WebPackageOrderServiceImpl:374] [Dubbo-thread-200] [xep221125101901021] [hWQAAAEWj0Z+] [xep.trade.qunar.com] [DOMESTIC_ONE_WAY] ERROR WebPackageOrderServiceImpl - bookingTag为空
[2022-11-25 10:19:01.441 QTraceId[ops_slugger_221125.101900.10.86.32.138.32130.1684558037_1]-QSpanId[1.22.1] ERROR com.qunar.flight.tts.ttsorder.service.impl.WebPackageOrderServiceImpl:195] [Dubbo-thread-200] [xep221125101901021] [hWQAAAEWj0Z+] [xep.trade.qunar.com] [DOMESTIC_ONE_WAY] ERROR WebPackageOrderServiceImpl - 生机票订单后查询失败，结果为空
[2022-11-25 10:19:01.486 QTraceId[ops_slugger_221125.101900.10.86.32.138.32130.1684558037_1]-QSpanId[1.22.1] ERROR com.qunar.flight.tts.ttsorder.util.SerializeUtils:31] [Dubbo-thread-200] [xep221125101901021] [hWQAAAEWj0Z+] [xep.trade.qunar.com] [DOMESTIC_ONE_WAY] ERROR SerializeUtils - error in Serialize ByteToObject:
java.io.InvalidObjectException: enum constant BOOKING_SEARCH_FALLBACK does not exist in class com.qunar.flight.tts.api.enums.AsyncBookingStatusEnum
	at java.io.ObjectInputStream.readEnum(ObjectInputStream.java:1753) ~[na:1.8.0_91]
	at java.io.ObjectInputStream.readObject0(ObjectInputStream.java:1350) ~[na:1.8.0_91]
	at java.io.ObjectInputStream.defaultReadFields(ObjectInputStream.java:2018) ~[na:1.8.0_91]
	at java.io.ObjectInputStream.readSerialData(ObjectInputStream.java:1942) ~[na:1.8.0_91]
	at java.io.ObjectInputStream.readOrdinaryObject(ObjectInputStream.java:1808) ~[na:1.8.0_91]
	at java.io.ObjectInputStream.readObject0(ObjectInputStream.java:1353) ~[na:1.8.0_91]
	at java.io.ObjectInputStream.readObject(ObjectInputStream.java:373) ~[na:1.8.0_91]
	at com.qunar.flight.tts.ttsorder.util.SerializeUtils.byteToObject(SerializeUtils.java:23) ~[classes/:na]
	at com.qunar.flight.tts.ttsorder.service.TtsBookingRedisService.get(TtsBookingRedisService.java:37) [classes/:na]
	at com.qunar.flight.tts.ttsorder.service.BookingTagManageService.getBookingTagFromRedis(BookingTagManageService.java:449) [classes/:na]
	at com.qunar.flight.tts.ttsorder.service.BookingTagManageService.getBookingTagCommon(BookingTagManageService.java:415) [classes/:na]
	at com.qunar.flight.tts.ttsorder.service.BookingTagManageService.cycleGetAsynBookingTag(BookingTagManageService.java:372) [classes/:na]
	at com.qunar.flight.tts.ttsorder.service.BookingTagManageService.getAsynBookingTag(BookingTagManageService.java:361) [classes/:na]
	at com.qunar.flight.tts.ttsorder.service.qlibra.ttsDirectDispatch.TraceTradeResultEvent.fillBookingTagInfo(TraceTradeResultEvent.java:200) [classes/:na]
	at com.qunar.flight.tts.ttsorder.service.qlibra.ttsDirectDispatch.TraceTradeResultEvent.orderReportQLibra(TraceTradeResultEvent.java:113) [classes/:na]
	at com.qunar.flight.tts.ttsorder.service.qlibra.QlibraService.orderReportQLibra(QlibraService.java:51) [classes/:na]
	at com.qunar.flight.tts.ttsorder.service.route.CreateOrderHandler.createOrder(CreateOrderHandler.java:215) [classes/:na]
	at com.qunar.flight.tts.ttsorder.service.RouteOrderCreateServiceImpl.createOrder(RouteOrderCreateServiceImpl.java:127) [classes/:na]

```



##### 总结：

1. 日志、监控是很容易想到的，但是**目前没有Qconfig开关的意识，特别是对于主流程，一定要加Qconfig开关**，因此后面在判断特殊状态码的时候，做了一个Qconfig开关。
2. 经常性出现生单失败，此时就需要去看**异常日志**了，而不是换报价做无用功。
3. 在A项目写代码，出现问题的不一定是在A项目，几个项目有关联，因此出现异常的时候**几个项目都需要打开error日志去看。**
4. **发API的时候需要注意，其他的哪些项目可能引用这个API**，这次就是因为只更新的trade_core的POM文件，没更新trade_order没更新POM导致的报错
5. Qconfig的开关加错位置了，开关应该起到这样的效果：在开关关闭时，如果是新的状态码，此时应该还是走booking绝对拦截，只有在开关打开&&新状态码时才去打标



