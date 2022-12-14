#### 使用机票测试包然后用NOAH远程Debug

##### 下载和配置机票测试包

+ 机票测试包下载（需要连接内网）地址：http://d.maps9.com/Jeeves

+ 选择部门后，直接下载（最好下载release版本）

  ![image-20220929120624877](https://shan-edu.oss-cn-chengdu.aliyuncs.com/img/image-20220929120624877.png)

+ 安装完之后进入软件包，点击齿轮进行配置

  ![image-20220929124600056](https://shan-edu.oss-cn-chengdu.aliyuncs.com/img/image-20220929124600056.png)

  登录QT账号，QT会收到验证码，然后点击服务配置，在通用服务中选择机票软路由，保存即可。

  ![image-20220929124632472](https://shan-edu.oss-cn-chengdu.aliyuncs.com/img/image-20220929124632472.png)

  ![image-20220929124946769](https://shan-edu.oss-cn-chengdu.aliyuncs.com/img/image-20220929124946769.png)

  ##### 创建noah环境，绑定环境

+ 创建noah环境，并且开启远程debug，可以使用域名或者远程ip![image-20220929114001733](https://shan-edu.oss-cn-chengdu.aliyuncs.com/img/image-20220929114001733.png)

+ 在软路由中绑定测试环境，使用测试包二维码扫描获取设备信息，然后绑定

  ![image-20220929115715590](https://shan-edu.oss-cn-chengdu.aliyuncs.com/img/image-20220929115715590.png)

+ 在idea中添加remote，host输入远程ip或者域名都可以，接下来就可以打断点远程debug了

  ![image-20220929115903533](https://shan-edu.oss-cn-chengdu.aliyuncs.com/img/image-20220929115903533.png)