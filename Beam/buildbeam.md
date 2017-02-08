protoc-3.1.0-linux-x86_64.exe    需要glibc 2.14

easy_install --upgrade pip

PIP , nose, urllib3



分享Centos6.5升级glibc过程 发布于 1 年前  作者 [ncuzp](https://cnodejs.org/user/ncuzp)  11833 次浏览  最后一次编辑是 5 个月前  来自 分享

上次看到有同学对Centos系统 glibc升级有点疑问, 不过相对来说glibc升级还是比较简单的, 网上也有很多介绍文章, 这里整理了个安装过程供大家参考下  [阅读原文](http://hardog.net/2016/03/06/Centos6-5-glibc-%E5%8D%87%E7%BA%A7/)

# 场景需求

默认的Centos6.5 glibc版本最高为2.12, 而在进行Nodejs开发时项目所依赖的包往往需要更高版本的glibc库支持, 因此在不升级系统的前提下, 需要主动更新系统glibc库. 一般遇到错误`libc.so.6: version GLIBC_2.14 not found`时表示需要对glibc进行升级了.

# glibc版本

查看系统glibc库版本可使用如下命令:

```
$ strings /lib64/libc.so.6 |grep GLIBC_
```

Centos6.5输出如下glibc版本列表, 从此图可以看出系统最高支持glibc的2.12版本:![glibc-2.12](http://hardog.net/images/post/20160306/glibc2.12.png)另外, 执行`$ ll /lib64/libc**`可以看到此时的libc.so.6是libc-2.12.so的别名, 如下图所示:![glibc-2.12-ll](http://hardog.net/images/post/20160306/ll2.12.png)

# glibc安装

首先, 点击此处下载glibc[点击下载](http://ftp.gnu.org/gnu/glibc/glibc-2.14.tar.gz), 得到`glibc-2.14.tar.gz`使用如下命令解压`glibc-2.14.tar.gz`:

```
$ tar -xzvf glibc-2.14.tar.gz
```

当前目录下得到目录`glibc-2.14`, 执行`$cd glibc-2.14`命令进入目录, 依次执行如下命令编译安装glibc-2.14:

```
$ mkdir build	// 在glibc-2.14目录下建立build文件夹
$ cd build		// 进入build目录
$ ../configure --prefix=/opt/glibc-2.14 // 配置glibc并设置当前glibc-2.14安装目录
$ make && make install		// 编译安装glibc-2.14库
```

# glibc软链

安装完成后, 建立软链指向glibc-2.14, 执行如下命令:

```
$ rm -rf /lib64/libc.so.6 			// 先删除先前的libc.so.6软链
$ ln -s /opt/glibc-2.14/lib/libc-2.14.so /lib64/libc.so.6
```

**注意**

删除`libc.so.6`之后可能导致系统命令不可用的情况, 可使用如下方法解决:

```
$ LD_PRELOAD=/opt/glibc-2.14/lib/libc-2.14.so  ln -s /opt/glibc-2.14/lib/libc-2.14.so /lib64/libc.so.6
```

如果上述更新失败可使用如下命令还原:

```
$ LD_PRELOAD=/lib64/libc-2.12.so ln -s /lib64/libc-2.12.so /lib64/libc.so.6    // libc-2.12.so 此项是系统升级前的版本
```

> 感谢`丁文翔 `指出本文遗漏事项!

此时查看系统glibc版本如下图所示:![glibc](http://hardog.net/images/post/20160306/glibc.png)可以看到当前glibc最高版本为2.14, libc.so.6软链指向如下图所示:![ll](http://hardog.net/images/post/20160306/ll.png)