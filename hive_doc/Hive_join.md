# Hive Join

## Common join
The common join, also called reduce side join. We need to make sure that big table is on the right-most side or specified by hit.
```
/*+ STREAMTABLE(b) */
SELECT /*+ STREAMTABLE(a) */ a.val, b.val, c.val FROM a JOIN b ON (a.key = b.key1) JOIN c ON (c.key = b.key1)

```
all the three tables are joined in a single map/reduce job and the values for a particular value of the key for tables b and c are buffered in the memory in the reducers. Then for each row retrieved from a, the join is computed with the buffered rows. If the STREAMTABLE hint is omitted, Hive streams the rightmost table in the join.

## Map Join
Map join is used when one of the join tables is small enough to fit in the memory. It is fast but limited. enable it:
```
SET hive.auto.convert.join=true; --default false
SET hive.mapjoin.smalltable.filesize=600000000; --default 25M
SET hive.auto.convert.join.noconditionaltask=true; --default false. Set to true so that map join hint is not needed
SET hive.auto.convert.join.noconditionaltask.size=10000000; --The default value controls the size of table to fit in memory
```
Once autoconvert join is enabled, there is no need to provide map join hits in the query specifically.

## Skew Join
When working with data that has a highly uneven distribution, the data skew could happen in such a way that a small number of compute nodes must handle the bulk of the computation. The following setting informs Hive to optimize properly if data skew happens.
### Separate queries
split the query into queries and run them separately avoid the skew join.
```
    # query 1
    select A.id from A join B on A.id = B.id
    # query 2
    select A.id from A join B on A.id = B.id where A.id <> 1;
    select A.id from A join B on A.id = B.id where A.id = 1 and B.id = 1;
```
#### Advantages
- Simple change in the query will avoid the skew join.
- Helpful when query is simple.
#### Disadvantages
- you need re-write same query twice
- It will be hard to write 2 separate query if origin query is complex
- It needs to be done at 2 separate places.

### Using Hive Configuration
Enable skew join optimization using hive configuration
```
set hive.optimize.skewjoin=true;
set hive.skewjoin.key=500000;
set hive.skewjoin.mapjoin.map.tasks=10000;
set hive.skewjoin.mapjoin.min.split=33554432;
```
#### Advantages
- No need to change the query.
- Simple change in the setting will improve the performance.
#### Disadvantages
- Above solution is not consistent and we do not see improvement in performance every time.
### Optimizing Skew Join
Data distribution with equal probability
1. Write a function to generate random numbers between 1 to R(number of rows) with equal probability.
2. Create a new field in Target Table.


## Bucket Map Join

**CLUSTERED BY** : Create bucketed table
### condition
-  tables are bucketed and joined on the bucketed column
-  the number of buckets in one table must be equal or a multiple of the number of buckets in the other table of the join.

### advantage
Each bucket is expected to hold/contain certain rows based on the bucketing key/columns.
- query processing easy and efficient in terms of performance while joining large buckets tables.

### Config
```
SET hive.auto.convert.join=true; --default false
set hive.optimize.bucketmapjoin = true;
```
### Example
Assume Table1 T1, Table2 T2 and both tables' data is bucketed using the 'id' column into 4 and 8 buckets. It means bucket_1 of T1 will contain rows with same id as that of bucket_1 of T2.
If we perform join on these two tables on the 'id' column, and if it is possible to send bucket1 of both the tables to a single mapper then we can achieve a good amount of optimization. 

## Sort merge bucket (SMB) join
SMB is the join performed on the bucket tables that have the same sorted, bucket, and join condition columns. It reads data from both bucket tables and performs common joins (map and reduce triggered) on the bucket tables. We need to enable the following properties to use SMB.

```
SET hive.input.format=org.apache.hadoop.hive.ql.io.BucketizedHiveInputFormat
SET hive.auto.convert.sortmerge.join=true;
SET hive.optimize.bucketmapjoin=true;
SET hive.optimize.bucketmapjoin.sortedmerge=true;
SET hive.auto.convert.sortmerge.join.noconditionaltask=true;
```

## Sort merge bucket map(SMBM) join
SMBM join is a special bucket join but triggers map-side join only. 
### advantage
It can avoid caching all rows in the memory like map join does. 
### condition
- the join tables must have the same bucket, sort, and join condition columns. 
- To enable such joins, we need to enable the following settings.
```
SET hive.auto.convert.join=true;
SET hive.auto.convert.sortmerge.join=true
SET hive.optimize.bucketmapjoin=true;
SET hive.optimize.bucketmapjoin.sortedmerge=true;
SET hive.auto.convert.sortmerge.join.noconditionaltask=true;
SET hive.auto.convert.sortmerge.join.bigtable.selection.policy=org.apache.hadoop.hive.ql.optimizer.TableSizeBasedBigTableSelectorForAutoSMJ;
```
## Skew join

## Reference
1. [Bucket Map Join](https://blog.clairvoyantsoft.com/bucket-map-join-in-hive-e9fee52affff)
2. [Skew Join](https://medium.com/expedia-group-tech/skew-join-optimization-in-hive-b66a1f4cc6ba)
3. [Join optimization](https://timepasstechies.com/hive-tutorial-9-hive-performance-tuning-using-join-optimization-common-map-bucket-skew-join/)
4. [Hive language manual joins](https://cwiki.apache.org/confluence/display/Hive/LanguageManual+Joins)