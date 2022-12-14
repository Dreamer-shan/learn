@Data父类的字段不参与hashcode计算



```java
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Data
public class Father {
    //姓氏
    private String lastName;
}
```



```java
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Data
public class Son extends Father{

    static class Child{
        private String name;
        public Child(String name) {
            this.name = name;
        }
    }
    //名
    private String firstName;

    public static void main(String[] args) {
        Child child1 = new Child("aaa");
        Child child2 = new Child("aaa");
        HashSet<Object> set = new HashSet<>();
        System.out.println("child1.equals(child2) = " + child1.equals(child2));
        set.add(child1);
        set.add(child2);
        System.out.println("set.size() = " + set.size());
        Son son1 = Son.builder().lastName("张").firstName("三").build();
        Son son2 = Son.builder().lastName("张").firstName("三").build();
        System.out.println(son1);
        System.out.println(son2);
        System.out.println("son1.equals(son2) = " + son1.equals(son2));
        set.clear();
        set.add(son1);
        set.add(son2);
        System.out.println("set.size() = " + set.size());
        System.out.println("set = " + set);
    }
}
```

```java
child1.equals(child2) = false
set.size() = 2
Son(firstName=三)
Son(firstName=三)
son1.equals(son2) = true
set.size() = 1
set = [Son(firstName=三)]
```

##### 问题1

为什么打印出来只有子类的字段firstName，没有父类的字段lastName呢？



原因是@Data默认是包含@ToString注解的，但是默认的@ToString没有设置`callSuper为true`，@ToString(callSuper = true)可以将超类实现`toString`的输出包含到输出中



加上@ToString(callSuper = true)注解后，就变成这样了

```java
child1.equals(child2) = false
set.size() = 2
Son(super=Father(lastName=张), firstName=三)
Son(super=Father(lastName=张), firstName=三)
son1.equals(son2) = true
set.size() = 1
set = [Son(super=Father(lastName=张), firstName=三)]
```

##### 问题2

可以看到，内部类child，直接new出来child1和child2，这两个对象是不相等的`child1.equals(child2) = false`，两个对象都可以添加到HashSet中，set.size() = 2，

但是`son1.equals(son2) = true`，且son1和son2放入hashSet中却发现最后set.size() = 1，说明有一个对象没有放进去。

黄色部分有提示，生成 equals/hashCode 实现，但不调用超类，即使此类不继承 java.lang.Object。如果这是有意为之，请将“（callSuper=false）”添加到您的类型中。

![image-20221209094539889](https://shan-edu.oss-cn-chengdu.aliyuncs.com/img/202212090945020.png)

解决办法，在子类`@EqualsAndHashCode(callSuper = true)`中加上这个方法就好了，就会把父类的字段也参与hashcode计算

```java

@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Data
public class Son extends Father{
    private String firstName;
    public static void main(String[] args) {
        HashSet<Son> set = new HashSet<>();
        Son son1 = Son.builder().lastName("张").firstName("三").build();
        Son son2 = Son.builder().lastName("李").firstName("三").build();
        System.out.println("son1.equals(son2) = " + son1.equals(son2));
        set.add(son1);
        set.add(son2);
        System.out.println("set.size() = " + set.size());
        System.out.println("set = " + set);
    }
}
```

```java
son1.equals(son2) = true
set.size() = 1
set = [Son(firstName=三)]
```

加上@EqualsAndHashCode(callSuper = true)后，就可以放进去了

```java
son1.equals(son2) = false
set.size() = 2
set = [Son(firstName=三), Son(firstName=三)]
```





