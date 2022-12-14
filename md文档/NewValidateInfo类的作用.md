##### NewValidateInfo类的作用

##### 1.NewValidateInfo的作用是什么？为什么要用这个字段？

各种原因会导致异步生单失败，例如生编、支付前校验的验座验价等等，但是异步生单失败了不能直接提示用户，否则用户体验很差，所以此时需要兜底逻辑，也就是换供应，换供应需要请求报价的接口，重搜报价，用新的报价重新去生单。

换供应后，代理商域名newSite以及一系列价格就需要更新，同时要计算换完供应后这单是盈利还是亏损进行价差统计

NewValidateInfo实质上的作用：**只要换报价**，就需要用到NewValidateInfo类，一系列价格就需要更新，分流是换报价的一种，各种原因会导致异步生单失败，此时也需要换报价。

```java
//兜底政策对应的代理商域名
private String newSite;
//兜底政策的价格信息(派单使用)
private CabinPrice cabinPrice;
// F单底价-N单用户支付价(订单的总收益)
private BigDecimal diffPrice;
//存储时机 参考 RePolicySource
private String stage;
//换政策类型 参考 PurchaseType
private String type;
//【供应单政策价-供应单佣金】- AD(结算价) （走定向派单带来的收益）
private BigDecimal addProfit;
//新政策-原N单底价
private BigDecimal diffPolicyPrice;
//使用的追加金额
private BigDecimal priceChaseAmount;
//N单政策价
private BigDecimal nBarePrice;
//N单佣金
private BigDecimal nCommissionAmount;
//F单佣金
private BigDecimal fCommissionAmount;
//定向派单时是否校验过用户乘客标签，便于做监控统计分析
private Boolean validateOrderUserLabel;
//是否是负向派单
private Boolean lossDisFlow;
```

type异步兜底类型    供应类型

```java
USER_GUID("user_guid", "用户指定供应单"),
FAIL_RE("fail_re", "失败拦截"),
DIS_FLOW_RE("dis_flow_re", "分流拦截"),
REDUCTION_DIS_FLOW_RE("reduction_order_dispatch_re", "立减分流拦截"),
SOLO_CHILD_RE("solo_child_re", "单独儿童票"),
INFANT_RE("infant_re", "婴儿票"),
DELAY_DISPATCH("delay_dispatch", "延迟派单"),
```

stage兜底时机   供应单采购时机

```java
BOOK_ORDER(0, "book_order", "booking"),
CREATE_ORDER(1, "create_order", "生单"),
PAY_VALIDATE(2, "pay_validate", "支付前校验"),
DISPATCH_ORDER(3,"dispatch_order","派单"),
```

NewValidateInfo跟换供应有很大关系，换供应主要在：**分流(定向派单)、异步兜底、支付前校验三个阶段**，因此NewValidateInfo的赋值也在这几个阶段，取值主要在支付前校验阶段。

```xml
<qflow:component id="disFlowComp" desc="分流"/>
<qflow:component id="orderChangeSupplierResetComp" desc="异步换供应重置供应"/>
<qflow:component id="payChangeSupplierResetComp" desc="支付前校验换供应重置供应"/>
```

##### 分流` com.qunar.flight.trade.core.component.orderasyn.impl.DisFlowServiceImpl`

1. 调用报价的接口，获取可用报价列表priceDetailList，拿不到报价, 可以认为不需要分流

   ```java
   List<PriceDetail> priceDetailList = iPriceDetails.getFilteredPriceDetailList(order, type);
   ```

2. 是否索要行程单，入参priceDetailList

   ```java
   String xcd = ExtendMapUtil.getOrderExtendMapValue(order.getPurchaseOrder(), ExtendMapEnum.XCD.KEY)
   ```

3. 用户乘客标签校验

   ```java
   priceDetailList = filterByUserLabel(priceDetailList,order,dispatchInfo)
   ```

   **是否索要行程单、用户乘客标签校验这两个步骤都是使用stream流对报价列表`priceDetailList`进行过滤操作**

4. 如果可用报价存在多个，由于booking阶段已对优先级进行排序，过滤排在后面的报价，对priceDetailList的后面的元素设置失败原因。

```java
if (priceDetailList.size() > 1) {
    QMonitor.recordOne("dis_flow_price_exist_multiple");
    for (int index = 1; index < priceDetailList.size(); index++) {
        PriceFilterCause cause = new PriceFilterCause(ReasonType.TRADE_FILTER.getType(), ReasonDetail.PRICE_PRIORITY_LOW.getType(), getPricePriorityLowRemark(priceDetailList.get(index)), priceDetailList.get(index).getDomain(),
                                                      PriceFilterWithDisFlow.buildPriceExt(priceDetailList.get(index)));
        recordDisFlowService.addFailReason(cause, dispatchInfo);
    }
}
```

