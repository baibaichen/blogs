# PostgreSQL's handling of fsync() errors is unsafe and risks data loss at least on XFS

TL;DR: Pg should PANIC on fsync() EIO return. Retrying fsync() is not OK at least on Linux. When fsync() returns success it means "all writes since the last fsync have hit disk" but we assume it means "all writes since the last SUCCESSFUL fsync have hit disk".

Pg wrote some blocks, which went to OS dirty buffers for writeback. Writeback failed due to an underlying storage error. The block I/O layer and XFS marked the writeback page as failed (AS_EIO), but had no way to tell the app about the failure. When Pg called fsync() on the FD during the next checkpoint, fsync() returned EIO because of the flagged page, to tell Pg that a previous async write failed. Pg treated the checkpoint as failed and didn't advance the redo start position in the control file.

> TL; DR：==**Pg应该在fsync（）EIO返回时进行PANIC。 至少在Linux上，重试fsync（）是不可行的。**== 当fsync（）返回成功时，它表示“自上次fsync以来所有写入均已命中磁盘”，但我们假定它的意思是“自上次成功fsync以来所有写入均已命中磁盘”。
>
> Pg编写了一些块，这些块进入OS脏缓冲区进行写回。 由于基础存储错误，写回失败。 块I / O层和XFS将回写页标记为失败（AS_EIO），但是无法告知应用程序失败。 当Pg在下一个检查点期间在FD上调用fsync（）时，由于标记了页面，fsync（）返回EIO，以告知Pg先前的异步写入失败。 Pg将检查点视为失败，并且未推进控制文件中的重做开始位置。

All good so far.

But then we retried the checkpoint, which retried the fsync(). The retry succeeded, because the prior fsync() *cleared the AS_EIO bad page flag*.

The write never made it to disk, but we completed the checkpoint, and merrily carried on our way. Whoops, data loss.

> 写操作没有成功到磁盘上，但是我们完成了检查点，然后愉快地继续前进。哎呀，数据丢失了。

The clear-error-and-continue behaviour of fsync is not documented as far as I can tell. Nor is fsync() returning EIO unless you have a very new linux man-pages with the patch I wrote to add it. But from what I can see in the POSIX standard we are not given any guarantees about what happens on fsync() failure at all, so we're probably wrong to assume that retrying fsync( ) is safe.

If the server had been using ext3 or ext4 with errors=remount-ro, the problem wouldn't have occurred because the first I/O error would've remounted the FS and stopped Pg from continuing. But XFS doesn't have that option. There may be other situations where this can occur too, involving LVM and/or multipath, but I haven't comprehensively dug out the details yet.

It proved possible to recover the system by faking up a backup label from before the first incorrectly-successful checkpoint, forcing redo to repeat and write the lost blocks. But ... what a mess.

I posted about the underlying fsync issue here some time ago: https://stackoverflow.com/q/42434872/398670, but haven't had a chance to follow up about the Pg specifics.

> 如果服务器使用的ext3或ext4的错误= remount-ro，则不会发生此问题，因为第一个I / O错误将重新安装FS并阻止Pg继续运行。 但是XFS没有该选项。 在其他情况下，也可能会发生这种情况，包括LVM和/或多路径，但我尚未全面挖掘细节。
>
> 事实证明，可以通过在第一个不成功的检查点之前伪造一个备份标签来恢复系统，从而迫使重做重复并写入丢失的块。 但是...真是一团糟。
>
> 我前一段时间在这里发布了有关底层fsync问题的信息：https://stackoverflow.com/q/42434872/398670，但还没有机会跟进Pg的细节。

I've been looking at the problem on and off and haven't come up with a good answer. I think we should just PANIC and let redo sort it out by repeating the failed write when it repeats work since the last checkpoint.

> 我一直在断断续续地研究问题，却没有给出好的答案。 我认为我们应该只是PANIC，并在重复自上一个检查点以来的工作时，通过重复失败的写入操作来重做它。

The API offered by async buffered writes and fsync offers us no way to find out which page failed, so we can't just selectively redo that write. I think we do know the relfilenode associated with the fd that failed to fsync, but not much more. So the alternative seems to be some sort of potentially complex online-redo scheme where we replay WAL only the relation on which we had the fsync() error, while otherwise servicing queries normally. That's likely to be extremely error-prone and hard to test, and it's trying to solve a case where on other filesystems the whole DB would grind to a halt anyway.

> 异步缓冲写入和fsync提供的API无法让我们找出哪个页面失败，因此我们不能只是选择性地重做该写入。 我想我们确实知道与fd关联的relfilenode未能进行fsync，但仅此而已。 因此，替代方法似乎是某种潜在的复杂的在线重做方案，在该方案中，我们仅重播发生了fsync（）错误的关系，而WAL仅正常处理查询。 这可能极易出错，并且很难测试，并且它正在尝试解决以下情况：在其他文件系统上，整个数据库总会停止运行。

I looked into whether we can solve it with use of the AIO API instead, but the mess is even worse there - from what I can tell you can't even reliably guarantee fsync at all on all Linux kernel versions.

> 我研究了我们是否可以使用 AIO API 来解决它，但是那里的情况更糟-从我可以告诉你的是，你甚至不能在所有Linux内核版本上可靠地保证fsync。

We already PANIC on fsync() failure for WAL segments. We just need to do the same for data forks at least for EIO. This isn't as bad as it seems because AFAICS fsync only returns EIO in cases where we should be stopping the world anyway, and many FSes will do that for us.

> 我们已经对WAL段的fsync（）失败进行了PANIC。 至少对于EIO，我们只需要对数据分支执行相同的操作。 这并不像看起来那样糟糕，因为据我所知，fsync仅在我们无论如何都要停止世界的情况下才返回EIO，而许多FS会为我们做到这一点。

There are rather a lot of pg_fsync() callers. While we could handle this case-by-case for each one, I'm tempted to just make pg_fsync() itself intercept EIO and PANIC. Thoughts?

> 有很多pg_fsync（）调用者。 虽然我们可以逐个处理此情况，但我还是想让pg_fsync（）本身截获EIO和PANIC。 有什么想法吗？