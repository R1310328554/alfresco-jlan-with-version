项目加载过程：
1、配置文件加载
a>web.xml-->VirtuaServerStartServlet.init-->VirtuaServiceRun.start 线程启动
  初始化Spring容器，获取XMLServerConfiguration配置实例，SMBServer实例、NetBIOSNameServer实例，最终启动Server服务

b> XMLServerConfiguration构造-->子类构造CifsOnlyXMLServerConfiguration-->子类构造ServerConfiguration
   XMLServerConfiguration.setXmlpath-->CifsOnlyXMLServerConfiguration.setXmlpath（加载jlanConfig.xml）
   -->CifsOnlyXMLServerConfiguration.loadConfiguration
   上面是获取XMLServerConfiguration配置实例，细节，注意，这里还加载一个Filesystems的配置，获取共享设备（调用 CifsOnlyXMLServerConfiguration.procSharesElement-->CifsOnlyXMLServerConfiguration.loadDBShare)
  XMLServerConfiguration.setShareMapper-->FilesMappingForDB 静态块（加载“资料库”的配置）
  同时，加载配置时，调用CifsOnlyXMLServerConfiguration.procServerCoreElement还创建了ThreadRequestPool 请求处理线程池【采用默认配置】

c>SMBServer构造-->子类构造NetworkFileServer-->子类构造NetworkServer-->SMBServer.CommonConstructor（设置每个服务会话SMBSrvSession配置）
  SMBServer.start 线程启动-->SMBServer.run 服务运行入口
  new AdminSharedDevice() “我的文件”添加到Filesystems配置共享中-->配置 m_connectionsHandler， CIFS connections handler【3种】
  -->初始化 connection handler并启用它-->等待连接 while循环-->若发出服务停止或连接异常都会停止服务

d>connection handler 初始化 【以NIOCifsConnectionsHandler为例】
  CifsConnectionsHandler.initializeHandler-->若SMB启用了tcpipSMB，则Create the native SMB/port 445 session handler，同时初始化，并添加到sessionHandlerList中（建立NIO ServerSocketChannel）
  -->若SMB启用了netBIOSSMB，则Create the NetBIOS session handler (port 139)，同时初始化，并添加到sessionHandlerList中（建立NIO ServerSocketChannel 服务器端）
  --> Create the session request handler list and add the first handler ，CIFSRequestHandler 是一个线程，接受客户端请求
  -->CIFSRequestHandler.run 创建 NIO Selcetor，等待连接，判断是否存在 request handler has active session，若当前request handler不是第一个，则关闭它，
  否则，继续等session；若有队列中有active session，则等待客户端请求（Selector.select 阻塞式），
  有客户端请求，则先进行Event处理，之后，若队列中存在多于一个session，则对队列中每个session（要是新建立的）都注册到当前Selector，
  最后检查空闲的session（超时的session），并关闭掉。
  Event处理细节：获取感兴趣的 Selector.selectedKeys，并迭代，若是感兴趣的SelectionKey，刚取出对应的session，并创建一个新的处理请求ThreadRequest，与之绑定，
  再放入到请求线程池队列中（这个动作为触发，请求处理线程池ThreadRequestPool中ThreadWork线程进行处理，真正处理请求），ThreadWork线程，处理队列中ThreadRequest对象，
  ThreadRequest.runRequest（实际调用 NIOCIFSThreadRequest.runRequest），调用 SMBSrvSession.processPacket ，进行最终处理【根据不同的pocket类型进行处理】

e>connection handler 启动【以NIOCifsConnectionsHandler为例】
  -->CifsConnectionsHandler.startHandler 线程启动 --> CifsConnectionsHandler.run  创建 NIO Selector，注册Session Handlers（服务器端）与指定的Selector绑定，
     等待连接，若没有连接建立，则继续循环，否则获取选择的键selectedKeys，迭代每个SelectionKey，若是可接收的，则获取这个key创建的channel，接受连接，
    为刚建立的SocketChannel客户端创建PacketHandler处理，创建新的SMBSrvSession，根据刚刚创建的PacketHandler，将创建的新SMBSrvSession放到Vector(CIFSRequestHandler)请求处理队列里面（永远只使用第一个requestHandler），
    -->NIOCifsConnectionsHandler.queueSessionToHandler-->CIFSRequestHandler.queueSessionToHandler-->SrvSessionQueue.addSession  通知线程，处理请求



注意：
重点线程，NIO 模型   channel（ServerSocketChannel与SocketChannel）、Selector
参见：
http://blog.csdn.net/wuxianglong/article/details/6604817
http://blog.csdn.net/wuxianglong/article/details/6612246
http://blog.csdn.net/wuxianglong/article/details/6612263
http://blog.csdn.net/wuxianglong/article/details/6612282