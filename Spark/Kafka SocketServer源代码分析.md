# Kafka SocketServer源代码分析

本文将详细分析Kafka SocketServer的相关源码。

## 总体设计

Kafka `SocketServer`是基于Java NIO来开发的，采用了==Reactor模式==，其中包含了：
1. 1个*Acceptor*负责接受客户端请求，
2. N个*Processor*负责读写数据，
3. M个*Handler*来处理业务逻辑。

在Acceptor和Processor，Processor和Handler之间都有队列来缓冲请求。


## kafka.network.Acceptor

这个类继承了`AbstractServerThread`，实现了Runnable接口，因此它是一个线程类。它的==主要职责是监听客户端的连接请求，并建立和客户端的数据传输通道，然后为这个客户端指定一个Processor，它的工作就到此结束==，这样它就可以去响应下一个客户端的连接请求了。它的`run`方法的主要逻辑如下： 

1. 首先在ServerSocketChannel上注册OP_ACCEPT事件：

  ```scala
  serverChannel.register(nioSelector, SelectionKey.OP_ACCEPT)
  ```

2. 然后开始等待客户端的连接请求：

  ```scala
  while (isRunning) {
    try {
      val ready = nioSelector.select(500)
      ...
    }
    catch {
      ...
    }
  }
  ```

3. 如果有连接进来，则将其分配给当前的processor，并且把当前processor指向下一个processor，也就是说它采用了Round Robin的方式来选择processor

  ```scala
  if (ready > 0) {
    val keys = nioSelector.selectedKeys()
    val iter = keys.iterator()
    while (iter.hasNext && isRunning) {
      try {
        val key = iter.next
        iter.remove()
        if (key.isAcceptable)
          accept(key, processors(currentProcessor))
        else
          throw new IllegalStateException("Unrecognized key state for acceptor thread.")
  
        // round robin to the next processor thread
        currentProcessor = (currentProcessor + 1) % processors.length
      } catch {
        case e: Throwable => error("Error while accepting connection", e)
      }
    }
  }
  ```

接下来看看Acceptor的accept方法的简化代码（省掉了异常处理）。先说点相关的知识，==SelectionKey是表示一个Channel和Selector的注册关系==。`Acceptor`中的`nioSelector`，只监听客户端的连接请求，即`serverChannel`上的`OP_ACCEPT`事件，当`nioSelector`的`select`方法返回时，则表示注册在它上面的`serverChannel`发生了对应的事件,也就是`OP_ACCEPT`，表示这个ServerSocketChannel发生客户端连接事件了。

因此，Acceptor的accept方法的处理逻辑为：

1. **首先**，通过SelectionKey来拿到对应的ServerSocketChannel；

2. **然后**，调用其accept方法来建立和客户端的连接；

3. **最后**，拿到对应的SocketChannel并交给了processor。

此时，Acceptor的任务就完成了，开始去处理下一个客户端的连接请求。Processor的accept方法的逻辑将在下一节介绍。

```scala
  def accept(key: SelectionKey, processor: Processor) {
    val serverSocketChannel = key.channel().asInstanceOf[ServerSocketChannel]
    val socketChannel = serverSocketChannel.accept()
    socketChannel.configureBlocking(false)
    socketChannel.socket().setTcpNoDelay(true)
    socketChannel.socket().setSendBufferSize(sendBufferSize)

    processor.accept(socketChannel)
  }
```

## kafka.network.Processor

Processor也是继承自AbstractServerThread并实现Runnable接口，所以也是一个线程类。它的主要职责是负责**从客户端读取数据和将响应返回给客户端，它本身不处理具体的业务逻辑，也就是说它并不认识它从客户端读取回来的数据**。==每个Processor都有一个Selector，用来监听多个客户端，因此可以非阻塞地处理多个客户端的读写请求==。

### 处理新建立的连接

从上一节中可以看到，Acceptor会把多个客户端的数据连接SocketChannel分配一个Processor，因此每个Processor内部都有一个队列来保存这些新来的数据连接：

```scala
  private val newConnections = new ConcurrentLinkedQueue[SocketChannel]()
```

Processor的accpet方法（Acceptor会调用它）的代码如下，它就把一个SocketChannel放到队列中，然后唤醒Processor的selector。

```scala
def accept(socketChannel: SocketChannel) {
    newConnections.add(socketChannel)
    wakeup()
}
```

>  **注意**：==这个方法不是在Processor的线程里面执行的，而是在Acceptor线程里面执行的==。

在run方法中，它首先调用方法configureNewConnections，如果有队列中有新的SocketChannel，则它首先将其OP_READ事情注册到该Processor的selector上面。

```scala
  private def configureNewConnections() {
    while(newConnections.size() > 0) {
      val channel = newConnections.poll()
      channel.register(selector, SelectionKey.OP_READ)
    }
  }
```

