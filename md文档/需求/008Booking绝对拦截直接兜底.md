##### 背景：

之前的booking绝对拦截是用户点击该报价的时候直接提示失败，因为这个报价是没有太大意义的，拿这个报价去生编很大概率会失败。

在降booking绝对拦截的需求中，如果是booking绝对拦截，报价把同步bookingTag复制到异步BookingTag中，交易拿到这个报价后，去进行生编，生编果很可能会失败，因此触发异步失败的兜底流程，换个报价后再生编验价，即使后面发生了变价之类的情况，但是起码用户侧不会感知到这个绝对拦截的弹窗了。



##### 现在的问题：某类Booking绝对拦截的代码不生编不pata

某个本该是Booking绝对拦截的报价，刚好无需生编，也不需要pata验价，因此可以通过支付前校验，用户成功支付了，但是支付后拿这个报价去生编，还是大概率生编失败，但是用户已经支付，所以可能需要给用户升舱，导致亏损。

我的理解：大部分的报价都需要生编和pata，上一个需求中打上了非恶意的标识是为了走支付前生编，而这类报价刚好是配置的就是不需要生编和pata。

之前在receiveOrder中打上了非恶意标识，但现在已经不关心这种绝对拦截的报价是不是恶意用户了，就算是恶意用户也走支付后生编，支付后生编升舱的亏损往往是因为紧俏造成的，如果不紧俏，支付后生编也没问题，如果紧俏的话，在做紧俏生编需求中已经部分解决，紧俏>恶意，走支付前生编。



##### 方案：

原来是在receiveOrder的ext中打上非恶意标识，在判恶意的时候取ext

```java
ExtUtils.setExt(receiveOrder, ExtKey.IS_NOT_MEAN_ORDER,Boolean.TRUE.toString());

String isNotMeanOrder = ExtUtils.getValueFromOrderExt(meanOrderRequest.getReceiveOrder(), ExtKey.IS_NOT_MEAN_ORDER);

if(isNotMeanOrder){
    立即生编
}

```

现在

1.在purchaseOrder的ext中打上booking搜索兜底标识BOOKING_SEARCH_FALLBACK

```java
ExtUtils.setExt(purchaseOrder, ExtKey.BOOKING_SEARCH_FALLBACK,Boolean.TRUE.toString());
String bookingSearchFallBack = ExtUtils.getValueFromOrderExt(meanOrderRequest.getReceiveOrder().getpurchaseOrder(), ExtKey.BOOKING_SEARCH_FALLBACK);
if(bookingSearchFallBack){
    立即生编
}
```

2.在判断是否直接兜底组件中判断purchaseOrder的ext有无兜底标识BOOKING_SEARCH_FALLBACK

```xml
<qflow:component id="checkDirectRePolicyComp" desc="判断是否直接兜底"/>
```

```java
if (bookingSearchFallback){
    log.info("判断是否直接兜底:booking绝对拦截的报价,需要直接兜底");
    QMonitor.recordOne("check_direct_re_policy_booking_absolute_intercept");
    return AsyncOrderRet.builder().asynValidateRet(AsynValidateResultEnum.IGNORE).build();
}
```

```xml
<qflow:component id="checkSpecialProductFailRePolicyComp" desc="判断是否是特殊产品拦截直接兜底"/>
这个组件中应该也是一样的逻辑
```

3.在异步确认是否换供应组件中，needChangeSupplier()方法用来判断是否换供应，在方法中新增这次走直接兜底的条件，如果满足，返回true

```xml
<qflow:component id="orderChangeSupplierDecideComp" desc="异步确认是否换供应"/>
```

4.在流程异步换供应重置供应中，运价直连和政策重置供应的公共部分中，清除BOOKING_SEARCH_FALLBACK的标识，否则这个标识又会带到下一次

```java
<qflow:component id="orderChangeSupplierResetComp" desc="异步换供应重置供应"/>
```



```java
if (!MapUtils.isEmpty(extAll)) {
    extAll.remove(ExtKey.POLICYCODE);
    ...
    extAll.remove(ExtKey.BOOKING_SEARCH_FALLBACK);
}
```





疑问点：是不是receiveOder是ext     purchaseOder是ExtendMap    

**不是**  purchaseOder和receiveOder里面都有extMap和ExtendMap，ExtendMap是extMap的一个key

```java
// 特殊扩展,json格式,存放订单属性标记
EXTEND_MAP("extendmap")
```

相当于一个大的extMap里面嵌套了一个extendMap