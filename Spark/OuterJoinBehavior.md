# [OuterJoinBehavior](https://cwiki.apache.org/confluence/display/Hive/OuterJoinBehavior)

|                 | Preserved Row Table | Null Supplying Table |
| :-------------- | :------------------ | :------------------- |
| Join Predicate  | Case J1: Not Pushed | Case J2: Pushed      |
| Where Predicate | Case W1: Pushed     | Case W2: Not Pushed  |

假设数据为

1. 左表：

   | s1.Key |
   | ------ |
   | 1      |
   | 2      |
   
2. 右表：

   | s2.Key |
   | ------ |
   | 1      |
   | 2      |

一般 `select * from a left join b on join_condition where condition ` 分为两步

1. 生成 join 后的数据集
2. 过滤数据集

## Case J1: Join Predicate on Preserved Row Table

**不能下推**

```SQL
select s1.key, s2.key 
from src s1 left join src s2 on s1.key > '2';
```
### 第一步后的结果：

| s1.key | S2.key |
| ------ | ------ |
| 1      | null   |
| 2      | null   |

### 最后的结果：

| s1.key | S2.key |
| ------ | ------ |
| 1      | null   |
| 2      | null   |

**不能下推**，原因是`out join` 上的条件不过滤 **Preserved Row Table** 上的数据

## Case W1: Where Predicate on Preserved Row Table

**可以下推**

```SQL
select s1.key, s2.key 
from src s1 left join src s2 
where s1.key > '2';
```

### 第一步后的结果：

| s1.key | S2.key |
| ------ | ------ |
| 1      | 1   |
| 2      | 2   |
| 1      | 1   |
| 2      | 2   |

### 最后的结果：

最后的结果为**空集**，所以下推没有问题，即

```sql
select s1.key, s2.key 
from (select key from src where key > '2') s1 left join src s2;
```


## Case J2: Join Predicate on Null Supplying Table

**可以下推**

```SQL
select s1.key, s2.key 
from src s1 left join src s2 on s2.key > '2';
```
### 第一步后的结果：

| s1.key | S2.key |
| ------ | ------ |
| 1      | null   |
| 2      | null   |

### 最后的结果：

| s1.key | S2.key |
| ------ | ------ |
| 1      | null   |
| 2      | null   |

和下推的结果一样
```sql
select s1.key, s2.key 
from src s1 left join (select key from src where key > '2') s2;
```

## Case W2: Where Predicate on Null Supplying Table

**不能下推**

```SQL
select s1.key, s2.key 
from src s1 left join src s2 
where s2.key > '2';
```

### 第一步后的结果：

| s1.key | S2.key |
| ------ | ------ |
| 1      | 1   |
| 2      | 2   |
| 1      | 1   |
| 2      | 2   |

### 最后的结果：

最后的结果为**空集**，所以不能下推，因为如果下推，结果集不是空集。

## TODO

这里要查下 SQL 标准，对于 **left out join**，当 join 条件能匹配成功，无法不匹配时，会如何产生产生结果集，
```SQL
-- sql 1
select s1.key, s2.key 
from src s1 left join src s2 on s1.key > '2';
```
和

```SQL
-- sql
select s1.key, s2.key 
from src s1 left join src s2 
where s1.key > '2';
```

是不是左表（`Preserved Row Table`）在没有匹配的情况下，只会有一条。