### 读取客户端的数据

在Processor的run方法中，它也是调用selector的select方法来监听客户端的数据请求，简化的代码如下：

```scala
  val ready = selector.select();
  if(ready > 0) {
    val keys = selector.selectedKeys()
    val iter = keys.iterator()
    while(iter.hasNext && isRunning) {
      var key: SelectionKey = null
      key = iter.next
      iter.remove()
      if(key.isReadable)
        read(key)
    }
  }
```

从上面的逻辑中可以看到，当一个客户端数据传输过来，read方法会被调用，下面是read方法的简化代码。

```scala
def read(key: SelectionKey) {
    val socketChannel = channelFor(key)
    var receive = key.attachment.asInstanceOf[Receive]
    if(key.attachment == null) {
      receive = new BoundedByteBufferReceive(maxRequestSize)
      key.attach(receive)
    }
    val read = receive.readFrom(socketChannel)
    if(read < 0) {
      close(key)
    } else if(receive.complete) {
      val req = RequestChannel.Request(processor = id, requestKey = key, buffer = receive.buffer, startTimeMs = time.milliseconds, remoteAddress = address)
      requestChannel.sendRequest(req)
      key.attach(null)
      // explicitly reset interest ops to not READ, no need to wake up the selector just yet
      key.interestOps(key.interestOps & (~SelectionKey.OP_READ))
    } else {
      // more reading to be done
      key.interestOps(SelectionKey.OP_READ)
      wakeup()
    }
  }
```

read方法的流程为： 
a. 首先从SelectionKey中拿到对应的SocketChannel，并且取出attach在SelectionKey上的Receive对象，如果是第一次读取，Receive对象为null，则创建一个BoundedByteBufferReceive，由它来处理具体的读数据的逻辑。可以看到每个客户端都有一个Receive对象来读取数据。 
b. 如果数据从客户端读取完毕(receive.complete)，则将读取的数据封装成Request对象，并添加到requestChannel中去。如果没有读取完毕（可能是客户端还没有发送完或者网络延迟），那么就让selector继续监听这个通道的OP_READ事件。

因此，我们知道具体读取数据是在BoundedByteBufferReceive里面完成的，而读取完成后要交给RequestChannel，接下来我们来看这两部分的代码。



#### BoundedByteBufferReceive

BoundedByteBufferReceive中有2个ByteBuffer，分别是sizeBuffer和contentBuffer，其中sizeBuffer是固定的4个字节，表示这次发送来的数据总共有多大，随后再读取对应大小的数据放到contentBuffer中。

主要的处理逻辑都是在readFrom这个方法中，简化的代码如下：

```
  def readFrom(channel: ReadableByteChannel): Int = {
    var read = 0
    // have we read the request size yet?
    if(sizeBuffer.remaining > 0)
      read += Utils.read(channel, sizeBuffer)

    // have we allocated the request buffer yet?
    if(contentBuffer == null && !sizeBuffer.hasRemaining) {
      sizeBuffer.rewind()
      val size = sizeBuffer.getInt()
      contentBuffer = byteBufferAllocate(size)
    }

    // if we have a buffer read some stuff into it
    if(contentBuffer != null) {
      read = Utils.read(channel, contentBuffer)
      // did we get everything?
      if(!contentBuffer.hasRemaining) {
        contentBuffer.rewind()
        complete = true
      }
    }
    read
  }
```

首先检查sizeBuffer是不是都读满了，没有的话就从对应的channel中读取数据放到sizeBuffer中，就是下面这句，它会从channel中读取最多等同于sizeBuffer中剩下空间数量的数据。

```
Utils.read(channel, sizeBuffer)
```

当sizeBuffer读取完成了，就知道真正的数据有多少了，因此就是按照这个大小来分配contentBuffer了。紧接着就是从channel读取真正的数据放到contentBuffer中，当把contentBuffer读满以后就停止了并把complet标记为true。因此，可以看到客户端在发送数据的时候需要先发送这次要发送数据的大小，然后再发送对应的数据。

这样设计是因为java NIO在从channel中读取数据的时候只能指定读多少，而且数据也不是一次就能全部读取完成的，用这种方式来保证数据都读进来了。

到此为止，我们知道了Processor是如何读取数据的。简而言之，Processor通过selector来监听它负责的那些数据通道，当通道上有数据可读时，它就是把这个事情交给BoundedByteBufferReceive。BoundedByteBufferReceive先读一个int来确定数据量有多少，然后再读取真正的数据。那数据读取进来后又是如何被处理的呢？下一节来分析对应的代码。



#### kafka.network.RequestChannel

