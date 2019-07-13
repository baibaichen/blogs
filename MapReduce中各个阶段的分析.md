# [MapReduce中各个阶段的分析](https://blog.csdn.net/wyqwilliam/article/details/84669579)

MapReduce中各个阶段的分析：

![img](https://img-blog.csdnimg.cn/20181201150922771.jpg?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3d5cXdpbGxpYW0=,size_16,color_FFFFFF,t_70)

在MapReduce的各个阶段：

在文件被读入的时候调用的是Inputformat方法读入的。inputformat——>recordreader——>read（一行） 。在这里读入一行，返回的是(k,v）的形式，key是行号的偏移量，value的值是这一行的内容。

在上述的过程中，之后是调用map方法，将以上内容转换成正真的（key，value）的形式。key为值，value为1，然后调用context.write方法将该数据写出来。

从map端写出来之后具体写到outputcollector收集器中。

经过outputcollector收集器之后会写入到环形缓缓区中。在环形缓冲区中会做几件事情，①排序，调用的是快速排序法。②分区，调用的是hashpartitioner分区。达到80%之后会溢写磁盘。分区中hashpartitioner分区的时候是按照key进行hash取值的。相同的hash值会在一个分区中，取几个分区可以人为设定。排序的时候的两个依据是partition和key两个作为依据的。同一个partition中是按照key进行排序的。

环形缓冲区中的数据会spill溢写到磁盘中。

在溢写到磁盘之后会merge，归并排序，将多个小文件merge成大文件的。所以合并之后的大文件还是分区，并且分区内部是有序的。

在这里map阶段就算结束了，后边就是reduce阶段了，reduce阶段会去map阶段merge之后的文件中拿数据，按照相同的分区去取数据。reduce中是有分区号的，将数据拿过来之后会存储在本地磁盘中。

取完数据之后会按照相同的分区，再将取过来的数据进行merge归并排序，大文件的内容按照key有序进行排序。

之后会调用groupingcomparator进行分组，之后的reduce中会按照这个分组，每次取出一组数据，调用reduce中自定义的方法进行处理。

最后调用outputformat会将内容写入到文件中。

![img](https://img-blog.csdnimg.cn/20181201170821184.png?x-oss-process=image/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3d5cXdpbGxpYW0=,size_16,color_FFFFFF,t_70)

![img](https://img-blog.csdnimg.cn/20181201172318218.png)

 

在这里map端输入的（key，value）的类型我们人为可以指定，一般会设置为（LongWritable,Text）,为什么会是longwritable呢，因为map端正真进来的时候是切分之后的文件。key的值是读取的行的偏移量。 
