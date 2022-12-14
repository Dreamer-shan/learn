同步bookingTag和异步bookingTag

​	同步bookingTag只有一些基本信息，异步bookingTag还有一些复杂的逻辑，因此较为准确。后面比价的时候通常是取异步bookingTag中的价格



同步价格校验和异步价格校验

​	先执行异步价格校验，pata价-供应价

​	后执行同步价格校验，供应价-用户价

