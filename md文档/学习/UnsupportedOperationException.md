##### UnsupportedOperationException

```java
List<PriceDetail> priceDetailList = priceDetailsFactory.getPriceDetailsImplWithFilter()
    .getFilteredPriceDetailList(receiveOrder, data.getType());
```

在使用evaluate赋值的时候出现报错，java.lang.UnsupportedOperationException，自己写demo试了一下，发现该List是由数组转换而成

![image-20221115161752605](https://shan-edu.oss-cn-chengdu.aliyuncs.com/img/202211151617733.png)

 原因：
	调用Arrays.asList()生产的List的add和remove方法出现异常，这是因为Arrays.asList()**返回的是Arrays的内部类ArrayList而不是java.util.ArrayList**。Arrays的内部类ArrayList都是**继承了AbstractList，AbstractList中的add、set、remove方法都默认throw new UnsupportedOperationException()**

![image-20221115162032085](https://shan-edu.oss-cn-chengdu.aliyuncs.com/img/202211151620130.png)

在AbstractList中，add、remove都会直接报异常

![image-20221115162135132](https://shan-edu.oss-cn-chengdu.aliyuncs.com/img/202211151621169.png)

解决办法：

```java
List<Integer> list = Arrays.asList(1);
List arrList = new ArrayList(list);

arrList.add(2);
System.out.println(arrList);
```

