## RpcEnv - RPC环境

> 怎么才能知道系统里有多少可用的endpoints？[参见开发RPC环境](https://jaceklaskowski.gitbooks.io/mastering-apache-spark/content/exercises/spark-exercise-custom-rpc-environment.html)

>An end point for the RPC that defines what functions to trigger given a message. It is guaranteed that `onStart`, `receive` and `onStop` will be called in sequence. The life-cycle of an endpoint is: `constructor -> onStart -> receive* -> onStop ` 
>
>Note: receive can be called concurrently. If you want receive to be thread-safe, please use `ThreadSafeRpcEndpoint`. 
>
>If any error is thrown from one of RpcEndpoint methods except onError, onError will be invoked with the cause. If `onError` throws an error, `RpcEnv` will ignore it.
>
> 如果从除了`onError`之外的`RpcEndpoint`方法之一抛出任何错误，那么`onError`将被调用。如果`onError`抛出一个错误，`RpcEnv`将忽略它。
>



**RPC Environment** (aka **RpcEnv**) is an environment for [RpcEndpoints](https://jaceklaskowski.gitbooks.io/mastering-apache-spark/content/spark-rpc-RpcEndpoint.html) to process messages. A RPC Environment manages the entire life cycle of RpcEndpoints:

- registers (sets up) endpoints (by name or uri)
- routes incoming messages to them
- stops them

A RPC Environment is defined by the **name**, **host**, and **port**. It can also be controlled by a **security manager**. You can create a RPC Environment using [RpcEnv.create](https://jaceklaskowski.gitbooks.io/mastering-apache-spark/content/spark-rpc.html#create) factory methods. The only implementation of RPC Environment is [Netty-based implementation](https://jaceklaskowski.gitbooks.io/mastering-apache-spark/content/spark-rpc-netty.html).

A [RpcEndpoint](https://jaceklaskowski.gitbooks.io/mastering-apache-spark/content/spark-rpc-RpcEndpoint.html) defines how to handle **messages** (what **functions** to execute given a message). RpcEndpoints register (with a name or uri) to `RpcEnv` to receive messages from [RpcEndpointRefs](https://jaceklaskowski.gitbooks.io/mastering-apache-spark/content/spark-RpcEndpointRef.html).

## RpcEndpointRef — Reference to RPC Endpoint

1. `RpcEndpointRef` is a reference to a [RpcEndpoint](https://jaceklaskowski.gitbooks.io/mastering-apache-spark/content/spark-rpc-RpcEndpoint.html) in a [RpcEnv](https://jaceklaskowski.gitbooks.io/mastering-apache-spark/content/spark-rpc.html). 它是线程安全的
2. `RpcEndpointRef` is **a serializable entity** and so you can send it over a network or save it for later use (it can however be deserialized using the owning `RpcEnv` only).
3. A `RpcEndpointRef` has [an address](https://jaceklaskowski.gitbooks.io/mastering-apache-spark/content/spark-RpcEndpointRef.html#rpcaddress) (a Spark URL), and a name.

You can send **asynchronous one-way messages** to the corresponding RpcEndpoint using [send](https://jaceklaskowski.gitbooks.io/mastering-apache-spark/content/spark-RpcEndpointRef.html#send) method. You can send a **semi-synchronous** message, i.e. "subscribe" to be notified when a response arrives, using `ask` method. You can also block the current calling thread for a response using `askWithRetry` method.

- `spark.rpc.numRetries` (default: `3`) - the number of times to retry connection attempts.
- `spark.rpc.retry.wait` (default: `3s`) - the number of milliseconds to wait on each retry.

It also uses [lookup timeouts](https://jaceklaskowski.gitbooks.io/mastering-apache-spark/content/spark-rpc.html#endpoint-lookup-timeout).