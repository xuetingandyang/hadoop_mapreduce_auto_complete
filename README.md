# Auto-complete Project in Hadoop MapReduce

## Background

Auto-complete has been widely deployed, like Google search suggestion and spelling correction. 
In this project, auto-complete is implemented by N-Gram Model [(wiki-NGram)](https://en.wikipedia.org/wiki/N-gram).

An N-Gram is a contiguous sequence of n items from a given sequence of text or speech.
An N-Gram model uses probability to predict next word or phase.

## Implementation

### 1. Set Up LAMP (Linux, Apache, MySQL, PHP)

Install LAMP in you localhost. 

```shell script
sudo apt-get update
sudo apt-get -y install wget screen unzip
screen -S lamp  # run LAMP installation in the background to avpid unexpected interruption
wget -O lamp.zip https://github.com/teddysun/lamp/archive/master.zip
unzip lamp.zip
cd lamp-master/
chmod +x *.sh   # change script mode to executable
./lamp.sh
```
Get your local host ip address.
```shell script
ifconfig
```
Note: There are multiple ip addresses, usually, the 'inet' of first one is the needed ip address.
(flags is like <UP,BROADCAST,RUNNING,MULTICAST>).

### 2. Set up MySQL

Initialize the output table for later writing process of MapReduce.

Note: Start MySQL server before run MySQL
```shell script
sudo systemctl start mysql
```
```shell script
cd /usr/local/mysql/bin/
./mysql -uroot -pyour_password
```
Create 'Test' database and 'output' table in MySQL.
```
create database Test;
use Test;
create table output(starting_phrase VARCHAR(250), following_word VARCHAR(250), count INT);
```
Get port number of MySQL database. (We need it when MapReduce writing into MySQL database.)
```
SHOW VARIABLES WHERE Variable_name = 'port';
```


### 3. Set up Hadoop
Use Docker to set up the Hadoop environment with 1 namenode (master machine) and 2 datanodes (slave machines).
```shell script
sudo docker pull joway/hadoop-cluster 
git clone https://github.com/joway/hadoop-cluster-docker 
```
Create Hadoop network.
```shell script
sudo docker network create --driver=bridge hadoop
```
**Modify start-container.sh** to ensure you **sync** '/~/src' codes in your localhost with '/root/src' on docker container.
```shell script
vim start-container.sh
```
Change the '~/src' to absolute path in your localhost, like '/home/yxt/src'
```shell script
sudo docker run -itd \
                --net=hadoop \
                -p 50070:50070 \
                -p 8088:8088 \
		-v /home/yxt/src:/root/src
```
Now you can modify your codes in /home/yxt/src in IntelliJ on local host.
The changes will also happen in container.

Start container. Note: Every time you want to use this docker, you need to run the following commands.
```shell script
cd hadoop-cluster-docker
sudo ./start-container.sh
```
The correct output is
```shell script
start hadoop-master container...
start hadoop-slave1 container...
start hadoop-slave2 container...
root@hadoop-master:~# 
```
Now, start the Hadoop environment. Every time you need Hadoop, you need to run this script. 
```shell script
./start-hadoop.sh
```
Test your Hadoop environment with 'word-count' script.
```shell script
./run-wordcount.sh
```
The correct output is
```shell script
input file1.txt:
Hello Hadoop

input file2.txt:
Hello Docker

wordcount output:
Docker    1
Hadoop    1
Hello    2
```
If there is any problems, please see (https://quip.com/85VvAGqcb0Lg).


### 4. Set remote connection with MySQL 
In the container, download mysql-connector for remote connection.
```shell script
cd src
wget https://s3-us-west-2.amazonaws.com/jiuzhang-bigdata/mysql-connector-java-5.1.39-bin.jar
```
Set up HDFS.
```shell script
hdfs dfs -mkdir /mysql
hdfs dfs -put mysql-connector-java-*.jar /mysql/ #hdfs path to mysql-connector*
```

### 6. Set up parameters before run Auto-Complete
In the container, find the auto-complete folder. 
It should be /root/src/auto-complete.
Set input path for HDFS.
```shell script
hdfs dfs -mkdir -p input
```
Make sure HDFS output path not exist.
```shell script
hdfs dfs -rm -r /output 
```
Upload input files into HDFS.
```shell script
hdfs dfs -put bookList/* input/
```
We need modify some parameters in 'Driver.java'.
```shell script
cd src/mian/java
vi Driver.java
```
My example is:
```java
DBConfiguration.configureDB(
    configuration2,
    "com.mysql.jdbc.Driver",
    "jdbc:mysql://172.18.0.1:3306/Test",	// jdbc:mysql://ip_address:port/database_name
    "root",
    "00000000"
);

job2.addArchiveToClassPath(new Path("/mysql/mysql-connector-java-5.1.39-bin.jar"));
```
### 7. Run Auto-Complete
```shell script
hadoop com.sun.tools.javac.Main *.java
jar cf ngram.jar *.class
```
Set nGram_size(2), threshold_size(3), and topK(4).
```shell script
hadoop jar ngram.jar Driver input /output 2 3 4
```
There are 2 MapRedice jobs. 
If you only successfully run the job1, but failed at job2.
(like any connection failed to fail writing into database)
Check the ip_address in Driver.java.

Also, if you failed, before run it agagin, you need delete the output path in HDFS.
```shell script
hdfs dfs -rm -r /output
```

### 8. Check MySQL Database
In **localhost**, check whther the results have been written into database.
```shell script
mysql -uroot -pyour_password
use Test
select * from output limit 10;
```



