##### compute 、computeIfAbsent、computeIfPresent

Java 8 在 Map 和 ConcurrentMap 接口中都增加了 3 个 `compute` 方法，说明也是支持多线程并发安全操作的。

三个方法的区别：

- compute：计算并更新值
- computeIfAbsent：Value不存在时才计算
- computeIfPresent：Value存在时才计算

##### compute

统计出现次数

```java
public static void main(String[] args) {
    List<String> animals = Arrays.asList("dog", "cat", "cat", "dog", "fish", "dog");
    Map<String, Integer> map = new HashMap<>();
    for(String animal : animals){
        Integer count = map.get(animal);
        map.put(animal, count == null ? 1 : ++count);
    }
    System.out.println(map);
}
```

输出

```java
{cat=2, fish=1, dog=3}
```

代码再怎么精简都需要一步 `get` 操作，判断集合中是否有元素再确定是初始化为1，还是需要 +1。很多时候，这个 `get` 操作显然是毫无必要的

```java
List<String> animals = Arrays.asList("dog", "cat", "cat", "dog", "fish", "dog");
Map<String, Integer> map = new HashMap<>();
for(String animal : animals){
    map.compute(animal, (k, v) -> v == null ? 1 : ++v);
}
System.out.println(map);
```

使用 `compute` 方法一行搞定。compute的源码

```java
default V compute(K key,
                  BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
    // 函数式接口不能为空    
    Objects.requireNonNull(remappingFunction);
    // 获取旧值
    V oldValue = get(key);
    // 获取计算的新值
    V newValue = remappingFunction.apply(key, oldValue);
    if (newValue == null) { // 新值为空
        // delete mapping
        if (oldValue != null || containsKey(key)) { // 旧值存在时
            // 移除该键值
            remove(key);
            return null;
        } else {
            // nothing to do. Leave things as they were.
            return null;
        }
    } else { // 新值不为空
        // 添加或者覆盖旧值
        put(key, newValue);
        return newValue;
    }
}
```

##### computeIfPresent

computeIfPresent() 方法对 hashMap 中指定 key 的值进行重新计算，**前提是该 key 存在于 hashMap 中**

语法：`hashmap.computeIfPresent(K key, BiFunction remappingFunction)`

参数

- key - 键
- remappingFunction - 重新映射函数，用于重新计算值

返回值

+ 如果 key 对应的 value 不存在，则返回该 null，如果存在，则返回通过 remappingFunction 重新计算后的值

```java
    public static void main(String[] args) {
        // 创建一个 HashMap
        HashMap<String, Integer> prices = new HashMap<>();
        // 往HashMap中添加映射关系
        prices.put("Shoes", 200);
        prices.put("Bag", 300);
        prices.put("Pant", 150);
        System.out.println("HashMap: " + prices);

        // 重新计算鞋加上10%的增值税后的价值
        Integer shoesPrice = prices.computeIfPresent("Shoes", (key, value) -> value + value * 10 / 100);
        if (Objects.nonNull(shoesPrice)){
            System.out.println("Price of Shoes after VAT: " + shoesPrice);
        }
        // 输出更新后的HashMap
        System.out.println("Updated HashMap: " + prices);
    }
```

注意表达式：

```
prices.computeIfPresent("Shoes", (key, value) -> value + value * 10/100)
```

代码中，使用了匿名函数 lambda 表达式 **(key, value) -> value + value \* 10/100** 作为重新映射函数