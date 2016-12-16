# ORACP File Transfer Utility

This program is a command line utility that can perform file operations over an 
[Oracle JDBC connection][OJDBC]. It can simplify the transfer of files between a client and 
the database compared to approaches like SFTP.
 
[OJDBC]: <http://docs.oracle.com/cd/E11882_01/java.112/e16548/urls.htm>

It makes use of the [SYS.UTL_FILE][UTL_FILE] package to execute file operations over JDBC. For
example it can transfer a datapump dump file before running the import or export utilities. 

[UTL_FILE]: <https://docs.oracle.com/database/121/ARPLS/u_file.htm#ARPLS069>

## Table of Contents

- [Features](#features)
- [Building](#building)
    - [Gradle Installation](#gradle-installation)
    - [Oracle JDBC Driver](#oracle-jdbc-driver)
    - [Build Tasks](#build-tasks)
- [Configuration](#configuration)
	- [Launcher Configuration](#launcher-configuration)
- [Using the Gradle Launcher](#using-the-gradle-launcher)
	- [Launcher Tasks](#launcher-tasks)
	- [ocpPut Example](#ocpput-example)
- [Using the Executable](#using-the-executable)
    - [List Example](#list-example)
    - [Get Example](#get-example)
    - [Put Example](#put-example)
- [Tests](#tests)
- [Author](#author)
- [License](#license)

## Features

This program can run the following tasks:
* **List**: List the contents of an oracle Directory Object.
* **Get**: Transfer a file from the database to a local directory.
* **Put**: Transfer a local file to a database directory.

If you are running on Windows you can execute the launcher generated by 
[launch4j][LAUNCH4J]. As an alternative you can run tasks from the included Gradle launcher 
script or use the executable JAR file.

[LAUNCH4J]: <http://launch4j.sourceforge.net/docs.html>

Below is the usage displayed when invoked with the **--help** option.

	usage: oracp [OPTIONS] [list|get FILENAME DEST|put FILENAME]
	This program is a command line utility that can perform file operations over an Oracle JDBC
	connection.
	   -d,--debug            turn on debug logging
	   -f,--force            Force overwrite of destination.
	   -h,--help             print this message
	   -p,--passwd <arg>     DB password
	   -s,--db-dir <arg>     DB directory object
	   -u,--user <arg>       DB username
	      --url <arg>        Oracle JDBC URL (jdbc:oracle:thin:@//hostname:port/service)
	
	You must choose one of the following tasks:
	   list              : List the contents of an oracle Directory Object.
	   get FILENAME DEST : Transfer a file from the database to a local directory.
	   put FILENAME      : Transfer a local file to a database directory.

## Building

### Gradle Installation

Before you can use this program you will need a [Gradle Installation][GRADLE-DOWNLOAD]. If you 
are behind a proxy then you may need to configure [proxy settings][GRADLE-PROXY].

[GRADLE-DOWNLOAD]: <https://gradle.org/gradle-download/>
[GRADLE-PROXY]: <https://docs.gradle.org/current/userguide/build_environment.html#sec:accessing_the_web_via_a_proxy>
  
### Oracle JDBC Driver

Before you can build or run this project you will need a copy of the **ojdbc7.jar** driver. This 
driver can't be included with the distribution because it is covered under the 
[Oracle Technology Network License Agreement][ORACLE-OTN].

[ORACLE-OTN]: <http://www.oracle.com/technetwork/licenses/distribution-license-152002.html>

To get access to the file you will need an [OTN login][OTN-ACCOUNT]. To download the file you 
have 2 options:

1. Edit the Maven section of the **gradle.build** file with your OTN login. When you run the build
  	target it will download the file from the [Oracle Maven repository][OTN-MAVEN].

		maven {     
		    url 'https://www.oracle.com/content/secure/maven/content'
		    credentials {
		        username 'user'
		        password 'password'
		    }
		}

2. Download [ojdbc7.jar][ORACLE-JDBC] manually. You will need to place the file in 
	**./lib** swap the comments on the below lines in **build.gradle**.
  	
		//compile files('lib/ojdbc7.jar')
		compile group: 'com.oracle.jdbc', name: 'ojdbc7', version: '12.1.0.2'

[OTN-MAVEN]: <https://blogs.oracle.com/dev2dev/entry/how_to_get_oracle_jdbc>
[ORACLE-JDBC]: <http://www.oracle.com/technetwork/database/features/jdbc/index-091264.html>
[OTN-ACCOUNT]: https://profile.oracle.com/myprofile/account/create-account.jspx

### Build Tasks

Important Gradle targets are:

* **build**: Compile java sources and create jar file in **./build/libs/**.
* **test**: Invoke the JUnit test suite.
* **createLaunch4j**: Create a standalone oracp executable for Windows in **./build/launch4j/**.
* **install**: Copy all required files and dependencies to **./launch/**.

## Configuration

Important configuration notes:
* If you are using the program generated by **launch4j** then there is no configuration necessary 
	because everything is passed on the command line. 
  
* If you are using the Gradle launcher then you will need to edit the launcher script as indicated
	in the [Launcher Configuration](#launcher-configuration) section. 

* Before you can run the directory list task you will need to create the **fn_list_dir** function.
	This file is located under **./launch/fn_list_dir.sql**. It must be run as the SYS user.

### Launcher Configuration

The launcher includes a Gradle script and a directory containing all jar dependencies.

	[~/oracp/launch]$ ll
	total 4.2M
	drwxr-xr-x 2 atl101 dba 4.0K Dec 13 22:40 jars/
	-rw-r--r-- 1 atl101 dba 2.0K Dec 13 23:07 build.gradle

You should edit **oracp/build.gradle** and configure the following settings for your DB connection.

	// Edit these connection parameters
	dbHost = 'slc03qjw.us.oracle.com'
	dbPort = '1521'
	dbName = 'PINDB'
	jdbcUser 	= 'PIN102'
	jdbcPass 	= 'PIN102'
	dbDirectory = 'APP_DUMP_DIR'
	dbUrl = "jdbc:oracle:thin:@//${dbHost}:${dbPort}/${dbName}"

## Using the Gradle Launcher

The gradle launcher is a a convenient and cross-platform alternative to using a shell script or
executable JAR file.

### Launcher Tasks

Gradle can show the tasks that are defined for the launcher:

	[~/oracp/launch]$ gradle tasks
	:launch:tasks
	
	OcpExec tasks
	-------------
	ocpBase - Configure for URL: <jdbc:oracle:thin:@//oracledb.test.com:1521/PINDB>
	ocpGet - Get a file from APP_DUMP_DIR: (gradle ocpGet -Pfile=FILENAME)
	ocpHelp - Print command line usage
	ocpList - List files in DB directory APP_DUMP_DIR
	ocpPut - Put a file to APP_DUMP_DIR: (gradle ocpPut -Pfile=FILEPATH)

### ocpPut Example

The following example shows the syntax for the **ocpPut** task.

	[~/oracp/launch]$ gradle -q ocpGet -Pfile=expdp_xref116_v4.dmp
	Opening Connection...
	Oracle Database 12c Enterprise Edition Release 12.1.0.2.0 - 64bit Production
	* Starting task: GET
	Source: APP_DUMP_DIR/expdp_xref116_v4.dmp (4.14 MB)
	Destination: <./oracp/launch/expdp_xref116_v4.dmp>
	  0.75% (31/4240 KB) (186.04 KB/sec)
	  100.00% (4240/4240 KB) (50095.25 KB/sec)
	Transfer Complete!

## Using the Executable

The below examples show the arguments passed if you are using the executable JAR 
(**./launch/jars/oracp-1.0.jar**) or the **launch4j** generated executable 
(**./launch/oracp.exe**).

*Note: The launch4j executable only works on Windows.*

### List Example

In this example we list the contents of directory **APP_DUMP_DIR**.

	$ oracp \
		--url jdbc:oracle:thin:@//oracledb.test.com:1521/PINDB \
		--user PIN102 --passwd PIN102 \
		--source-dir APP_DUMP_DIR \
		list

	Opening Connection...
	Oracle Database 12c Enterprise Edition Release 12.1.0.2.0 - 64bit Production
	* Starting task: LIST
	Total 4 files in APP_DUMP_DIR:
	  expdp-confdb-daily.dmp
	  expdp-confdb-daily.log
	  expdp-jiradb-daily.dmp
	  expdp-jiradb-daily.log

### Get Example

In this example we transfer file **expdp_xref116_v4.dmp** to the current directory **./**.

	$ oracp \
		--url jdbc:oracle:thin:@//oracledb.test.com:1521/PINDB \
		--user PIN102 --passwd PIN102 \
		--source-dir APP_DUMP_DIR \
		get expdp_xref116_v4.dmp ./

	Opening Connection...
	Oracle Database 12c Enterprise Edition Release 12.1.0.2.0 - 64bit Production
	* Starting task: GET
	Source: APP_DUMP_DIR/expdp_xref116_v4.dmp (4.14 MB)
	Destination: <C:\temp\bin\expdp_xref116_v4.dmp>
	  0.75% (31/4240 KB) (88.89 KB/sec)
	  9.06% (383/4240 KB) (333.64 KB/sec)
	[...]
	  99.62% (4223/4240 KB) (420.64 KB/sec)
	  100.00% (4240/4240 KB) (204.16 KB/sec)
	Transfer Complete!

### Put Example

In this example we transfer file **C:\temp\expdp_xref116_v4.dmp** to the database directory
**APP_DUMP_DIR**.

Note: The process will fail if the file already exists in the destination. You can use 
the **--force** option to override this check.

	$ oracp \
		--url jdbc:oracle:thin:@//oracledb.test.com:1521/PINDB \
		--user PIN102 --passwd PIN102 \
		--source-dir APP_DUMP_DIR \
		--force \
		put \temp\expdp_xref116_v4.dmp

	Source: D:\Data\cjuliano\ATL_Git\atl_brm\src\projects\oracp\.gradle\2.14.1\taskArtifacts\fileSnapshots.bin (0.17 MB)
	Destination: <APP_DUMP_DIR/fileSnapshots.bin>
	Force overwrite of destination file!
	  17.95% (31/178 KB) (49.69 KB/sec)
	  71.79% (127/178 KB) (65.80 KB/sec)
	  100.00% (178/178 KB) (84.97 KB/sec)
	Transfer Complete!

## Tests

This project includes a **JUnit** test suite that is executed as part of the build and test 
targets.  By default most of the tests are skipped because they require connectivity to 
an Oracle database.

To enable these tests edit the connection information in **OcpMainTest.java** and comment the 
**includeTestsMatching** filter in the build.gradle script shown below.

	test {
	    filter {
	        //don't include tests requiring a DB connection.
	        includeTestsMatching "*.T01_testHelp"
	    }
	
	    testLogging {
	        // set options for log level LIFECYCLE
	        exceptionFormat "full"
	    	showStandardStreams true
	    }
	}

## Author

- Chad Juliano <@chadj2>

## License

This program is licensed under [GNU Lesser General Public License v3.0 only][LGPL-3.0]. 
Some rights reserved. See [LICENSE][].

![](images/lgplv3b-72.png "LGPL-3.0")
![](images/spdx-72.png "SPDX")

[LGPL-3.0]: <https://spdx.org/licenses/LGPL-3.0>
[LICENSE]: <LICENSE.md>

