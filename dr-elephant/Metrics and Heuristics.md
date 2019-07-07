## Metrics

### Used Resources

The resource usage is the amount of resource used by your job in GB Hours.

#### Calculation

We define resource usage of a task as the product of container size of the task and the runtime of the task. The resource usage of a job can thus be defined as the sum of resource usage of all the mapper tasks and all the reducer tasks.

#### Example

```
Consider a job with: 
4 mappers with runtime {12, 15, 20, 30} mins. 
4 reducers with runtime {10 , 12, 15, 18} mins. 
Container size of 4 GB 
Then, 
Resource used by all mappers: 4 * (( 12 + 15 + 20 + 30 ) / 60 ) GB Hours = 5.133 GB Hours 
Resource used by all reducers: 4 * (( 10 + 12 + 15 + 18 ) / 60 ) GB Hours = 3.666 GB Hours 
Total resource used by the job = 5.133 + 3.6666 = 8.799 GB Hours 
```

### Wasted Resources

This shows the amount of resources wasted by your job in GB Hours or in the form of percentage of resources wasted.

#### Calculation

```
To calculate the resources wasted, we calculate the following: 
The minimum memory wasted by the tasks (Map and Reduce)
The runtime of the tasks (Map and Reduce)
The minimum memory wasted by a task is equal to the difference between the container size and maximum task memory(peak memory) among all tasks. The resources wasted by the task is then the minimum memory wasted by the task multiplied by the duration of the task. The total resource wasted by the job then will be equal to the sum of wasted resources of all the tasks. 
 
Let us define the following for each task: 

peak_memory_used := The upper bound on the memory used by the task. 
runtime := The run time of the task. 

The peak_memory_used for any task is calculated by finding out the maximum of physical memory(max_physical_memory) used by all the tasks and the virtual memory(virtual_memory) used by the task. 
Since peak_memory_used for each task is upper bounded by max_physical_memory, we can say for each task: 

peak_memory_used = Max(max_physical_memory, virtual_memory/2.1)
Where 2.1 is the cluster memory factor. 

The minimum memory wasted by each task can then be calculated as: 

wasted_memory = Container_size - peak_memory_used 

The minimum resource wasted by each task can then be calculated as: 

wasted_resource = wasted_memory * runtime
```

### Runtime

The runtime metrics shows the total runtime of your job.

#### Calculation

The runtime of the job is the difference between the time when the job was submitted to the resource manager and when the job finished.

> 作业的运行时间是作业提交给**资源管理器**的时间和作业完成时的时间差。

#### Example

Let the submit time of a job be 1461837302868 ms Let the finish time of the job be 1461840952182 ms The runtime of the job will be 1461840952182 - 1461837302868 = 3649314 ms or 1.01 hours

> 设作业的提交时间为**1461837302868**毫秒，作业的完成时间为**1461840952182**毫秒，那么，作业的运行时间将是`1461840952182—1461837302868＝3649314 ms`或**1.01**小时。

### Wait time

The wait time is the total time spent by the job in the waiting state.

> 等待时间是**作业**在等待状态中所花费的总时间。

#### Calculation

```
For each task, let us define the following: 

ideal_start_time := The ideal time when all the tasks should have started 
finish_time := The time when the task finished 
task_runtime := The runtime of the task 

- Map tasks
For map tasks, we have 

ideal_start_time := The job submission time 

We will find the mapper task with the longest runtime ( task_runtime_max) and the task which finished last ( finish_time_last ) 
The total wait time of the job due to mapper tasks would be: 

mapper_wait_time = finish_time_last - ( ideal_start_time + task_runtime_max) 

- Reduce tasks
For reducer tasks, we have 

ideal_start_time := This is computed by looking at the reducer slow start percentage (mapreduce.job.reduce.slowstart.completedmaps) and finding the finish time of the map task after which first reducer should have started
We will find the reducer task with the longest runtime ( task_runtime_max) and the task which finished last ( finish_time_last ) 

The total wait time of the job due to reducer tasks would be: 
reducer_wait_time = finish_time_last - ( ideal_start_time + task_runtime_max) 
```