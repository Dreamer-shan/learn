用户单价格receiveOrder、供应单价格purchaseOrder、接口价格

用户单价格是原始的价格，进行pata的时候获取到供应单价格，但是这个供应单价格不能跟用户单价格混合在一起，所以作为一些临时对象存放在extmap.purchaseAdapter    **purchaseAdapterInfo**中，之后进行比价

比价通常是    **供应单价格-用户单价格**

P单价格有三个来源：bookingTag、黑屏指令pata返回的价格、旗舰店接口返回的价格





两种表

N开头的表：n_order_info  n_passenger  n_order_ext    

面向用户，存订单信息和用户信息，如果有变价，出票成功后，把供应单价格覆盖原来的N单价格

F开头的表：f_n_order_info  f_n_order_info f_n_passenger

面向供应商，大部分信息来源于N开头的表，但是计算价格的时候有一些特殊逻辑，同时有一些自己独有的字段。

