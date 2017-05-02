https://docs.google.com/document/d/1bao-5B6uBuf-kwH1meenAuXXS0c9cBQ1B2J59I3FiyI

#API

## Primitive Transforms

**A primitive transform** is a PTransform that has been chosen to have no default implementation in terms of other PTransforms. We will likely have further writing and discussion about these as the Beam model evolves, but for now this document explicitly selects transforms to be primitive.

A primitive transform therefore must be implemented directly by a pipeline runner in terms of (possibly composite) pipeline-runner-specific concepts. (composites may still be implemented this way, or may be left as-is and run as multiple primitives).

**原语变换**无默认实现，因为它没有选择基于其它的PTransform来实现。随着Beam模型的发展，我们可能会进一步撰写和讨论这些问题，但是本文目前明确地选择某些PTransform是**原语变换**。

因此，原语转换必须在Pipeline runner内部，根据Pipeline runner的特定概念（在pipeline runner内部可能是组合的）直接实现。组合变换也可以采用这种方式来实现，或者原样保留，做为多个原语变换来运行。

## What does a runner author need to do?

Just PipelineRunner.run() :-) 

In order to succeed at this, you will need to:

- Replace selected transforms as desired (primitives or composites). Previously, you'd do a lot of this by intercepting things with PipelineRunner.apply(). **Now you will use the API presented here for graph surgery**.
- Run or compile the resulting specialized pipeline.

**In order to succeed at the latter, you will need to address the higher-order aspects, where the runner provides values to the user that the user then calls.**

只需实现`PipelineRunner.run()`，:)

要想搞定，你需要：

- 根据需要替换选定的变换（原语变换或复合复合变换）。以前，您通过截取`PipelineRunner.apply()`来做很多事情。现在使用此处提供的API进行DAG**移植手术**。
- 运行或编译生成的专门Pipeline。







