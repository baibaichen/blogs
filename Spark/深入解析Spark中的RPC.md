# 深入解析Spark中的RPC

## 前言

Spark是一个快速的、通用的分布式计算系统，而分布式的特性就意味着，必然存在节点间的通信，本文主要介绍不同的Spark组件之间是如何通过RPC（Remote Procedure Call) 进行点对点通信的。分为3个章节，

1. Spark RPC的简单示例和实际应用
2. Spark RPC模块的设计原理
3. Spark RPC核心技术总结

## 1. Spark RPC的简单示例和实际应用

Spark的RPC主要在两个模块中，

1. 在Spark-core中，主要承载了更好的封装server和client的作用，以及和scala语言的融合，它依赖于模块org.apache.spark.spark-network-common。
2. 在org.apache.spark.spark-network-common中，该模块是java语言编写的，最新版本是基于netty4开发的，提供全双工、多路复用I/O模型的Socket I/O能力，Spark的传输协议结构（wire protocol）也是自定义的。

为了更好的了解Spark RPC的内部实现细节，我基于Spark 2.1版本抽离了RPC通信的部分，单独启了一个项目[https://github.com/neoremind/kraps-rpc](https://link.zhihu.com/?target=https%3A//github.com/neoremind/kraps-rpc)，放到了github以及发布到Maven中央仓库做学习使用，提供了比较好的上手文档、参数设置和性能评估。下面就通过这个模块对Spark RPC先做一个感性的认识。

以下的代码均可以在[kraps-rpc](https://link.zhihu.com/?target=https%3A//github.com/neoremind/kraps-rpc)找到。

### 1.1 简单示例

假设我们要开发一个Hello服务，客户端可以传输 string，服务端响应hi或者bye，并echo回去输入的string。

**第一步**，定义一个 `HelloEndpoint` 继承自 `RpcEndpoint` 表明可以并发的调用该服务，如果继承自 `ThreadSafeRpcEndpoint` 则表明该 `Endpoint` 不允许并发。

```scala
class HelloEndpoint(override val rpcEnv: RpcEnv) extends RpcEndpoint {
  override def onStart(): Unit = {
    println("start hello endpoint")
  }

  override def receiveAndReply(context: RpcCallContext): PartialFunction[Any, Unit] = {
    case SayHi(msg) => {
      println(s"receive $msg")
      context.reply(s"hi, $msg")
    }
    case SayBye(msg) => {
      println(s"receive $msg")
      context.reply(s"bye, $msg")
    }
  }

  override def onStop(): Unit = {
    println("stop hello endpoint")
  }
}

case class SayHi(msg: String)
case class SayBye(msg: String)
```

和Java传统的RPC解决方案对比，可以看出这里不用定义接口或者方法标示（比如通常的id或者name），使用scala的模式匹配进行方法的路由。虽然点对点通信的契约交换受制于语言，这里就是SayHi和SayBye两个case class，但是Spark RPC定位于内部组件通信，所以无伤大雅。

**第二步**，把刚刚开发好的Endpoint交给Spark RPC管理其生命周期，用于响应外部请求。RpcEnvServerConfig可以定义一些参数、server名称（仅仅是一个标识）、bind地址和端口。通过NettyRpcEnvFactory这个工厂方法，生成RpcEnv，RpcEnv是整个Spark RPC的核心所在，后文会详细展开，通过setupEndpoint将"hello-service"这个名字和第一步定义的Endpoint绑定，后续client调用路由到这个Endpoint就需要"hello-service"这个名字。调用awaitTermination来阻塞服务端监听请求并且处理。

```scala
val config = RpcEnvServerConfig(new RpcConf(), "hello-server", "localhost", 52345)
val rpcEnv: RpcEnv = NettyRpcEnvFactory.create(config)
val helloEndpoint: RpcEndpoint = new HelloEndpoint(rpcEnv)
rpcEnv.setupEndpoint("hello-service", helloEndpoint)
rpcEnv.awaitTermination()
```

**第三步**，开发一个client调用刚刚启动的server，首先RpcEnvClientConfig和RpcEnv都是必须的，然后通过刚刚提到的"hello-service"名字新建一个远程Endpoint的引用（Ref），可以看做是stub，用于调用，这里首先展示通过异步的方式来做请求。

```scala
val rpcConf = new RpcConf()
val config = RpcEnvClientConfig(rpcConf, "hello-client")
val rpcEnv: RpcEnv = NettyRpcEnvFactory.create(config)
val endPointRef: RpcEndpointRef = rpcEnv.setupEndpointRef(RpcAddress("localhost", 52345), "hell-service")
val future: Future[String] = endPointRef.ask[String](SayHi("neo"))
future.onComplete {
    case scala.util.Success(value) => println(s"Got the result = $value")
    case scala.util.Failure(e) => println(s"Got error: $e")
}
Await.result(future, Duration.apply("30s"))
```

也可以通过同步的方式，在最新的Spark中askWithRetry实际已更名为askSync。

```scala
val result = endPointRef.askWithRetry[String](SayBye("neo"))
```

这就是 Spark RPC 的通信过程，使用起来易用性可想而知，非常简单，RPC框架屏蔽了**Socket I/O模型**、**线程模型**、**序列化/反序列化过程**、<u>**使用netty做了包识别**</u>，**长连接**，**网络重连重试**等机制。

### 1.2 实际应用

在Spark内部，很多的Endpoint以及EndpointRef与之通信都是通过这种形式的，举例来说比如driver和executor之间的交互用到了心跳机制，使用[HeartbeatReceiver](https://link.zhihu.com/?target=https%3A//github.com/apache/spark/blob/branch-2.1/core/src/main/scala/org/apache/spark/HeartbeatReceiver.scala)来实现，这也是一个Endpoint，它的注册在SparkContext初始化的时候做的，代码如下：

```scala
_heartbeatReceiver = env.rpcEnv.setupEndpoint(HeartbeatReceiver.ENDPOINT_NAME, new HeartbeatReceiver(this))
```

而它的调用在[Executor](https://link.zhihu.com/?target=https%3A//github.com/apache/spark/blob/branch-2.1/core/src/main/scala/org/apache/spark/executor/Executor.scala)内的方式如下：

```scala
val message = Heartbeat(executorId, accumUpdates.toArray, env.blockManager.blockManagerId)
val response = heartbeatReceiverRef.askWithRetry[HeartbeatResponse](message, RpcTimeout(conf, "spark.executor.heartbeatInterval", "10s")) 
```

## 2. Spark RPC 模块的设计原理

首先说明下，自Spark 2.0后已经把Akka这个RPC框架剥离出去了（详细见[SPARK-5293](https://link.zhihu.com/?target=https%3A//issues.apache.org/jira/browse/SPARK-5293)），原因很简单，因为很多用户会使用Akka做消息传递，那么就会和Spark内嵌的版本产生冲突，而Spark也仅仅用了Akka做RPC，所以2.0之后，基于底层的org.apache.spark.spark-network-common模块实现了一个类似Akka Actor消息传递模式的scala模块，封装在了core里面，[kraps-rpc](https://link.zhihu.com/?target=https%3A//github.com/neoremind/kraps-rpc)也就是把这个部分从core里面剥离出来独立了一个项目。

虽然剥离了Akka，但是还是沿袭了Actor模式中的一些概念，在现在的Spark RPC中有如下映射关系。

```scala
   RpcEndpoint => Actor
RpcEndpointRef => ActorRef
        RpcEnv => ActorSystem
```

底层通信全部使用 *netty* 进行了替换，使用的是 *org.apache.spark.spark-network-common* 这个内部 **lib**。

### 2.1 类图分析

这里先上一个UML图展示了Spark RPC模块内的类关系，白色的是 *Spark-core* 中的 **scala** 类，黄绿色的是 `org.apache.spark.spark-network-common` 中的 **java** 类。

![ ](https://pic3.zhimg.com/v2-76f4290331be284243c7e0f0ea7e3966_r.jpg)

不要被这张图所吓倒，经过下面的解释分析，相信读者可以领会其内涵，不用细究其设计的合理度，Spark是一个发展很快、不断演进的项目，代码不是一成不变的，持续变化是一定的。

#### RpcEndpoint 和 RpcCallContext

先看最左侧的`RpcEndpoint`，`RpcEndpoint`是一个可以响应请求的服务，和**Akka**中的`Actor`类似，从它的提供的方法签名（如下）可以看出，`receive`方法是单向方式的，可以比作UDP，而`receiveAndReply`是应答方式的，可以比作TCP。它的子类实现可以选择性的覆盖这两个函数，我们第一章实现的HelloEndpoint以及Spark中的HeartbeatReceiver都是它的子类。

```scala
def receive: PartialFunction[Any, Unit] = {
    case _ => throw new RpcException(self + " does not implement 'receive'")
}

def receiveAndReply(context: RpcCallContext): PartialFunction[Any, Unit] = {
    case _ => context.sendFailure(new RpcException(self + " won't reply anything"))
}
```

其中`RpcCallContext`是用于分离核心业务逻辑和底层传输的桥接方法，<u>这也可以看出Spark RPC多用组合，聚合以及回调callback的设计模式来做OO抽象</u>，这样可以剥离**业务逻辑**->**RPC封装（Spark-core模块内）**->**底层通信（spark-network-common）**三者。`RpcCallContext`可以用于回复正常的响应以及错误异常，例如：

```scala
reply(response: Any)      // 回复一个message，可以是一个case class。
sendFailure(e: Throwable) // 回复一个异常，可以是Exception的子类，
                          // 由于Spark RPC默认采用Java序列化方式，
                          // 所以异常可以完整的在客户端还原，
                          // 并且作为cause re-throw出去。
```

`RpcCallContext`也分为了两个子类，分别是 `LocalNettyRpcCallContext` 和 `RemoteNettyRpcCallContext`，这个主要是框架内部使用，如果是本地就走`LocalNettyRpcCallContext`直接调用Endpoint即可，否则就走`RemoteNettyRpcCallContext`需要通过RPC和远程交互，这点也体现了RPC的核心概念，就是如何执行另外一个地址空间上的函数、方法，就仿佛在本地调用一样。

另外，`RpcEndpoint`还提供了一系列回调函数覆盖。

```scala
- onError
- onConnected
- onDisconnected
- onNetworkError
- onStart
- onStop
- stop
```

另外需要注意下，它的一个子类是`ThreadSafeRpcEndpoint`，很多Spark中的Endpoint继承了这个类，Spark RPC框架对这种Endpoint不做并发处理，也就是同一时间只允许一个线程在做调用。

还有一个默认的RpcEndpoint叫做`RpcEndpointVerifier`，每一个RpcEnv初始化的时候都会注册上这个Endpoint，因为客户端的调用每次都需要先询问服务端是否存在某一个Endpoint。

#### RpcEndpointRef

`RpcEndpointRef` 类似于 **Akka** 中 `ActorRef`，顾名思义，它是 `RpcEndpoint` 的引用，提供的方法 `send` 等同于 `!`, `ask` 方法等同于 `?`，`send` 用于单向发送请求（`RpcEndpoint` 中的 `receive `响应它），<u>提供 **fire-and-forget** 语义</u>，而 `ask` 提供请求响应的语义（ `RpcEndpoint` 中的 `receiveAndReply` 响应它），默认是需要返回 **response** 的，带有超时机制，可以同步阻塞等待，也可以返回一个 `Future` 句柄，不阻塞发起请求的工作线程。

`RpcEndpointRef` 是客户端发起请求的入口，它可以从 `RpcEnv` 中获取，并且聪明的做本地调用或者RPC。

#### RpcEnv 和 NettyRpcEnv

类库中最核心的就是 `RpcEnv`，刚刚提到了这就是 `ActorSystem`，服务端和客户端都可以使用它来做通信。

对**服务端**来说，`RpcEnv` 是 `RpcEndpoint` 的运行环境，负责 `RpcEndpoint` 的整个生命周期管理，它可以==注册==或者==销毁== `Endpoint`，解析 *TCP* 层的数据包并反序列化，封装成`RpcMessage`，并且路由请求到指定的 `Endpoint`，调用业务逻辑代码，如果 `Endpoint`需要响应，把返回的对象序列化后通过 *TCP* 层再传输到远程对端，如果`Endpoint` 发生异常，那么调用 `RpcCallContext.sendFailure` 来把异常发送回去。

对客户端来说，通过 `RpcEnv` 可以获取 `RpcEndpoint` 引用，也就是 `RpcEndpointRef`。

`RpcEnv` 和具体的底层通信模块交互，其伴生对象包含创建 `RpcEnv` 的方法：

```scala
def create(
      name: String,
      bindAddress: String,
      advertiseAddress: String,
      port: Int,
      conf: SparkConf,
      securityManager: SecurityManager,
      numUsableCores: Int,
      clientMode: Boolean): RpcEnv = {
    val config = RpcEnvConfig(conf, name, bindAddress, advertiseAddress, port, securityManager,
      numUsableCores, clientMode)
    new NettyRpcEnvFactory().create(config)
  }
```

RpcEnv的创建由RpcEnvFactory负责，RpcEnvFactory目前只有一个子类是NettyRpcEnvFactory，原来还有AkkaRpcEnvFactory。NettyRpcEnvFactory.create方法一旦调用就会立即在bind的address和port上启动server。

它依赖的RpcEnvConfig就是一个包含了SparkConf以及一些参数（kraps-rpc中更名为RpcConf）。RpcEnv的参数都需要从RpcEnvConfig中拿，最基本的hostname和port，还有高级些的连接超时、重试次数、Reactor线程池大小等等。

下面看看RpcEnv最常用的两个方法：

```scala
// 服务端注册endpoint，必须指定名称，客户端路由就靠这个名称来找endpoint
def setupEndpoint(name: String, endpoint: RpcEndpoint): RpcEndpointRef 

// 客户端拿到一个endpoint的引用
def setupEndpointRef(address: RpcAddress, endpointName: String): RpcEndpointRef
```

NettyRpcEnv由NettyRpcEnvFactory.create创建，这是整个Spark core和org.apache.spark.spark-network-common的桥梁，内部利用底层提供的通信能力，同时包装了一个类Actor的语义。上面两个核心的方法，setupEndpoint会在 `Dispatcher` 中注册Endpoint，setupEndpointRef会先去调用RpcEndpointVerifier尝试验证本地或者远程是否存在某个endpoint，然后再创建RpcEndpointRef。更多关于服务端、客户端调用的细节将在时序图中阐述，这里不再展开。

#### Dispatcher和Inbox

`NettyRpcEnv` 中包含 `Dispatcher`，主要针对服务端，帮助路由到正确的`RpcEndpoint`，并且调用其业务逻辑。

这里需要先阐述下Reactor模型，Spark RPC的Socket I/O一个典型的Reactor模型的，但是结合了Actor pattern中的mailbox，可谓是一种混合的实现方式。

使用Reactor模型，由底层netty创建的EventLoop做I/O多路复用，这里使用Multiple Reactors这种形式，如下图所示，从netty的角度而言，Main Reactor和Sub Reactor对应BossGroup和WorkerGroup的概念，前者负责监听TCP连接、建立和断开，后者负责真正的I/O读写，**而图中的ThreadPool就是的Dispatcher中的线程池**，它来解耦开来耗时的业务逻辑和I/O操作，这样就可以更scalabe，只需要少数的线程就可以处理成千上万的连接，这种思想是标准的分治策略，offload非I/O操作到另外的线程池。 

真正处理RpcEndpoint的业务逻辑在ThreadPool里面，中间靠Reactor线程中的handler处理decode成RpcMessage，然后投递到Inbox中，所以compute的过程在另外的下面介绍的Dispatcher线程池里面做。

![img](https://pic3.zhimg.com/80/v2-6aef9092a113e5a0b123c1141199fe2a_hd.png)

（图片来源[点此](https://link.zhihu.com/?target=http%3A//gee.cs.oswego.edu/dl/cpjslides/nio.pdf)）

刚刚还提到了Actor pattern中mailbox模式，Spark RPC最早起源于Akka，所以进化到现在，仍然了使用了这个模式。这里就介绍Inbox，每个Endpoint都有一个Inbox，Inbox里面有一个InboxMessage的链表，InboxMessage有很多子类，可以是远程调用过来的RpcMessage，可以是远程调用过来的fire-and-forget的单向消息OneWayMessage，还可以是各种服务启动，链路建立断开等Message，这些Message都会在Inbox内部的方法内做模式匹配，调用相应的RpcEndpoint的函数（都是一一对应的）。

Dispatcher中包含一个MessageLoop，它读取LinkedBlockingQueue中的投递RpcMessage，根据客户端指定的Endpoint标识，找到Endpoint的Inbox，然后投递进去，由于是阻塞队列，当没有消息的时候自然阻塞，一旦有消息，就开始工作。Dispatcher的ThreadPool负责消费这些Message。

Dispatcher的ThreadPool它使用参数spark.rpc.netty.dispatcher.numThreads来控制数量，如果kill -3 <PID>每个Spark driver或者executor进程，都会看到N个dispatcher线程：

```scala
"dispatcher-event-loop-0" #26 daemon prio=5 os_prio=31 tid=0x00007f8877153800 nid=0x7103 waiting on condition [0x000000011f78b000]
```

那么另外的问题是谁会调用Dispatcher分发Message的方法呢？答案是RpcHandler的子类NettyRpcHandler，这就是Reactor中的线程做的事情。RpcHandler是底层org.apache.spark.spark-network-common提供的handler，当远程的数据包解析成功后，会调用这个handler做处理。

这样就完成了一个完全异步的流程，Network IO通信由底层负责，然后由Dispatcher分发，只要Dispatcher中的InboxMessage的链表足够大，那么就可以让Dispatcher中的ThreadPool慢慢消化消息，和底层的IO解耦开来，完全在独立的线程中完成，一旦完成Endpoint内部业务逻辑，利用RpcCallContext回调来做消息的返回。

#### Outbox

NettyRpcEnv中包含一个ConcurrentHashMap[RpcAddress, Outbox]，每个远程Endpoint都对应一个Outbox，这和上面Inbox遥相呼应，是一个mailbox似的实现方式。

和Inbox类似，Outbox内部包含一个OutboxMessage的链表，OutboxMessage有两个子类，OneWayOutboxMessage和RpcOutboxMessage，分别对应调用RpcEndpoint的receive和receiveAndReply方法。

NettyRpcEnv中的send和ask方法会调用指定地址Outbox中的send方法，当远程连接未建立时，会先建立连接，然后去消化OutboxMessage。

同样，一个问题是Outbox中的send方法如何将消息通过Network IO发送出去，如果是ask方法又是如何读取远程响应的呢？答案是send方法通过org.apache.spark.spark-network-common创建的TransportClient发送出去消息，由Reactor线程负责序列化并且发送出去，每个Message都会返回一个UUID，由底层来维护一个发送出去消息与其Callback的HashMap，当Netty收到完整的远程RpcResponse时候，回调响应的Callback，做反序列化，进而回调Spark core中的业务逻辑，做Promise/Future的done，上层退出阻塞。

这也是一个异步的过程，发送消息到Outbox后，直接返回，Network IO通信由底层负责，一旦RPC调用成功或者失败，都会回调上层的函数，做相应的处理。

#### spark-network-common中的类

这里暂不做过多的展开，都是基于Netty的封装，有兴趣的读者可以自行阅读源码，当然还可以参考我之前开源的[Navi-pbrpc](https://link.zhihu.com/?target=https%3A//github.com/neoremind/navi-pbrpc/wiki/Tutorials)框架的代码，其原理是基本相同的。

### 2.2 时序图分析

#### 服务启动    
![img](https://pic1.zhimg.com/v2-c333b37c160958d35569bbb2cd653fac_r.jpg)

#### 服务端响应

![img](https://pic1.zhimg.com/v2-9693b3cf2d3df7e0996b55844ae56ad4_r.jpg)

第一阶段，IO接收。TransportRequestHandler是netty的回调handler，它会根据wire format（下文会介绍）解析好一个完整的数据包，交给NettyRpcEnv做反序列化，如果是RPC调用会构造RpcMessage，然后回调RpcHandler的方法处理RpcMessage，内部会调用Dispatcher做RpcMessage的投递，放到Inbox中，到此结束。

第二阶段，IO响应。MessageLoop获取带处理的RpcMessage，交给Dispatcher中的ThreadPool做处理，实际就是调用RpcEndpoint的业务逻辑，通过RpcCallContext将消息序列化，通过回调函数，告诉TransportRequestHandler这有一个消息处理完毕，响应回去。

这里请重点体会异步处理带来的便利，使用Reactor和Actor mailbox的结合的模式，解耦了消息的获取以及处理逻辑。

#### 客户端请求
![img](https://pic2.zhimg.com/v2-79d7059727a80068a7db7b9681ed8509_r.jpg)

客户端一般需要先建立RpcEnv，然后获取RpcEndpointRef。

第一阶段，IO发送。利用RpcEndpointRef做send或者ask动作，这里以send为例，send会先进行消息的序列化，然后投递到指定地址的Outbox中，Outbox如果发现连接未建立则先尝试建立连接，然后调用底层的TransportClient发送数据，直接通过该netty的API完成，完成后即可返回，这里返回了UUID作为消息的标识，用于下一个阶段的回调，使用的角度来说可以返回一个Future，客户端可以阻塞或者继续做其他操作。

第二，IO接收。TransportResponseHandler接收到远程的响应后，会先做反序列号，然后回调第一阶段的Future，完成调用，这个过程全部在Reactor线程中完成的，通过Future做线程间的通知。

## 3. Spark RPC核心技术总结

Spark RPC作为RPC传输层选择TCP协议，做可靠的、全双工的binary stream通道。

做一个高性能/scalable的RPC，需要能够满足第一，服务端尽可能多的处理并发请求，第二，同时尽可能短的处理完毕。CPU和I/O之前天然存在着差异，网络传输的延时不可控，CPU资源宝贵，系统进程/线程资源宝贵，为了尽可能避免Socket I/O阻塞服务端和客户端调用，有一些模式（pattern）是可以应用的。Spark RPC的I/O Model由于采用了[Netty](https://link.zhihu.com/?target=http%3A//netty.io/)，因此使用的底层的I/O多路复用（I/O Multiplexing）机制，这里可以通过spark.rpc.io.mode参数设置，不同的平台使用的技术不同，例如linux使用epoll。

线程模型采用Multi-Reactors + mailbox的异步方式来处理，在上文中已经介绍过。

Schema Declaration和序列化方面，Spark RPC默认采用Java native serialization方案，主要从兼容性和JVM平台内部组件通信，以及scala语言的融合考虑，所以不具备跨语言通信的能力，性能上也不是追求极致，目前还没有使用Kyro等更好序列化性能和数据大小的方案。

协议结构，Spark RPC采用私有的wire format如下，采用headr+payload的组织方式，header中包括整个frame的长度，message的类型，请求UUID。为解决TCP粘包和半包问题，以及组织成完整的Message的逻辑都在org.apache.spark.network.protocol.MessageEncoder中。

![img](https://pic2.zhimg.com/80/v2-03cf6d9253b6b1b68d0922c5ee9a5921_hd.png)



使用[wireshake](https://link.zhihu.com/?target=https%3A//www.wireshark.org/)具体分析一下。

首先看一个RPC请求，就是调用第一章说的HelloEndpoint，客户端调用分两个TCP Segment传输，这是因为Spark使用netty的时候header和body分别writeAndFlush出去。

下图是第一个TCP segment：

![img](https://pic3.zhimg.com/80/v2-4532d7fe254e0b5653cd6cf7a20a3fa6_hd.png)

例子中蓝色的部分是header，头中的字节解析如下：

```scala
00 00 00 00 00 00 05 d2 // 十进制1490，是整个frame的长度
```

03一个字节表示的是RpcRequest，枚举定义如下，

```scala
RpcRequest(3)
RpcResponse(4)
RpcFailure(5)
StreamRequest(6)
StreamResponse(7)
StreamFailure(8),
OneWayMessage(9)
User(-1)
```

每个字节的意义如下

```scala
4b ac a6 9f 83 5d 17 a9  // 8个字节是UUID
05 bd // 十进制1469，payload长度
```

具体的Payload就长下面这个样子，可以看出使用Java native serialization，一个简单的Echo请求就有1469个字节，还是很大的，序列化的效率不高。但是Spark RPC定位内部通信，不是一个通用的RPC框架，并且使用的量非常小，所以这点消耗也就可以忽略了，还有Spark Structured Streaming使用该序列化方式，其性能还是可以满足要求的。

![img](https://pic3.zhimg.com/80/v2-e0bea5763ff3fa1df56c28009ca350aa_hd.png)

另外，作者在[kraps-rpc](https://link.zhihu.com/?target=https%3A//github.com/neoremind/kraps-rpc%234-performance-test)中还给Spark-rpc做了一次性能测试，具体可以参考[github](https://link.zhihu.com/?target=https%3A//github.com/neoremind/kraps-rpc%234-performance-test)。



## 总结

作者从好奇的角度来深度挖掘了下Spark RPC的内幕，并且从2.1版本的Spark core中独立出了一个专门的项目[Kraps-rpc](https://link.zhihu.com/?target=https%3A//github.com/neoremind/kraps-rpc)，放到了github以及发布到Maven中央仓库做学习使用，提供了比较好的上手文档、参数设置和性能评估，在整合kraps-rpc还发现了一个小的改进点，给Spark提了一个PR——[[SPARK-21701](https://link.zhihu.com/?target=https%3A//github.com/apache/spark/pull/18964)]，已经被merge到了主干，算是contribute社区了（10086个开心）。

接着深入剖析了Spark RPC模块内的类组织关系，使用UML类图和时序图帮助读者更好的理解一些核心的概念，包括RpcEnv，RpcEndpoint，RpcEndpointRef等，以及I/O的设计模式，包括I/O多路复用，Reactor和Actor mailbox等，这里还是重点提下Spark RPC的设计哲学，利用netty强大的Socket I/O能力，构建一个异步的通信框架。最后，从TCP层的segment二进制角度分析了wire protocol。

# Spark RPC 到底是个什么鬼？

本文会为大家介绍Spark中的RPC通信机制，详细阐述“Spark RPC到底是个什么鬼？”，闲话少叙，让我们来进入Spark RPC的世界！

## Spark RPC三剑客

Spark RPC中最为重要的三个抽象（“三剑客”）为：`RpcEnv`、`RpcEndpoint`、`RpcEndpointRef`，这样做的好处有：

- 对上层的API来说，屏蔽了底层的具体实现，使用方便
- 可以通过不同的实现来完成指定的功能，方便扩展
- 促进了底层实现层的良性竞争，Spark 1.6.3中默认使用了Netty作为底层的实现，但Akka的依赖依然存在；而Spark 2.1.0中的底层实现只有Netty，这样用户可以方便的使用不同版本的Akka或者将来某种更好的底层实现

下面我们就结合Netty和“三剑客”来具体分析他们是如何来协同工作的。

## Send a message locally

我们通过Spark源码中的一个Test（[RpcEnvSuite.scala](https://link.jianshu.com/?t=https://github.com/apache/spark/blob/master/core/src/test/scala/org/apache/spark/rpc/RpcEnvSuite.scala)）来分析一下发送本地消息的具体流程，源码如下（对源码做了一些修改）：

```scala
  test("send a message locally") {
    @volatile var message: String = null
    val rpcEndpointRef = env.setupEndpoint("send-locally", new RpcEndpoint {
      override val rpcEnv = env

      override def receive = {
        //case msg: String => message = msg
        case msg: String => println(message)  //我们直接将接收到的消息打印出来
      }
    })
    rpcEndpointRef.send("hello")
    //下面是原来的代码
    //eventually(timeout(5 seconds), interval(10 millis)) {
    //  assert("hello" === message)
    //}
  }
```

为了方便理解，先把流程图贴出来，然后详细进行阐述：

![img](https://upload-images.jianshu.io/upload_images/4473093-41a79db809742cb3.jpg)

下面我们来详细阐述上例的具体过程：

首先是`RpcEndpoint`创建并注册的流程：（图中的蓝色线条部分）

- 1、创建`RpcEndpoint`，并初始化`rpcEnv`的引用（RpcEnv已经创建好，底层实际上是实例化了一个NettyRpcEnv，而NettyRpcEnv是通过工厂方法NettyRpcEnvFactory创建的）
- 2、实例化RpcEndpoint之后需要向RpcEnv注册该RpcEndpoint，底层实现是向NettyRpcEnv进行注册，而实际上是通过调用Dispatcher的registerRpcEndpoint方法向Dispatcher进行注册
- 3、具体的注册就是向endpoints、endpointRefs、receivers中插入记录：而receivers中插入的信息会被Dispatcher中的线程池中的线程执行：会将记录take出来然后调用Inbox的process方法通过模式匹配的方法进行处理，注册的时候通过匹配到OnStart类型的message，去执行RpcEndpoint的onStart方法（例如Master、Worker注册时，就要执行各自的onStart方法），本例中未做任何操作
- 4、注册完成后返回RpcEndpointRef，我们通过RpcEndpointRef就可以向其代表的RpcEndpoint发送消息

下面就是通过RpcEndpointRef向其代表的RpcEndpoint发送消息的具体流程：（图中的红色线条部分）

- 1、2、调用RpcEndpointRef的send方法，底层实现是调用Netty的NettyRpcEndpointRef的send方法，而实际上又是调用的NettyRpcEnv的send方法，发送的消息使用RequestMessage进行封装：
  ```scala
  nettyEnv.send(RequestMessage(nettyEnv.address, this, message))
  ```
- 3、4、NettyRpcEnv的send方法首先会根据RpcAddress判断是本地还是远程调用，此处是同一个RpcEnv，所以是本地调用，即调用Dispatcher的postOneWayMessage方法
- 5、postOneWayMessage方法内部调用Dispatcher的postMessage方法
- 6、postMessage会向具体的RpcEndpoint发送消息，首先通过endpointName从endpoints中获得注册时的EndpointData，如果不为空就执行EndpointData中Inbox的post(message)方法，向Inbox的mesages中插入一条InboxMessage，同时向receivers中插入一条记录，此处将Inbox单独画出来是为了方便大家理解
- 7、Dispatcher中的线程池会拿出一条线程用来循环receivers中的消息，首先使用take方法获得receivers中的一条记录，然后调用Inbox的process方法来执行这条记录，而process将messages中的一条InboxMessage（第6步中插入的）拿出来进行处理，具体的处理方法就是通过模式匹配的方法，匹配到消息的类型（此处是OneWayMessage），然后来执行RpcEndpoint中对应的receive方法，在此例中我们只打印出这条消息（步骤8）

至此，一个简单的发送本地消息的流程执行完成。

什么，上面的图太复杂了？我也觉得，下面给出一张简洁的图：

![img](https://upload-images.jianshu.io/upload_images/4473093-8cb4e4eccbc61bba.jpg)

我们通过NettyRpcEndpointRef来发出一个消息，消息经过NettyRpcEnv、Dispatcher、Inbox的共同处理最终将消息发送到NettyRpcEndpoint，NettyRpcEndpoint收到消息后进行处理（一般是通过模式匹配的方式进行不同的处理）

如果进一步的进行抽象就得到了我们刚开始所讲的“三剑客”：RpcEnv、RpcEndpoint、RpcEndpointRef

![img](https://upload-images.jianshu.io/upload_images/4473093-e2dc3415aff1c163.jpg)

RpcEndpointRef发送消息给RpcEnv，RpcEnv查询注册信息将消息路由到指定的RpcEndpoint，RpcEndpoint接收到消息后进行处理（模式匹配的方式）

RpcEndpoint的声明周期：constructor -> onStart -> receive* -> onStop

其中receive*包括receive和receiveAndReply

本文我们只是通过一个简单的测试程序分析了Spark Rpc底层的实现，集群中的其它通信（比如Master和Woker的通信）的原理和这个测试类似，只不过具体的发送方式有所不同（包括`ask`、`askWithRetry`等），而且远程发消息的时候使用了`OutBox`和`NIO`等相关的内容，感兴趣的朋友可以对源码进行详细的阅读，本文不一一说明，目的就是通过简单的测试理解大致流程，不再为“Spark Rpc到底是什么”而纠结，一句话总结：Spark Rpc就是Spark中对分布式消息通信系统的高度抽象。

本文参考和拓展阅读：

[spark源码](https://link.jianshu.com/?t=https://github.com/apache/spark)

[Netty官方网站](https://link.jianshu.com/?t=http://netty.io)

[Java NIO Tutorial](https://link.jianshu.com/?t=http://tutorials.jenkov.com/java-nio/index.html)