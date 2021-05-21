# High-Level Overview

The Arrow C++ library is comprised of different parts, each of which serves a specific purpose.

## The physical layer

**Memory management** abstractions provide a uniform API over memory that may be allocated through various means, such as heap allocation, the memory mapping of a file or a static memory area. In particular, the **buffer** abstraction represents a contiguous area of physical data.

## The one-dimensional layer

**Data types** govern the *logical* interpretation of *physical* data. Many operations in Arrow are parametered, at compile-time or at runtime, by a data type.

**Arrays** assemble one or several buffers with a data type, allowing to view them as a logical contiguous sequence of values (possibly nested).

**Chunked arrays** are a generalization of arrays, comprising several same-type arrays into a longer logical sequence of values.

>**数据类型**控制物理数据的逻辑解释。Arrow中的许多操作在编译时或运行时由数据类型参数化。
>
>数组用一个数据类型组合一个或多个缓冲区，允许将它们作为一个逻辑连续的值序列（可能是嵌套的）来查看。
>
>分块数组是数组的泛化，它将几个相同类型的数组组成一个较长的逻辑值序列。

## The two-dimensional layer

**Schemas** describe a logical collection of several pieces of data, each with a distinct name and type, and optional metadata.

**Tables** are collections of chunked array in accordance to a schema. They are the most capable dataset-providing abstraction in Arrow.

**Record batches** are collections of contiguous arrays, described by a schema. They allow incremental construction or serialization of tables.

## The compute layer

**Datums** are flexible dataset references, able to hold for example an array or table reference.

**Kernels** are specialized computation functions running in a loop over a given set of datums representing input and output parameters to the functions.

## The IO layer

**Streams** allow untyped sequential or seekable access over external data of various kinds (for example compressed or memory-mapped).

## The Inter-Process Communication (IPC) layer

A **messaging format** allows interchange of Arrow data between processes, using as few copies as possible.

## The file formats layer

Reading and writing Arrow data from/to various file formats is possible, for example **Parquet**, **CSV**, **Orc** or the Arrow-specific **Feather** format.

## The devices layer

Basic **CUDA** integration is provided, allowing to describe Arrow data backed by GPU-allocated memory.

## The filesystem layer

A filesystem abstraction allows reading and writing data from different storage backends, such as the local filesystem or a S3 bucket.