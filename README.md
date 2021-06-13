# Introduction
This is a Hive function test repository. It contains three kind of functions: 
- UDF: User Defined Functions
- UDAF: User Defined Aggregate Function
- UDTF: User Defined Tabular Function

The structure of repository complies with the maven standard.


# Prepare
## Hadoop and Hive environment.
If you need Hadoop and Hive environment, please see my another repository [docker hadoop environment](https://github.com/guo11327/hadoop_cluster_docker).
## DataBase

```
rm -rf metastore_db/
schematool -initSchema -dbType derby
hive
```
In hive console
```
hive> create database studentdb;

hive> use studentdb;

hive> create table student(id int, name string, sex string, age int, department string) row format delimited fields terminated by ",";

# move data/student.txt "/root/student.txt" is path. replace to your path.

hive> load data local inpath "/root/student.txt" into table student;
```
then run follow:
```
hive> select * from student;
OK
95002   刘晨    女      19      IS
95017   王风娟  女      18      IS
95018   王一    女      19      IS
......

```
## Maven
install maven


[maven tortual](https://maven.apache.org/guides/getting-started/maven-in-five-minutes.html)

# Functions

## UDF
UDFs works on a single row in a table and produces a single row as output. Its one to one relationship between input and output of a function.

### Problem Statement
Get a int number, return a*2 

### File Path
```
src/main/java/com/example/MyUDF.java
# concate two string.
# src/main/java/com/example/ConcateUDF.java 
```
### Comment
Here we use org.apache.hadoop.hive.ql.udf.generic.GenericUDF. which supports complex types:array, map, struct, and uniontype.

## UDAF
User-Defined Aggregation Functions (UDAFs) are an excellent way to integrate advanced data-processing into Hive. Hive allows two varieties of UDAFs: simple and generic. Simple UDAFs, as the name implies, are rather simple to write, but incur performance penalties because of the use of Java Reflection, and do not allow features such as variable-length argument lists. Generic UDAFs allow all these features, but are perhaps not quite as intuitive to write as Simple UDAFs.

### File Path
```
src/main/java/com/example/MyUDAFAve.java
src/main/java/com/example/MyUDAFCount.java
```
### Comment
Here we supply MyUDAFAve and MyUDAFCount function to test.
[Writing GenericUDAFs: A Tutorial](https://cwiki.apache.org/confluence/display/hive/genericudafcasestudy)

## UDTF
User defined table functions represented by org.apache.hadoop.hive.ql.udf.generic.GenericUDTF interface. This function allows to output multiple rows and multiple columns for a single input.

### Problem Statement
Given a chinese name, return First name, Last name.

### File Path
```
src/main/java/com/example/MyUDTF.java
```

### Comment
1. we specify input and output parameters
```
abstract StructObjectInspector initialize(ObjectInspector[] args) throws UDFArgumentException; 
```
2. we process an input record and write out any resulting records 
```
abstract void process(Object[] record) throws HiveException;
```
3. function is Called to notify the UDTF that there are no more rows to process. Clean up code or additional output can be produced here.
```
abstract void close() throws HiveException;
```

more info click [Write UDTF](https://riptutorial.com/hive/example/22316/udtf-example-and-usage)

# Test Function

## jar
cd repository path. execute the following command:
```
mvn package
```
now, you will find *target* folder in work directory. then copy *target/hadooptrain-1.0-SNAPSHOT.jar* to hive.

## add jar in hive
In hive shell
```
add jar target/hadooptrain-1.0-SNAPSHOT.jar;
# create temporary function {function_name} as 'com.example.{Java_class_name}';
create temporary function myudf as 'com.example.MyUDF';
create temporary function myudf_concate as 'com.example.MyUDFConcat';
create temporary function myudaf_ave as 'com.example.MyUDAFAve';
create temporary function myudaf_count as 'com.example.MyUDAFCount';
create temporary function myudtf as 'com.example.MyUDTF';
```
## Test function
```
use studentdb;
```
### myudf
age * 2
```
select age,myudf(age) from student;
```
alice+Bob
```
select myudf_concate("alice","Bob");
```
### myudaf
myudf_ave
```
select department,myudaf_ave(age) from student group by department;
```
myudf_count
```
select department,myudaf_count(name) from student group by department;
```
### mydftf
"刘晨"  "刘" "晨"
```
select t.name,t.surname from student lateral view myudtf(name) t as name,surname;
```
## EXIT
```
exit;
```