```scala
abstract class InternalRow {}
class UnsafeRow extends InternalRow{}
abstract class BaseGenericInternalRow extends InternalRow {}
class JoinedRow extends InternalRow {}
class SpecificInternalRow extends BaseGenericInternalRow {}
class MutableUnsafeRow extends BaseGenericInternalRow {}
class GenericInternalRow extends BaseGenericInternalRow {}
```
![img](https://g.gravizo.com/svg?
abstract class InternalRow {}
class UnsafeRow extends InternalRow{}
abstract class BaseGenericInternalRow extends InternalRow {}
class JoinedRow extends InternalRow {}
class SpecificInternalRow extends BaseGenericInternalRow {}
class MutableUnsafeRow extends BaseGenericInternalRow {}
class GenericInternalRow extends BaseGenericInternalRow {})

<img width="100%" height="100%" src='https://g.gravizo.com/svg?
abstract class InternalRow {}
class UnsafeRow extends InternalRow{}
abstract class BaseGenericInternalRow extends InternalRow {}
class JoinedRow extends InternalRow {}
class SpecificInternalRow extends BaseGenericInternalRow {}
class MutableUnsafeRow extends BaseGenericInternalRow {}
class GenericInternalRow extends BaseGenericInternalRow {}
'>