5. 换供应，返回一个布尔值标识换供应是否成功，入参：用户订单ReceiveOrder，priceDetailList[0] ，type异步兜底类型  stage兜底时机`success = rePolicy.reSetReceiveOrder(order, priceDetailList.get(Constants.ZERO), type, stage);`

进入`com.qunar.flight.trade.core.component.repolicy.impl.RePolicyImpl#reSetReceiveOrder`方法后，老供应单如果是政策类型，`cancelPnr(receiveOrder)`取消pnr，该方法中实际上是将pnr置为null，`receiveOrder.getPurchaseOrder().setPnr(null);`

```javascript
/** 政策需要 回滚pnr,内部有异常处理，失败不影响主流程 */
if (!isAfare) {
    cancelPnr(receiveOrder);
}
```

1. 根据政策报价/运价直连报价更新供应单信息

```java
/** 更新供应单信息 */
if (CommonUtil.isPolicyPrice(priceDetail)) {
    /** 政策报价 */
    reSetPurchaseOrderForPolicy(receiveOrder, priceDetail, type, stage);
} else if (CommonUtil.isAfarePrice(priceDetail)){
    /** 运价直连报价 */
    reSetPurchaseOrderForAfare(receiveOrder, priceDetail, type, stage);
}
```

2. 兜底生编成功后处理：更新政策价格信息，插入兜底标识，`ExtUtils.addNewValidateInfo(receiveOrder, priceDetail, priceMap)`

##### 从什么地方赋值的

3. 在`addNewValidateInfo`方法中，**就new了对象NewValidateInfo，并为其赋值，最后放到用户订单对象**`purchaseOrder`的`ExtMap`中，键是枚举类`NEW_VALIDATE_INFO`中的字符串`new_validate_info`，值是序列化后的`validateInfo`对象

```java
public static void addNewValidateInfo(ReceiveOrder receiveOrder, PriceDetail priceDetail, Map<String, String> priceMap) {
    //供应单
    PurchaseOrder purchaseOrder = receiveOrder.getPurchaseOrder();
    //代理商域名
    String domain = purchaseOrder.getAgent().getDomain();
    //政策信息
    PolicyInfo policyInfo = purchaseOrder.getPolicyInfo();
    //仓位价格
    CabinPrice cabinPrice = priceDetail.getCabinPrice();
    //F单佣金
    String noCommissionToPrice = priceDetail.getExtParams().get(NO_COMMISSION_TO_PRICE);
    //N单佣金
    String noCommissionOriginPrice = priceDetail.getExtParams().get(NO_COMMISSION_ORIGIN_PRICE);
    //已使用的追价阈值
    BigDecimal alreadyPriceChaseAmount = PriceUtils.createBigDecimal(priceDetail.getExtParams().get(ALREADY_PRICE_CHASE_AMOUNT));
    //派单原价【540.0】，派单去佣金【539.0】，派单需要减去使用自动追价【0】，原单【519.9】，原单去佣金【519.9】，佣金配置需要去佣金结算:false
    log.info("派单原价【{}】，派单去佣金【{}】，派单需要减去使用自动追价【{}】，原单【{}】，原单去佣金【{}】，佣金配置需要去佣金结算:{}",
             cabinPrice.getAdultBarePrice(), noCommissionToPrice, alreadyPriceChaseAmount, receiveOrder.getPolicyInfo().getBarePrice(), noCommissionOriginPrice, priceDetail.getExtParams().get(NEW_BARE_PRICE_NEED_NO_COMMISSION));
    if (policyInfo != null) {
        BigDecimal toPrice = new BigDecimal(0);
        if (MapUtils.isNotEmpty(priceDetail.getExtParams())) {
            String toPriceStr = noCommissionToPrice;
            toPrice = PriceUtils.createBigDecimal(toPriceStr);
        }
        if (toPrice.compareTo(new BigDecimal(0))==0) {
            toPrice = PriceUtils.createBigDecimal(cabinPrice.getAdultBarePrice());
        }
        //toPrice-alreadyPriceChaseAmount
        toPrice =toPrice.subtract(alreadyPriceChaseAmount) ;
        //N单政策价
        BigDecimal saleBarePrice =PriceUtils.createBigDecimal(receiveOrder.getPolicyInfo().getBarePrice());
        //价差   toPrice-政策价   F单底价-N单用户支付价(订单的总收益)
        BigDecimal diffPrice = toPrice.subtract(saleBarePrice) ;
        //构造收益   【供应单政策价-供应单佣金】- AD(结算价) （走定向派单带来的收益）    F单-N单
        BigDecimal addProfit = buildAddProfit(priceDetail.getExtParams(), priceMap);
        //计算新政策与原N单底价的价差  新政策-原N单底价   新政策F单-原N单底价
        BigDecimal diffPolicyPrice = getDiffPolicyPrice(cabinPrice, priceDetail.getExtParams(), receiveOrder);
        NewValidateInfo validateInfo = new NewValidateInfo(domain, cabinPrice, diffPrice, purchaseOrder.getPurchaseStage().code,
                                                           purchaseOrder.getPurchaseType().code, addProfit, diffPolicyPrice);
        //使用的追加金额
        validateInfo.setPriceChaseAmount(alreadyPriceChaseAmount);
        //N单政策价
        validateInfo.setNBarePrice(saleBarePrice);
        //N单佣金
  validateInfo.setNCommissionAmount(saleBarePrice.subtract(PriceUtils.createBigDecimal(noCommissionOriginPrice)));
        //F单佣金
  validateInfo.setFCommissionAmount(PriceUtils.createBigDecimal(cabinPrice.getAdultBarePrice()).subtract(PriceUtils.createBigDecimal(noCommissionToPrice)));
        if(priceDetail.isValidateUserLabel()){
            validateInfo.setValidateOrderUserLabel(true);
        }
        if(priceDetail.isLossDisFlow()){
            validateInfo.setLossDisFlow(true);
        }
        //purchaseOrder订单   key new_validate_info   value 换政策进行支付前校验成功后, 保存的一些信息
        ExtUtils.setExt(purchaseOrder, ExtKey.NEW_VALIDATE_INFO, JacksonUtil.serialize(validateInfo));
    }
}
```

