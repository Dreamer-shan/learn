##### trade_oreder与trade_core交互

trade_order生子单的时候，最后调用了`com.qunar.flight.tts.ttsorder.service.IOrderBusiness#createOrder`，`IOrderBusiness`接口有很多实现类，`NFlightOrderBusinessImpl`是其中一个，**这个类进行机票生单**

```java
orderCreateResponse = newCreateOrder.createOrder((SingleOrderCreateRequest) orderCreateRequest);
```

实际上就是调用了com.qunar.flight.tts.api.order.api.ISingleOrderCreateService#createOrder方法，

`SingleCreateOrderServiceImpl`实现了`ISingleOrderCreateService`接口，先进行参数构建，然后执行生单流程。

```java
log.info("开始执行生单流程 >>>");
FlowResult<CreateOrderResult> flowResult = bizFlowExecutor
.execute(receiveOrder, createOrderBean, BizFlowStage.order, (data, orderResult) -> orderResult.isSuccess());
```

生单流程比较复杂，以单程运价直连为例，分为9大步骤：落单、初始化异步数据、分流、异步、异步换供应、异步缓存、缓存pnr及价格、支付前校验、支付前换供应

![image-20221017120244436](https://shan-edu.oss-cn-chengdu.aliyuncs.com/img/image-20221017120244436.png)

`com.qunar.flight.trade.core.service.impl.SingleCreateOrderServiceImpl#createOrder`执行了**同步落单**流程

```java
FlowResult<CreateOrderResult> flowResult = bizFlowExecutor
.execute(receiveOrder, createOrderBean, BizFlowStage.order, (data, orderResult) -> orderResult.isSuccess());
```

trade_order项目中最后发送异步生单消息`sendAsynOrderMqService.sendAsynOrderService(flightOrderNo);`，trade_core的`com.qunar.flight.trade.core.qmq.listener.OrderAsynListener#onMessage`接受消息后，执行异步处理流程`asyncOrderRet = iAsynOrderMessageHandler.handleAsynOrderMessage(receiveOrder);`

```java
/**
     * 处理异步消息
     * 分五个流程:
     * 1.初始化数据
     * 2.分流
     * 3.异步pnr 校验
     * 4.兜底
     * 5.异步缓存cache
     */
@Override
public AsyncOrderRet handleAsynOrderMessage(ReceiveOrder receiveOrder) {
    FlowResult<AsyncOrderRet> flowResult;
    AsyncOrderRet bizResult = null;

    String lockKey = String.format(Constants.ASYN_ORDER_LOCK, receiveOrder.getOrderNo());
    boolean lock = redis3Service.tryLock(lockKey, 0, 10, TimeUnit.SECONDS);
    if (!lock) {
        log.error("订单已经在进行异步处理，不需要再次执行");
        QMonitor.recordOne("asyn_order_lock_fail");
        return AsyncOrderRet.builder().asynValidateRet(AsynValidateResultEnum.IGNORE).build();
    }

    if (isHandleFinish(receiveOrder.getOrderNo())) {
        log.error("订单已经完成异步处理，不需要再次执行");
        QMonitor.recordOne("asyn_order_has_handle_finish");
        return AsyncOrderRet.builder().asynValidateRet(AsynValidateResultEnum.IGNORE).build();
    }

    try {

        // ******************** 初始化数据流程开始 ********************
        // 初始化数据
        initDataBizExecutor.execute(receiveOrder, receiveOrder, BizFlowStage.initAsynData);
        // ******************** 初始化数据流程结束 ********************

        // ******************** 分流流程开始 ********************
        // 路由并执行分流流程
        FlowResult<DisFlowRet> disFlowResult = disFlowBizExecutor.execute(receiveOrder, receiveOrder, BizFlowStage.disFlow);

        log.info("分流流程结果: {}", JacksonUtil.serialize(disFlowResult));

        if (!needIgnore(disFlowResult) && disFlowFailed(disFlowResult)) {
            log.error("分流换供应失败, 流程终止");
            QMonitor.recordOne(QMonitorConts.NEED_DIS_FLOW_BUT_RESET_SUPPLIER_FAIL);
            return AsyncOrderRet.builder()
                .asynValidateRet(AsynValidateResultEnum.FAIL)
                .interceptType(InterceptTypeEnum.DIS_FLOW_CHANGE_SUPPLIER_FAIL)
                .finalErrMsg("分流换供应失败, 流程终止")
                .build();
        }
        resetMeanOrder(disFlowResult, receiveOrder);
        // ******************** 分流流程结束 ********************

        // ******************** 异步流程开始 ********************
        // 路由并执行异步流程
        flowResult = asynBizExecutor.execute(receiveOrder, receiveOrder, BizFlowStage.asynOrder);
        log.info("异步流程结果: {}", JacksonUtil.serialize(flowResult));
        bizResult = flowResult.getBizResult();
        //推送异步流程即结果到Qlibra
        qLibraPushService.asynOrderReportQLibra(bizResult, receiveOrder);
        // 业务结果监控记录
        monitorAsynResult(flowResult, receiveOrder);

        // 刷CM
        refreshCMService.refreshCMOnAsyncOrderFailed(flowResult, receiveOrder);
        // ******************** 异步流程结束 ********************


        // ******************** 兜底流程开始 ********************
        if (CommonUtil.failFlowResult(flowResult) || OrderUtil.isMockChangeSupplierAsyn(receiveOrder)){
            interceptLogAndForbidSaleMsgNew(bizResult, receiveOrder);
            addAsynForbidChangeSupplerMsg(receiveOrder, bizResult);
            // 防止重复打印拦截日志
            bizResult.setNeedInterceptLog(false);
            log.warn("异步流程失败,执行异步兜底流程");
            AppContext.setChangeSupplier(Constants.ORDER_CHANGE_SUPPLIER);
            // 路由并执行换供应
            FlowResult<ChangeSupplierRet> supplierFlowResult = orderChangeSupplierBizExecutor.execute(receiveOrder,
                                                                                                      buildChangeSupplierCondition(flowResult, receiveOrder),
                                                                                                      BizFlowStage.orderChangeSupplier);
            log.info("异步兜底换供应结果: {}", JacksonUtil.serialize(supplierFlowResult));

            // 配了兜底流程 并且需要兜底重走异步流程
            if (fallBack.configChangeSupplier(supplierFlowResult) && fallBack.resetChangeSupplierSuccess(supplierFlowResult)){
                log.info("异步兜底换供应成功,执行兜底异步流程");
                AsyncOrderRet asyncOrderRet = fallBack.orderChangeSupplierHandle(receiveOrder);
                // 兜底换供应结果覆盖
                bizResult = asyncOrderRet == null ? bizResult : asyncOrderRet;
            }

            // 没真实兜底, 清除兜底context
            if (!fallBack.resetChangeSupplierSuccess(supplierFlowResult)){
                AppContext.removeChangeSupplier();
            }
        }
        // ******************** 兜底流程结束 ********************

        log.info("异步流程结果, 最终业务结果: {}", JacksonUtil.serialize(bizResult));

        // 有流程执行结果 入库
        bizResult = updateOrder(bizResult, receiveOrder);
        return bizResult;
    } catch (Exception ex) {
        log.error("执行异步流程异常", ex);
        QMonitor.recordOne(QMonitorConts.ASYNC_ORDER_BIZ_FLOW_EXCEPTION);
        throw new FlowExecuteException(ex.getMessage(), ex);
    } finally {
        log.info("开始 路由并缓存异步结果");
        try {
            cacheBizExecutor.execute(
                receiveOrder, buildCacheOrderBean(receiveOrder, bizResult),
                BizFlowStage.asynCache, (data, result) -> true
            );
        } finally {
            redis3Service.releaseLock(lockKey);
        }
    }
}
```

异步缓存这个步骤将异步生单的结果存到redis，`pidOriginStatus.put(PidInterfaceCode.CREATE_PNR.code, data.getPnrRet().getPnrResult().getOriginPidStatus());`

```java
    @Override
    public Boolean execute(CacheOrderBean data, FlowContext flowContext) {
        ReceiveOrder order = data.getOrder();
        SecondaryLogger.info(CachePnrAndPatService.class, "异步结果:{}", JacksonUtil.serialize(data.getPnrAndPatResult()));

        // 缓存
        return cachePnrAndPatResult(order, data);
    }

    @Override
    public boolean isNext(CacheOrderBean data, Boolean result) {
        return result;
    }

    /**
     * 缓存 结果
     *
     * @param order         订单
     * @param data pnr&pata 结果
     */
    private boolean cachePnrAndPatResult(ReceiveOrder order, CacheOrderBean data) {
        PnrAndPatResult pnrAndPatResult = data.getPnrAndPatResult();
        PnrPataResult pnrPatResult = buildCacheResult(pnrAndPatResult);
        if (data.getPnrRet() != null && data.getPnrRet().getPnrResult() != null) {
            pnrPatResult.setPnrFailOriResponse(data.getPnrRet().getPnrResult().getErrMsg());
            pnrPatResult.setPnrFailOriginalCode(data.getPnrRet().getPnrResult().getCode());
            if(data.getPnrRet().getPnrResult().getOriginPidStatus() != null){
                Map<String, Integer> pidOriginStatus = pnrPatResult.getPidOriginStatus();
                if(pidOriginStatus == null){
                    pidOriginStatus = Maps.newHashMap();
                    pnrPatResult.setPidOriginStatus(pidOriginStatus);
                }
                pidOriginStatus.put(PidInterfaceCode.CREATE_PNR.code, data.getPnrRet().getPnrResult().getOriginPidStatus());
            }
        }
        // 打印拦截日志
        printSuccessInterceptLog(order, pnrPatResult);
        return cachePnrAndPatResult(order.getOrderNo(), pnrPatResult);
    }
```

最后执行支付前校验的流程

`com.qunar.flight.trade.core.service.impl.PayValidServiceImpl#payValidator`中执行支付前校验

```java
FlowResult<CheckResult> flowResult = bizExecutor.execute(order, payValidatorBean, BizFlowStage.payValidate,
                (data, result) -> payValidateSuccess(result));
```

在doFallBack中执行支付前校验换供应的流程

```java
//支付前校验换供应
checkResult = doFallBack(receiveOrder, checkResult, attachmentParam);
```

