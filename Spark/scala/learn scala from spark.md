```scala
abstract class InternalRow {}
abstract class BaseGenericInternalRow extends InternalRow {}
class JoinedRow extends InternalRow {}
class UnsafeRow extends InternalRow{}
class SpecificInternalRow extends BaseGenericInternalRow {}
class MutableUnsafeRow extends BaseGenericInternalRow {}
class GenericInternalRow extends BaseGenericInternalRow {}
```
![img](https://g.gravizo.com/svg?
abstract class InternalRow {}
abstract class BaseGenericInternalRow extends InternalRow {}
class JoinedRow extends InternalRow {}
class UnsafeRow extends InternalRow{}
class SpecificInternalRow extends BaseGenericInternalRow {}
class MutableUnsafeRow extends BaseGenericInternalRow {}
class GenericInternalRow extends BaseGenericInternalRow {})

<img width="100%" height="100%" src='https://g.gravizo.com/svg?
abstract class InternalRow {}
abstract class BaseGenericInternalRow extends InternalRow {}
class JoinedRow extends InternalRow {}
class UnsafeRow extends InternalRow{}
class SpecificInternalRow extends BaseGenericInternalRow {}
class MutableUnsafeRow extends BaseGenericInternalRow {}
class GenericInternalRow extends BaseGenericInternalRow {}
'>

<img src='https://g.gravizo.com/svg?
abstract%20class%20InternalRow%20%7B%7D%0Aabstract%20class%20BaseGenericInternalRow%20extends%20InternalRow%20%7B%7D%0Aclass%20JoinedRow%20extends%20InternalRow%20%7B%7D%0Aclass%20UnsafeRow%20extends%20InternalRow%7B%7D%0Aclass%20SpecificInternalRow%20extends%20BaseGenericInternalRow%20%7B%7D%0Aclass%20MutableUnsafeRow%20extends%20BaseGenericInternalRow%20%7B%7D%0Aclass%20GenericInternalRow%20extends%20BaseGenericInternalRow%20%7B%7D
'>
