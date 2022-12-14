

##### getInstance() 的理解

##### getInstance方法的主要作用

主函数当中使用此类的getInstance()函数，即**可得到系统当前已经实例化的该类对象，若当前系统还没有实例化过这个类的对象，则调用此类的构造函数**

##### 对象实例化

对象的实例化方法，也是比较多的，最常用的方法是直接使用new。而这是最普通的，如果要考虑到其它的需要，如单实例模式，层次间调用等等。
直接使用new就不是最好的设计，**这时候需要使用间接使用new，即getInstance方法。这是一个设计方式的代表，而不仅仅指代一个方法名**。

##### GetInstance与new区别：

new的使用
如Object object = new Object()，这时候，就必须要知道有第二个Object的存在，而第二个Object也常常是在当前的应用程序域中的，可以被直接调用的

GetInstance的使用
在主函数开始时调用，返回一个实例化对象，**此对象是static的，在内存中保留着它的引用，即内存中有一块区域专门用来存放静态方法和变量**，可以直接使用，**调用多次返回同一个对象。**

两者区别对照
大部分类(非抽象类/接口/屏蔽了constructor的类)都可以用new，**new就是通过生产一个新的实例对象，或者在栈上声明一个对象 ，每部分的调用用的都是一个新的对象。**

getInstance是少部分类才有的一个方法，各自的实现也不同。
getInstance在单例模式(保证一个类仅有一个实例，并提供一个访问它的全局访问点)的类中常见，**用来生成唯一的实例，getInstance往往是static的**。

(1) 对象使用之前通过getInstance得到而不需要自己定义，用完之后不需要delete；

(2)**new 一定要生成一个新对象，分配内存；getInstance() 则不一定要再次创建，它可以把一个已存在的引用给你使用，这在性能上优于new**；

(3) new创建后只能当次使用，**而getInstance()可以跨栈区域使用，或者远程跨区域使用**。所以getInstance()通常是创建static静态实例方法的。

##### 总结：

1. getInstance这个方法在单例模式用的甚多，为了避免对内存造成浪费，直到需要实例化该类的时候才将其实例化，所以用getInstance来获取该对象
2.  至于其他时候，也就是为了简便而已，为了不让程序在实例化对象的时候，不用每次都用new关键字，索性提供一个instance方法，不必一执行这个类就初始化，这样做到不浪费系统资源！单例模式可以防止数据的冲突，节省内存空间

##### 项目中的例子

CommonQConfig作为Qconfig配置对象，**只需要实例化一次**，使用getInstance()方法实际上是返回了INSTANCE，而INSTANCE是静态的。

```java
if(!CommonQConfig.getInstance().isDegradeRetrySupportDegrade()){
    AppContext.setSkipPidError(true);
}
```

```java
public static CommonQConfig getInstance() {
    return INSTANCE;
}

private static final CommonQConfig INSTANCE = new CommonQConfig();
```

