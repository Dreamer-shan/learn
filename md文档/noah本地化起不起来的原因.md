noah本地化起不起来的原因

发现是create bean出了问题，有rpc调用，项目里调用其他项目的dubbo接口时，只有dubbo接口没有实现类，所以注入的时候就出了问题。

解决：在JVM参数中添加-Ddubbo.reference.check=false

![image-20221028194340780](C:\Users\hongyuan.shan\AppData\Roaming\Typora\typora-user-images\image-20221028194340780.png)

