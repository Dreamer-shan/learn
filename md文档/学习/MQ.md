##### MQ

消息队列常见的使用场景，比较核心的有 3 个：**解耦**、**异步**、**流量削峰**。

##### 1.**流量削峰**

双11到了，你疯狂剁手，买了100件商品，快递几乎同时到了（请求很多），各种快递员给你打电话让你去取件，京东、顺丰、邮政、四通一达....你（某个系统）分身乏术（处理能力不够），快递堆成山了。

这时候丰巢、菜鸟驿站出现了，快递员把快递放在丰巢和菜鸟驿站，等你能忙过来了再慢慢去取件。

此时的丰巢和菜鸟驿站就相当于一个消息队列，等你根据自己处理快递（请求）的能力去消息队列取件。

##### 2.解耦：

例如 A 系统发送数据到 BCD 三个系统，通过接口调用发送。但是此时如果 E 系统也要这个数据 或者 D 系统现在不需要了，此时就需要去修改业务代码，改变业务逻辑。

![mq-1](https://shan-edu.oss-cn-chengdu.aliyuncs.com/img/202211141953835.png)

同时，在此场景中，A 系统跟其它系统严重耦合，A 系统产生一条比较关键的数据，很多系统都需要 A 系统这个数据的话，就要时刻考虑其他系统如果挂了该咋办？重发？还是把消息存起来？

如果使用 MQ，A 系统产生一条数据，发送到 MQ 中，其他系统需要数据自己订阅 MQ 的消息进行消费，若某个系统不需要该消息，直接取消订阅就可以了。这样下来，**A 系统就不需要去考虑要给谁发送数据，不需要维护这个代码，也不需要考虑其它系统是否调用成功、失败超时等情况，达到了解耦的目的。**

![mq-2](https://shan-edu.oss-cn-chengdu.aliyuncs.com/img/202211141953035.png)



项目中的例子，收到出票成功的消息后，将派单数据记录落到f_dispatcher_to_dragon_record表中，如果不使用MQ也可以，把代码都写在一起，出票成功后直接调用该方法落到表中，但是这样做耦合性就变高了。

![](https://shan-edu.oss-cn-chengdu.aliyuncs.com/img/202211051745666.png)

##### 3.异步

举个例子：A 系统接收一个请求，需要在自己本地写库，还需要在 BCD 三个系统写库，自己本地写库要 3ms，BCD 三个系统分别写库要 300ms、450ms、200ms。最终请求总延时是 3 + 300 + 450 + 200 = 953ms，接近 1s，对用户体验有一定影响。

![mq-3](https://shan-edu.oss-cn-chengdu.aliyuncs.com/img/202211051753479.png)

**使用 MQ的话**，那么 A 系统连续发送消息到 MQ 队列中，假如耗时 5ms，A 系统从接受一个请求到返回响应给用户，总时长是 3 + 5 = 8ms，最终的响应时间只跟耗时最多的处理步骤有关。

![mq-4](https://shan-edu.oss-cn-chengdu.aliyuncs.com/img/202211051755739.png)

项目中的应用：在trade_order中，生单成功后，把清理订单的标志needClearOrder置为false，然后发送异步生单消息到MQ，然后trade_core中订阅该消息进行异步生单

![image-20221105180351795](https://shan-edu.oss-cn-chengdu.aliyuncs.com/img/202211051803968.png)

异步生单需要进行一系列操作，主要目的就是为了降低用户对响应时间的感知，如果都同步一路执行下来的话，可能需要很长时间，包括我前几天排查问题的时候，有一种情况的异步流程执行有问题，有两个case，一个是异步换供应获取报价时间过长，另一个是分流的时间过长，**如果不使用异步的话**，流程组一路执行下来的话，在极端情况下，可能用户需要等几十秒才会收到响应，用户体验十分不好。

![image2022-11-4_19-5-47](https://shan-edu.oss-cn-chengdu.aliyuncs.com/img/202211051831542.png)

![image2022-11-4_19-16-25](https://shan-edu.oss-cn-chengdu.aliyuncs.com/img/202211051831243.png)

```xml
<qflow:stage name="initAsynData" desc="初始化异步数据">
    <!--初始化pnr pat缓存,有双写redis操作-->
    <qflow:component id="initAsynDataComp" desc="初始化异步数据"/>
</qflow:stage>

<qflow:stage name="disFlow" desc="分流">
    <!--还不太懂分流是做什么  似乎跟定向派单有关系-->
    <!--分流就是定向派单，定向派单往往会换供应，换供应需要先获取新的报价，然后重算一系列价格，同时统计价差，例如定向派单是盈利还是亏损-->
    <qflow:component id="disFlowComp" desc="分流"/>
</qflow:stage>

<qflow:stage name="asynOrder" desc="异步">
    <qflow:component id="prePayPolicyOversoldForbidComp" desc="预付不生编政策爆单禁售校验"/>
    <qflow:component id="prePayPolicyForbidComp" desc="预付不生编政策禁售校验"/>
    <!--三种情况需要直接兜底-->
    <qflow:component id="checkDirectRePolicyComp" desc="判断是否直接兜底"/>
    <!--检查异步流程特殊产品失败是否直接兜底的组件-->
    <qflow:component id="checkSpecialProductFailRePolicyComp" desc="判断是否是特殊产品拦截直接兜底"/>
    <!--单程 政策 异步生编，生编有几种策略  无需生编, 立即生编,下一阶段生编,生编拦截-->
    <qflow:component id="strategyNormalPnrOrderServiceComp" desc="策略生编"/>
    <qflow:component id="dishonestCheckComp" desc="校验失信人"/>
    <!--异步生单时验座和去航司取价格，然后在支付前校验时验价-->
    <!-- pnr/pata 校验  有两种pata  一种是rtPata 还有ssav pata-->
    <!-- 首先判断是否需要ssav/ta pata 然后调用对应接口获取并解析对应pata指令的结果-->
    <qflow:component id="asynPnrAndPatComp" desc="校验pnr及价格"/>
</qflow:stage>

<qflow:stage name="orderChangeSupplier" desc="异步换供应">
    <!--生单换政策兜底抉择服务-->
    <!--是否需要兜底(换供应)  一系列规则,满足规则就不兜底 ->是否能兜底(能否兜底也有一系列规则,相当于前置校验)->-->
    <qflow:component id="orderChangeSupplierDecideComp" desc="异步确认是否换供应"/>
    <!--前置校验通过,开始换供应  调报价接口获得报价列表priceDetailList(已排好序),返回priceDetailList.get(0)-->
    <qflow:component id="orderChangeSupplierFetchPriceDetailComp" desc="异步换供应获取报价"/>
    <!--换供应,又会使用到NewValidateInfo类  但是重置供应可能不成功  也可能换供应后没有变动?-->
    <qflow:component id="orderChangeSupplierResetComp" desc="异步换供应重置供应"/>
</qflow:stage>

<qflow:stage name="asynCache" desc="异步缓存">
    <!--缓存pnr和价格  redis双写-->
    <!--这里又把pnr pat缓存,有双写到redis 是做什么-->
    <qflow:component id="cachePnrAndPatComp" desc="缓存pnr及价格"/>
</qflow:stage>
```

##### 优缺点

1. 优点：**解耦**、**异步**、**削峰**。
2. 缺点：
   1. 系统可用性降低：引入的外部依赖越多，越容易挂掉，万一 MQ 挂了，可能导致整套系统都不可用，因此需要保证消息队列的高可用
   2. 系统复杂度提高：需要保证消息不被重复消费，消息丢失、消息的顺序性

RabbitMQ 的高可用性：

1. RabbitMQ 有三种模式：单机模式、普通集群模式、镜像集群模式。

   普通集群模式是指多台机器，每台机器启动1个 RabbitMQ 实例，**创建的 queue，只会放在一个 RabbitMQ 实例上**，但是每个实例都同步 queue 的元数据（元数据可以认为是 queue 的一些配置信息、队列结构等，通过元数据，可以找到 queue 所在实例）。**消费的时候，实际上如果连接到了另外一个实例，那么该实例会从 queue 所在实例上拉取数据过来**![mq-7](https://shan-edu.oss-cn-chengdu.aliyuncs.com/img/202211051908060.png)

​	

如果那个放 queue 的实例宕机了，会导致接下来其他实例就无法从那个实例拉取。这种方案**主要是提高吞吐量的**，就是说让集群中多个节点来服务某个 queue 的读写操作。

镜像集群模式（高可用性）

在镜像集群模式下，创建 queue 无论元数据还是 queue 里的消息都会**存在于多个实例上**，就是说，每个 RabbitMQ 节点都有这个 queue 的一个**完整镜像**，包含 queue 的全部数据。然后每次写消息到 queue 的时候，都会自动把**消息同步**到多个实例的 queue 上

![mq-8](https://shan-edu.oss-cn-chengdu.aliyuncs.com/img/202211051919143.png)

##### kafka

![img](https://www.17coding.info/cdn/WeChat%20Screenshot_20190325215237.png)

相关概念：

Broker是kafka实例，每个服务器上有一个或多个kafka的实例，姑且认为每个broker对应一台服务器。每个kafka集群内的broker都有一个**不重复**的编号，如图中的broker-0、broker-1等

**Topic**：逻辑上的概念，消息的主题，可以理解为消息的分类，kafka的数据就保存在topic。在每个broker上都可以创建多个topic。

**Partition**：Topic的分区，每个topic可以有多个分区，分区的作用是**做负载，提高kafka的吞吐量**。同一个topic在不同的分区的数据是不重复的，**partition的存在形式就是一个个的文件夹**！

**Replication**:**每一个分区都有多个副本**，当主分区（Leader）故障的时候会选择一个副本（Follower）上位，成为Leader。在kafka中默认副本的最大数量是10个，且副本的数量不能大于Broker的数量，**follower和leader绝对是在不同的机器**，同一机器对同一个分区也只可能存放一个副本（包括自己）

**Zookeeper**：kafka集群依赖zookeeper来保存集群的的元信息，来保证系统的可用性。

##### **发送数据**

Producer在写入数据的时候**永远是找leader**，不会直接将数据写入follower！生产者使用push模式将消息发布到Broker，消费者使用pull模式从Broker订阅消息。

![img](https://www.17coding.info/cdn/WeChat%20Screenshot_20190325215252.png)

消息写入leader后，follower是主动的去leader进行同步的，producer采用push模式将数据发布到broker，每条消息追加到分区中，顺序写入磁盘，所以保证**同一分区**内的数据是有序的

![img](https://www.17coding.info/cdn/WeChat%20Screenshot_20190325215312.png)

![img](https://shan-edu.oss-cn-chengdu.aliyuncs.com/img/202211151018571.webp)

任何发布到 Partition 的消息都会被追加到 Partition 数据文件的尾部，这样的顺序写磁盘操作让 Kafka 的效率非常高（经验证，顺序写磁盘效率比随机写内存还要高

##### **Kafka 中的底层存储设计**

假设我们现在 Kafka 集群只有一个 Broker，我们创建 2 个 Topic 名称分别为：「topic1」和「topic2」，Partition 数量分别为 1、2，那么我们的根目录下就会创建如下三个文件夹：

```
    | --topic1-0
    | --topic2-0
    | --topic2-1
```







##### 保证消息不丢失

通过ACK应答机制！在生产者向队列写入数据的时候可以设置参数来确定是否确认kafka接收到数据，这个参数可设置的值为**0**、**1**、**all**

​		0代表producer往集群发送数据不需要等到集群的返回，不确保消息发送成功。安全性最低但是效率最高。
　　1代表producer往集群发送数据只要leader应答就可以发送下一条，只确保leader发送成功。
　　all代表producer往集群发送数据需要所有的follower都完成从leader的同步才会发送下一条，确保leader发送成功和所有的副本都完成备份。安全性最高，但是效率最低。

##### 保存数据

kafka将数据保存在磁盘，单独开辟一块磁盘空间，顺序写入数据（效率比随机写入高）

![img](https://pic2.zhimg.com/80/v2-9c8de1bed82a54799c4ef2cbfeedab61_1440w.webp)

##### QMQ

meta server提供集群管理和集群发现的作用  类似zookeeper

qmq-server   实时消息服务

qmq-delay-server   延时/定时消息服务

qmq-store   存储

qmq-remoting   网络相关

qmq-client   客户端逻辑

##### QMQ存储

meta-server是qmq的元数据中心，有点类似于zookeeper的注册中心的角色。meta-server的数据存储在mysql中。



##### kafka存储模型

Kafka 和 RocketMQ 都是基于 **partition 的存储模型**，也就是每个 subject 分为一个或多个 partition，而 Server 收到消息后将其分发到某个 partition 上，而 Consumer 消费的时候是与 partition 对应的。比如，我们某个 subject a 分配了 3 个 partition(p1, p2, p3)，有 3 个消费者 (c1, c2, c3）消费该消息，则会建立 c1 - p1, c2 - p2, c3 - p3 这样的消费关系。

![image-20221114102326104](https://shan-edu.oss-cn-chengdu.aliyuncs.com/img/202211141023362.png)

如果consumer 个数 > partition 个数，则有些consumer 是空闲的

![image-20221114102428532](https://shan-edu.oss-cn-chengdu.aliyuncs.com/img/202211141024578.png)

而consumer 个数 < partition 个数，存在有的 consumer 消费的 partition 个数会比其他的 consumer 多的情况

![image-20221114102434406](https://shan-edu.oss-cn-chengdu.aliyuncs.com/img/202211141024458.png)

合适的分配策略只有是 partition 个数与 consumer 个数成倍数关系。



并且kafka在扩容/缩容时，因为这种静态的绑定的关系，还会导致 Consumer 扩容缩容麻烦。也就是使用 Kafka 这种基于 partition 的消息队列时，如果遇到**处理速度跟不上时，光简单的增加 Consumer 并不能马上提高处理能力**，需要对应的增加 partition 个数，而特别在 Kafka 里 partition 是一个比较重的资源，增加太多 parition 还需要考虑整个集群的处理能力；当高峰期过了之后，如果想缩容 Consumer 也比较麻烦，因为 partition 只能增加，不能减少。

kafka消息堆积通常的处理办法：如果是kafka消费能力不足，则可以考虑增加 topic 的 partition的个数，同时提升消费者组的消费者数量，**消费数=分区数(二者缺一不可)**

##### QMQ存储模型

![image-20221114102831508](https://shan-edu.oss-cn-chengdu.aliyuncs.com/img/202211141028551.png)

不使用基于 partition 的情况下，通过添加一层拉取的 log(pull log) 来动态映射 consumer 与 partition 的逻辑关系，这样就解决了 consumer 的动态扩容缩容问题

message log 方框上方的数字:3,6,9 表示这几条消息在 message log 中的偏移，consumer log 中方框内的数字 3,6,9,20 正对应着 message log 的偏移，表示这几个位置上的消息都是 topic1 的消息，consumer log 方框上方的 1,2,3,4 表示这几个方框在 consumer log 中的逻辑偏移。下面的 pull log 方框内的内容对应着 consumer log 的逻辑偏移，而 pull log 方框外的数字表示 pull log 的逻辑偏移。

+ message log 所有 subject 的消息进入该 log，消息的主存储

- consumer log 存储的是 message log 的索引信息
- pull log 每个 consumer 拉取消息的时候会产生 pull log，pull log记录的是拉取的消息在 consume log 中的 sequence

那么消费者就可以使用 pull log 上的 sequence 来表示消费进度，这样一来我们就解耦了 consumer 与 partition 之间的耦合关系



##### 发送消息

一共几个大的步骤：校验、存储、队列缓冲、发送消息、发送完后删除消息

生成消息

如果是map类型就把map的键值对放进去

```java
private Message buildMessage(String subject, Object data) {
    Message message = producerProvider.generateMessage(subject);

    if (data instanceof Map) {
        Map<String, String> dataMap = (Map) data;
        for (String key : dataMap.keySet()) {
            message.setProperty(key, dataMap.get(key));
        }
    } else {
        message.setData(data);
    }

    return message;
}

```

生成唯一的messageId：**时间戳+ip+pid+incrNo**

```java
public Message generateMessage(String subject, long expire, TimeUnit timeUnit) {
    return generateMessage(idGenerator.getNext(), subject, expire, timeUnit);
}
```

```java
public Message generateMessage(String messageId, String subject, long expire, TimeUnit timeUnit) {
    validateExpiredTime(expire, timeUnit);	//过期时间校验
    BaseMessage msg = new BaseMessage(messageId, subject);//messageId和Subject有长度限制 最大长度为100
    msg.setSerializer(serializer);
	
    msg.setExpiredDelay(expire, timeUnit);
    msg.setProperty(BaseMessage.keys.qmq_appCode, applications.getAppCode());
    setupEnv(msg);
    //设置软路由
    setupSoftRouter(msg);
    return msg;
}
```

```java
private void setupSoftRouter(BaseMessage msg) {
    if (routerManager.isSoftRouterEnabled()) {
        String routerId = RouterQtracer.getSoftRouterId();
        // producer 所在的环境如果支持软路由，则在qtrace的上下文中必定含有routerId(timer调用除外), 如果未发现则报错。
        if (StringUtils.isEmpty(routerId)) {
            log.warn("Not found routerId in Qtrace context, Maybe your application use multi-thread but no wrapping it.");
            routerManager.reportAbsentDetail(absentDetail(msg));
            if (routerManager.shouldThrowException()) {
                throw new RuntimeException("Not found routerId in Qtrace context, Maybe your application use multi-thread but no wrapping it.");
            }

            // 自动根据本地 router id 构造出 routing key
            // 返回软路由Id列表和基准环境id(left：软路由id，right：基准环境id)列表。
            // 字串format:#101#100#|#201#200#  ，101 201是软路由id，100 200是基准环境id。
            final String localRouterId = routerManager.localRouterId();
            final String masterEnv = routerManager.localMasterEnvId();
            if (!Strings.isEmpty(localRouterId)) {
                routerId = "#" + localRouterId + "#" + masterEnv + "#";
            }
        }
        msg.setProperty(TC_TRACING_ROUTING_KEY, routerId);
        msg.setProperty(MASTER_ENV_KEY, routerManager.localMasterEnvId());
    }
}
```



```java
private void setupSoftRouter(BaseMessage msg) {
    if (routerManager.isSoftRouterEnabled()) {
        String routerId = RouterQtracer.getSoftRouterId();
        // producer 所在的环境如果支持软路由，则在qtrace的上下文中必定含有routerId(timer调用除外), 如果未发现则报错。
        if (StringUtils.isEmpty(routerId)) {
            log.warn("Not found routerId in Qtrace context, Maybe your application use multi-thread but no wrapping it.");
            routerManager.reportAbsentDetail(absentDetail(msg));
            if (routerManager.shouldThrowException()) {
                throw new RuntimeException("Not found routerId in Qtrace context, Maybe your application use multi-thread but no wrapping it.");
            }

            // 自动根据本地 router id 构造出 routing key
            // 返回软路由Id列表和基准环境id(left：软路由id，right：基准环境id)列表。
            // 字串format:#101#100#|#201#200#  ，101 201是软路由id，100 200是基准环境id。
            final String localRouterId = routerManager.localRouterId();
            final String masterEnv = routerManager.localMasterEnvId();
            if (!Strings.isEmpty(localRouterId)) {
                routerId = "#" + localRouterId + "#" + masterEnv + "#";
            }
        }
        msg.setProperty(TC_TRACING_ROUTING_KEY, routerId);
        msg.setProperty(MASTER_ENV_KEY, routerManager.localMasterEnvId());
    }
}
```







首先去初始化，初始化会CAS把STARTED置为true,同时初始化一个RPCQueueSender队列

```java
@PostConstruct
public void init() {
    if (STARTED.compareAndSet(false, true)) {
        BrokerConfig.INSTANCE.getDataCenter2Route();//路由配置相关放到producer初始化时进行，失败直接退出
        BrokerRuleConfig.INSTANCE.getBackupRouteConf();//路由配置相关放到producer初始化时进行，失败直接退出
        routerManagerDispatcher.init(clientIdProvider.get());
    }
}
```

两个参数，一个是message，还有一个则是监听器来监听消息是否发送到了broker，注意，这个监听器**只能够监听其消息有没有发送到broker，并不能监听消息有没有发送到consumer**。在此方法之中会首先判断消息的类型：非持久消息直接发送，持久消息分为事务型和非事务型，不管是哪种，都需要将消息先落库

```java
@Override
public void sendMessage(Message message, MessageSendStateListener listener) {
    if (!STARTED.get()) {
        throw new RuntimeException("MessageProducerProvider未初始化，如果使用非Spring的方式请确认init()是否调用");
    }
    ...    
        try {
            // 初始化ProduceMessageImpl对象
            ProduceMessageImpl pm = initProduceMessage(message, listener, producerSendSpan, sendStateListenerSpan);
            QmqLogger.log(pm.getBase(), "准备发送");
            configDurable(message);
            //非持久消息，直接发送
            if (!message.isDurable()) {
                QTracer.addKVAnnotation("type", "nodurable");
                QmqLogger.log(pm.getBase(), "非持久消息");
                pm.send();
                return;
            }
            //持久消息，分为事务/非事务型消息
            if (!messageTracker.trackInTransaction(pm)) {
                pm.send();
            }
        }
```



```java
public boolean trackInTransaction(ProduceMessage message) {
    MessageStore messageStore = this.transactionProvider.messageStore();
    message.setStore(messageStore);
    if (transactionProvider.isInTransaction()) {
        QTracer.addKVAnnotation("type", "transaction");
        //开启事务
        this.transactionProvider.setTransactionListener(transactionListener);
        messageStore.beginTransaction();
        TransactionMessageHolder current = TransactionMessageHolder.init(messageStore);
        current.insertMessage(message);

        QmqLogger.log(message.getBase(), "事务持久消息");
        return true;
    } else {
        QTracer.addKVAnnotation("type", "durable");
        try {
            message.save();
        } catch (Exception e) {
            QTracer.addTimelineAnnotation("Qmq.Store.Failed");
            message.getBase().setStoreAtFailed(true);
        }
        QmqLogger.log(message.getBase(), "持久消息");
        return false;
    }
}
```



doSend（）这个方法，首先用CAS将state的状态置为1，若失败则会**抛出一个同一条消息不能被入队两次的异常**，**成功的话就会加入到QueueSender sender之中**，如果消息加入队列之中失败的话就说明队列已经满了，如果消息为**非可靠的消息，那么消息就会被丢弃**，如果为可靠数据，那么就会等待50ms重试一次，如果失败则会取消发送并且调用onFailed的方法

```java
private void doSend() {
    if (state.compareAndSet(INIT, QUEUED)) {
        tries.incrementAndGet();
        if (sendSync()) return;

        if (sender.offer(this)) {
            QmqLogger.log(getBase(), "进入发送队列.");
            QMon.monitorProducerEnqueueCount(getSubject());
        } else if (store != null) {
            QMon.monitorProducerEnqueueFailCount(getSubject());
            QmqLogger.log(getBase(), "内存发送队列已满! 此消息将暂时丢弃,等待补偿服务处理");
            failed();
        } else {
            if (ReliabilityLevel.isLow(getBase())) {
                QmqLogger.log(getBase(), "内存发送队列已满! 非可靠消息，此消息被丢弃.");
                QMon.monitorProducerEnqueueFailCount(getSubject());
                onFailed();
                return;
            }
            QmqLogger.log(getBase(), "内存发送队列已满! 此消息在用户进程阻塞,等待队列激活.");
            if (sender.offer(this, 50)) {
                QmqLogger.log(getBase(), "进入发送队列.");
                QMon.monitorProducerEnqueueCount(getSubject());
            } else {
                QmqLogger.log(getBase(), "由于无法入队,发送失败！取消发送!");
                QMon.monitorProducerEnqueueFailCount(getSubject());
                onFailed();
            }
        }

    } else {
        QMon.monitorProducerEnqueueFailCount(getSubject());
        throw new IllegalStateException("同一条消息不能被入队两次.");
    }
}
```

入队：最后是进入到QueueSender的线程池的BlockingQueue队列中，如果添加成功，线程池实现了runnable接口，然后执行run方法，批量把队列中的任务取出来并执行

```java
public boolean addItem(Item item) {
    boolean offer = this.queue.offer(item);
    if (offer) {
        this.executor.execute(this);
    }
    return offer;
}
```

```java
@PostConstruct
public void init() {
    this.queue = new LinkedBlockingQueue<>(this.queueSize);
    if (this.executor == null) {
        this.executor = new ThreadPoolExecutor(1, threads, 1L, TimeUnit.MINUTES,new ArrayBlockingQueue<Runnable>(1), new NamedThreadFactory("batch-" + name + "-task", true));
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
    }
}
```



qunar.tc.qmq.broker.impl.BrokerServiceImpl#getClusterBySubject

```java
//获取broker集群的信息、
BrokerClusterInfo cluster = brokerService.getClusterBySubject(clientType, subject);

ClusterFuture future = clusterMap.get(key);
MetaInfoService.MetaInfoRequestParam requestParam = MetaInfoService.buildRequestParam(clientType, subject, group, appCode);
if (future == null) {
    future = request(requestParam, false);
} else {
    //心跳机制，通过mateserver跟broker通信
    metaInfoService.tryAddRequest(requestParam);
}

//同时MetaInfoService实现了runnable接口，在run方法中执行requestWrapper方法，最后发送消息
// 如果有在线的broker就设置消息类型为ONLINE，否则设置为HEARTBEAT，最后发送请求client.sendRequest(request);
if (param.getRequestType() == null) {
    if (!hadOnline(param)) {
        request.setRequestType(ClientRequestType.ONLINE);
    } else {
        request.setRequestType(ClientRequestType.HEARTBEAT);
    }
} else {
    request.setRequestType(param.getRequestType());
}


//负载均衡
BrokerGroupInfo target = brokerLoadBalance.loadBalance(cluster, lastSentBroker);

//发送消息，先把message组装成数据报，然后发送
Datagram response = doSend(target, messages);
//正式发送数据报
Datagram result = producerClient.sendMessage(target, datagram);

//通过NettyProducerClient异步发送消息，只跟master交互，最后把数据写到netty的缓冲区，再将netty缓冲区中的数据写到Socket缓冲区中，其实是把组装好的数据报Datagram发送出去了，返回也是返回的Datagram


Datagram response = doSend(target, messages);
RemotingHeader responseHeader = response.getHeader();
int code = responseHeader.getCode();
switch (code) {
    case CommandCode.SUCCESS:
        return process(target, response);
    case CommandCode.BROKER_REJECT:
        handleSendReject(target);
        throw new BrokerRejectException("");
    default:
        throw new RemoteException();
}
```







MQ幂等性：

幂等操作的特点是其任意多次执行所产生的影响均与一次执行的影响相同

![img](https://shan-edu.oss-cn-chengdu.aliyuncs.com/img/202211141953995.webp)

场景：购买会员卡，上游支付系统负责给用户扣款，下游系统负责给用户发卡，通过MQ异步通知。不管是上半场的ACK丢失，导致MQ收到重复的消息，还是下半场ACK丢失，导致购卡系统收到重复的购卡通知，都可能出现，上游扣了一次钱，下游发了多张卡。

MQ幂等设计：

上半场1-3：

 1. MQ-client将消息发给服务端MQ-server

 2. MQ-server存储消息

 3. MQ-server返回ack给客户端

    如果3丢失，发送端MQ-client超时后会重发消息，可能导致服务端MQ-server收到重复消息

    方案：**MQ系统内部要生成一个msg-id**，作为去重和幂等的依据，这个消息的特点是全局唯一，且具备业务无关性，

下半场4-6：

4. 服务端MQ-server将消息发给接收端MQ-client

5. 接收端MQ-client回ACK给服务端

6. 服务端MQ-server将落地消息删除

   如果5丢失，服务端MQ-server超时后会重发消息，可能导致MQ-client收到重复的消息，

7. 为了保证业务幂等性**，业务消息体中，也要有一个id**，作为去重和幂等的依据，这个业务ID的特性是：

   （1）对于同一个业务场景，全局唯一

   （2）由业务消息发送方生成，业务相关

   （3）由业务消息消费方负责判重，以保证幂等

   可以使用redis或者mysql唯一索引来判断是否重复。

   redis：把消费数据记录在 redis 中，下次消费时先到 redis 中查看是否存在该消息，存在则表示消息已经消费过，直接丢弃消息

   mysql：数据消费后插入到数据库中，使用数据库的唯一性约束防止重复消费。每次消费直接尝试插入数据，如果提示唯一性字段重复，则直接丢失消息