RequestChannel是Processor和Handler交换数据的地方。它包含了一个队列requestQueue用来存放Processor加入的Request，Handler会从里面取出Request来处理；它还为每个Processor开辟了一个respondQueue，用来存放Handler处理了Request后给客户端的Response。下面是一些源码：

初始化requestQueue和responseQueues的代码：

```
  private val requestQueue = new ArrayBlockingQueue[RequestChannel.Request](queueSize)
  private val responseQueues = new Array[BlockingQueue[RequestChannel.Response]](numProcessors)
  for(i <- 0 until numProcessors)
    responseQueues(i) = new LinkedBlockingQueue[RequestChannel.Response]()
```

sendRequest方法：Processor在读取完数据后，将数据封装成一个Request对象然后调用这个方法将Request添加到requestQueue中。如果requestQueue满的话，这个方法会阻塞在这里直到有Handler取走一个Request。

```
  def sendRequest(request: RequestChannel.Request) {
    requestQueue.put(request)
  }
```

receiveRequest方法：Handler从requestQueue中取出Request，如果队列为空，这个方法会阻塞在这里直到有Processor加入新的Request。

```
  def receiveRequest(): RequestChannel.Request =
    requestQueue.take()
```

类似的sendResponse和receiveResponse就写在这里，唯一的区别就是添加和取出Response的时候要指定Processor的id因为每个Processor都有其对应的responseQueue。



### 返回数据给客户端

Processor不仅负责从客户端读取数据，还要将Handler的处理结果返回给客户端。在Processor的run方法（Processor是一个线程类），它会调用processNewResponses()来处理Handler的提供给客户端的Response。简化的代码如下：

```
  private def processNewResponses() {
    var curr = requestChannel.receiveResponse(id)
    while(curr != null) {
      val key = curr.request.requestKey.asInstanceOf[SelectionKey]
      curr.responseAction match {
        case RequestChannel.SendAction => {
          key.interestOps(SelectionKey.OP_WRITE)
          key.attach(curr)
        }
      }
    curr = requestChannel.receiveResponse(id)
    }
  }
```

它依次把requestChannel中responseQueue的Response取出来，然后将对应通道的OP_WRITE事件注册到selector上。这和上面的configureNewConnections很类似。

然后当selector的select方法返回时，检查是否有通道是WRITEABLE，如果有则调用Processor中的write方法。在write方法中，Processor又将具体写数据的任务交给了Response中的Send对象。这和读取数据的处理方式非常类似，就不细说了。

到此为止，我们分析了Processor是如何从客户端读取数据的，以及如何将Handler处理后的响应返回给客户端。下一节将简要分析一下Handler。



## kafka.server.KafkaRequestHandler

Handler的职责是从requestChannel中的requestQueue取出Request，处理以后再将Response添加到requestChannel中的responseQueue中。 因为Handler是处理具体业务的，所以它可以有不同的实现，或者把具体的处理再外包出去。我们就简要看一下KafkaRequestHandler是如何做的。

KafkaRequestHandler实现了Runnable，因此是个线程类，除去错误处理的代码后，其run方法可以简化为如下代码，它把所有的处理逻辑都交给了KafkaApis：

```
  def run() {
    while(true) {
      var req : RequestChannel.Request = requestChannel.receiveRequest(300)
      apis.handle(req)
    }
  }
```

因为KafkaApis是和具体业务相关，以后再分析相关的代码。



## kafka.network.SocketServer

在分析完Acceptor、Processor和Handler之后，整个SocketServer就分析得差不多了。SocketServer这个类就无非是把前面几个类组合在一起。

首先构造出RequestChannel，

```
val requestChannel = new RequestChannel(numProcessorThreads, maxQueuedRequests)
```

然后，startup方法中先启动Processors，后启动Acceptor，

```
for(i <- 0 until numProcessorThreads) {
  processors(i) = new Processor(...)
  Utils.newThread("kafka-network-thread-%d-%d".format(port, i), processors(i), false).start()
}

// start accepting connections
this.acceptor = new Acceptor(host, port, processors, sendBufferSize, recvBufferSize, quotas)
```

在shutdown方法中则是先停止Acceptor后停止Processor。

那什么时候启动Handler呢？这和SocketServer真没有什么关系，因为SocketServer是一个底层的通讯设施，以它为基础来构建上层应用的，因此上层应用会创建SocketServer和Handler从而让他们一起工作，那在Kafka里面，这个上层应用在哪里？答案就是kafka.server.KafkaServer的startUp方法中，相关的代码很直白就不写在这里了。



## 小结

本文详细分析了Kafka中SocketServer中的Acceptor和Processor的主要代码，以及它们是如何在一起构建上层应用的。Kafka采用是经典的Reactor模式，也就是1个Acceptor响应客户端的连接请求，N个Processor来读取数据，从Kafka的实践可见，这种模式可以构建出高性能的服务器。