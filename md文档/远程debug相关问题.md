##### 远程debug相关问题

1.debug的时候最好先预热，也就是不打断点，跑两次之后再打

2.断点还没跑完的时候不要点停止或者重启，这样服务可能阻塞住，导致有时候断点突然进不来了，有时候甚至需要重启服务。最好是按F9让断点都跑完后，再开始新的请求。

3.重启后看catalina日志，Catalina是容器日志

4.sudo /home/q/tools/bin/restart_tomcat.sh /home/q/www/tts_trade_core/   重启服务

5.看监控地址  curl http://localhost:8080/config/qmonitor.jsp，如果只看想某个监控key的命中情况，可以使用grep