##### JSON.parseArray

`com.qunar.flight.tts.ttsorder.service.impl.QMallBusinessImpl#convertPassengerInfo`中有用到`JSON.parseArray`，作用是将json字符串转换成的实体类列表

```java
//辅营产品信息
String[] qmallProducts = orderReq.getQmallProducts();
...
//json字符串转换成的实体类列表
List<QMallOrderInfo> qMallOrderInfos = JSON.parseArray(qmallProducts[sourcePassengerInfo.getPassengerKey() - 1], QMallOrderInfo.class);
```

这个接口是fastjson提供的，例如有个OrderVo类和字符串

```xml
[{“id”:“10001”,“name”:“苹果手机”,“price”:5000,“count”:2},{“id”:“10002”,“name”:“华为手机”,“price”:4000,“count”:2},{“id”:“10003”,“name”:"
小米手机",“price”:3000,“count”:2}]
```

```java
public class OrderVo {
    public String id;
    public String name;
    public Integer price;
    public Integer count;
} 
```

`List<OrderVo> list = JSON.parseArray(data, OrderVo.class);`可以把json字符串转换成一个一个的实体类（因为json字符串中有很多数据，因此需要parseArray解析成数组），然后存入到list集合中。