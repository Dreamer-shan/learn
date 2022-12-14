

#### ExecutorService

Java.util.concurrent.ExecutorService接口代表一种异步执行机制，它能够在后台执行任务。**ExecutorService接口实现了Executor接口**？

```java
public interface ExecutorService extends Executor
```

ExecutorService与thread pool是非常相似的。ThreadPoolExecutor继承了AbstractExecutorService，而AbstractExecutorService实现了ExecutorService接口

```java
public class ThreadPoolExecutor extends AbstractExecutorService

public abstract class AbstractExecutorService implements ExecutorService

public interface ExecutorService extends Executor
```

简单的demo：

1. 通过newFixedThreadPool()工厂方法创建一个ExecutorService的实例。这个方法创建了一个拥有10个线程的线程池来执行任务。

2. Runnable接口的匿名实现类作为参数被传递给execute()方法。Runable将会被ExecutorService中的一个线程来执行

下面的图片说明了一个线程委托一个任务给ExecutorService进行异步执行

![A thread delegating a task to an ExecutorService for asynchronous execution.](https://jenkov.com/images/java-concurrency-utils/executor-service.png)

一旦线程委托任务给ExecutorService，该线程会独立于提交任务的线程继续执行自己之后的操作，而ExecutorService并发地执行该线程提交的任务。如图，operation1和operation2提交给ExecutorService，ExecutorService并发执行这两个task，主线程直接执行operation3；

如下代码所示，让线程池中的线程睡5s，主线程的代码会先执行，输出hello world，5s后，线程池中的任务睡醒了开始执行。

```java
ExecutorService executorService = Executors.newFixedThreadPool(10);
executorService.execute(new Runnable() {
    @Override
    public void run() {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Asynchronous task");
    }
});
System.out.println("hello world");
executorService.shutdown();
```

ExecutorService的使用

委托任务给ExecutorService的方式：

##### execute(Runnable)

接受一个java.lang.Runable对象的实例，异步执行

```java
ExecutorService executorService = Executors.newSingleThreadExecutor();

executorService.execute(new Runnable() {
    public void run() {
        System.out.println("Asynchronous task");
    }
});

executorService.shutdown();
```

注意，如果不关闭ExecutorService，task执行完以后实际上并不会关闭JVM，因为ExecutorService中激活的线程会阻止JVM关闭。图中也可以看到这一点。

![image-20221115204722657](https://shan-edu.oss-cn-chengdu.aliyuncs.com/img/202211152047786.png)

##### submit(Runnable)

submit(Runnable) 返回一个Future对象。**Future对象可以用来检测Runable是否执行完成**

```java
ExecutorService executorService = Executors.newFixedThreadPool(10);
Future future = executorService.submit(new Runnable() {
    @Override
    public void run() {
        System.out.println("Asynchronous task");
    }
});
future.get();
executorService.shutdown();
```

##### submit(Callable)

Runnable.run()方法不能返回一个结果(因为是void类型)，就算线程执行完了，成功了future.get()也只是得到null

而call方法可以返回一个结果，通过submit(Callable)方法返回的Future对象获取Callable的结果

```java
ExecutorService executorService2 = Executors.newFixedThreadPool(10);
Future<Object> future2 = executorService2.submit(new Callable<Object>() {
    @Override
    public Object call() throws Exception {
        System.out.println("Asynchronous Callable");
        return "Callable result";
    }
});
System.out.println("future2.get() = " + future2.get());
executorService2.shutdown();
```

输出

```java
Asynchronous Callable
future2.get() = Callable result
```

##### invokeAny(...)

接收一个Callable对象或者Callable的子接口实例的**集合**作为参数，这个方法不会返回Future，但会**随机返回**集合中某一个Callable的结果

```java
ExecutorService executorService3 = Executors.newFixedThreadPool(10);
HashSet<Callable<String>> callableList = new HashSet<Callable<String>>();
callableList.add(new Callable<String>() {
    @Override
    public String call() throws Exception {
        return "task1";
    }
});
    callableList.add(() -> {
        return "task2";
    });
    callableList.add(() -> {
        return "task3";
    });
for (int i = 0; i < 10; i++) {
    String res = executorService3.invokeAny(callableList);
    System.out.println("res = " + res);
}
executorService3.shutdown();
```

可以看到，结果是随机的

```java
res = task2
res = task3
res = task1
res = task2
res = task1
res = task1
res = task1
res = task1
res = task1
res = task1
```

1. invokeAll(...)

   invokeAll()接收一个Callable对象的**集合**作为参数，该方法会调用你传给他的集合中的所有Callable对象。Invoke()会返回一个Future对象的列表，通过这个列表你可以获取每一个Callable执行的结果。

   记住一个任务可能会因为一个异常而结束，因此这时任务并不是真正意义上执行成功了。这在Future上是没有办法来判断的。

   ```java
   
   ExecutorService executorService4 = Executors.newFixedThreadPool(10);
   HashSet<Callable<String>> callableList = new HashSet<Callable<String>>();
   callableList.add(new Callable<String>() {
       @Override
       public String call() throws Exception {
           return "task1";
       }
   });
       callableList.add(() -> {
           return "task2";
       });
       callableList.add(() -> {
           return "task3";
       });
   List<Future<String>> futures = executorService4.invokeAll(callableList);
   for (Future<String> future : futures) {
       System.out.println("future.get() = " + future.get());
   }
   executorService4.shutdown();
   ```

##### ExecutorService.shutdown()

该方法调用后ExecutorService不会立即关闭，但是它也不会接受新的任务，直到它里面的所有线程都执行完毕，ExecutorService才会关闭

1. 停止接收新的submit的任务；
2. 已经提交的任务（包括正在跑的和队列中等待的）,会继续执行完成；
3. 等到第2步完成后，才真正停止；

##### ExecutorService.shutdownNow()

立即关闭ExecutorService，这会尝试**立即停止所有正在执行的任务，并且忽略所有提交的但未被处理的任务**。**对于正在执行的任务是不能确定的**，也许它们停止了，也行它们执行直到结束。

1. 跟 shutdown() 一样，先停止接收新submit的任务；
2. 忽略队列里等待的任务；
3. 尝试将正在执行的任务interrupt中断；
4. 返回未执行的任务列表；

##### shutdown和shutdownNow的区别

**shutdown只是将线程池的状态设置为SHUTWDOWN状态，正在执行的任务会继续执行下去，没有被执行的则中断。**

**而shutdownNow则是将线程池的状态设置为STOP，正在执行的任务则被停止，没被执行任务的则返回**

### ThreadPoolExecutor

Java.util.concurrent.ThreadPoolExecutor类是ExecutorSerivce接口的具体实现。ThreadPoolExecutor使用线程池中的一个线程来执行给定的任务，ThreadPoolExecutor也有ExecutorService的那些方法，submit、execute等

![A ThreadPoolExecutor.](https://jenkov.com/images/java-concurrency-utils/thread-pool-executor.png)