##### 计算逻辑：

F单佣金toPrice=toPrice-已使用的追价阈值alreadyPriceChaseAmount

N单政策价（N单用户支付价）saleBarePrice

F单底价-N单用户支付价(订单的总收益)   价差diffPrice=F单佣金toPrice-N单政策价saleBarePrice    

新政策F单-原N单底价  diffPolicyPrice = 新政策F单cabinPrice-原N单底价priceDetail.getExtParams()

构造收益  【供应单政策价-供应单佣金】- AD(结算价) （走定向派单带来的收益）

buildAddProfit方法传入priceDetail.getExtParams()和priceMap，根据needNoCommission决定走配置代理还是自营/期间代理。

配置的代理商：F单价格取priceMap的"AD"键对应的字符串去构造BigDecimal对象

自营或者配置的旗舰店：F单价格取priceDetail.getExtParams()的"noCommissionToPrice"键对应的字符串去构造BigDecimal对象

如果priceDetail.getExtParams()包含已使用的追价阈值"alreadyPriceChaseAmount"，**则F单佣金-已使用的追价阈值，最后返回N单-F单?**

```java
private static BigDecimal buildAddProfit(Map<String, String> extMap, Map<String, String> priceMap) {
    boolean needNoCommission = BooleanUtils.toBoolean(extMap.get(NEW_BARE_PRICE_NEED_NO_COMMISSION));
    String toPriceStr;
    if (needNoCommission) {
        //配置的代理商走新逻辑【原供应单政策价-供应单佣金】- AD(结算价)（避免新政策价已经低于原政策价-佣金，此时给代理商结算的价不含佣金）
        toPriceStr = priceMap.get(Constants.ADULT_BARE_PRICE_TAG_FOR_PAIDAN);
    } else {
        //自营或者配置的旗舰店走原逻辑 【原供应单政策底价-供应单佣金】-【新供应单政策底价-供应单佣金】
        toPriceStr = extMap.get(NO_COMMISSION_TO_PRICE);
    }
    String originalPriceStr = extMap.get(NO_COMMISSION_ORIGIN_PRICE);
    //N单佣金
    BigDecimal originalPrice = PriceUtils.createBigDecimal(originalPriceStr);
    //F单佣金
    BigDecimal toPrice = PriceUtils.createBigDecimal(toPriceStr);
    if(extMap.containsKey(ALREADY_PRICE_CHASE_AMOUNT)){
        QMonitor.recordOne("buildAddProfit_user_auto_price_chase_amount");
        //F单佣金-已使用的追价阈值
        toPrice =toPrice.subtract(PriceUtils.createBigDecimal(extMap.get(ALREADY_PRICE_CHASE_AMOUNT)));
    }
    if (originalPrice.compareTo(PriceUtils.ZERO) <=0 || toPrice.compareTo(PriceUtils.ZERO)<=0) {
        //build_add_profit_error, extMap:{ }，AD:782.0, originalPrice :0, toPrice:0
        log.info("build_add_profit_error, extMap:{}，AD:{}, originalPrice :{}, toPrice:{}", JacksonSupport.toJson(extMap), priceMap.get(Constants.ADULT_BARE_PRICE_TAG_FOR_PAIDAN), originalPrice, toPrice);
        QMonitor.recordOne("build_add_profit_error");
        return PriceUtils.ZERO;
    }
    //N单-F单?
    return originalPrice .subtract(toPrice) ;
}
```

