[![Build Status](https://travis-ci.org/jhu-sheridan-libraries/mod-oriole.svg?branch=master)](https://travis-ci.org/jhu-sheridan-libraries/mod-oriole)

# mod-oriole

Copyright (C) 2019 Johns Hopkins University

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

# Goal

FOLIO compatible A-Z Database module.

Provides PostgreSQL based storage to implement an A-Z Database module. Written in Java, using the raml-module-builder and uses Maven as its build system.

# Prerequisites

- Java 8 JDK
- Maven 3.3.9
- Postgres 9.6.1 (running and listening on localhost:5432, logged in user must have admin rights)

## Getting Started

### Sandbox Setup
Download and Install Intellij

`brew install maven`

`brew install jetty`

`brew install jenv`

#### Java 8 JDK

You will need a JDK that includes tools.jar since this project uses the AspectJ maven plugin.

You can download Amazon's JDK at  https://docs.aws.amazon.com/corretto/latest/corretto-8-ug/macos-install.html

`jenv add /Library/Java/JavaVirtualMachines/amazon-corretto-8.jdk/Contents/Home/`

`jenv global 1.8`

If you haven't already, enable the maven and export plugins:

`jenv enable-plugin maven`

`jenv enable-plugin export`

Update bash settings

`java -jar target/mod-oriole-fat.jar`

Postgres setup

`exec java "$JAVA_OPTS" -jar ${VERTICLE_HOME}/module.jar "$@"`

#### IntelliJ Debugger Settings:
Main class: org.folio.rest.RestLauncher

Project Arguments: run org.folio.rest.RestVerticle db_connection=/Users/amanda/oriole/mod-oriole-archive/postgres-conf.idea.json

Working Dir: /[your path to]/mod-oriole

Use classpath of module: mod-oriole

JDK: Default 1.8

### Clone from GitHub

Be sure to include submodules since there are some common RAML definitions that are shared between FOLIO projects via Git submodules:

```bash
git clone --recurse-submodules -j8 git@github.com:jhu-sheridan-libraries/mod-oriole.git
```

### Docker installation 
* Follow the steps here: https://wiki.library.jhu.edu/display/ULCSS/Docker+-+software+containerization+platform

### Building

run `mvn install` from the root directory.

The above command will compile, test and create a fat-jar file in the `target` directory. 

To just run tests, 

```bash 
mvn test 
```

### Run the fat jar 


#### With Embedded PostgreSQL

To run the fat jar file directly, with embedded postresql:  

``` 
java -jar target/mod-oriole-fat.jar embed_postgres=true
```

#### With standalone PostgreSQL

First set up PostgreSQL and create database and role. 

On Mac, start psql client: 

```
psql postgres 
```

Create role and database

```
CREATE ROLE folio WITH PASSWORD 'folio' LOGIN SUPERUSER;
CREATE DATABASE folio WITH OWNER folio;
```

Create a postgres-conf.json file, and use it as the connection conf. 

```
cat > /tmp/postgres-conf.json <<END
{
  "host": "localhost",
  "port": 5432,
  "username": "folio",
  "password": "folio",
  "database": "folio"
}
END
```

Start the fat jar with the db_connection option. 

```
java -jar target/mod-oriole-fat.jar db_connection=/tmp/postgres-conf.json
```

#### Check if server has started

Try the following and you should get a 401 response. 

```bash 
curl -D - -w '\n' -H "X-Okapi-Tenant: test"  http://localhost:8081/oriole-libraries
```

The output would be like: 

```
HTTP/1.1 500 Internal Server Error
Content-Type: text/plain
host: localhost:8081
user-agent: curl/7.54.0
accept: */*
x-okapi-tenant: test
Transfer-Encoding: chunked

```

This is because the module hasn't been initialized. Keep reading for more command line options. 

## Command Line

The following examples are for interactions with the API when using the far jar file. 

### Register a tenant to the module

First you need to initialize the module. This will create a database table for this module and the tenant. 
Future CRUD operations from the same tenant will be based on that database table. 

```bash 
curl -X "POST" "http://localhost:8081/_/tenant" \
     -H 'X-Okapi-Tenant: diku' \
     -H 'Content-Type: application/json' \
     -H 'Accept: application/json' \
     -d $'{
  "module_to": "mod-oriole-1.0"
}'
```

The output is like the following: 

``` 
HTTP/1.1 201 Created
Content-Type: application/json
host: localhost:8081
user-agent: curl/7.54.0
accept: */*
x-okapi-tenant: diku
content-length: 31
Transfer-Encoding: chunked

"[ ]"
```

### Fetch resources (GET)

To fetch the resources, use this command: 

```bash 
curl "http://localhost:8081/oriole/resources" \
     -H 'X-Okapi-Tenant: diku' \
     -H 'Accept: application/json'
```

The results would be: 

``` 
HTTP/1.1 200 OK
Transfer-Encoding: chunked
Content-Type: application/json
x-okapi-tenant: diku
accept: application/json
host: localhost:8081
Connection: close

{
  "resources" : [ ],
  "totalRecords" : 0,
  "resultInfo" : {
    "totalRecords" : 0,
    "facets" : [ ],
    "diagnostics" : [ ]
  }
}
```

Note that it returns a 200 response, instead of the 500 error earlier in the Getting Started section. This is because 
the database table has been created for this tenant and this module. There are no records found because we
haven't post any resources in the database yet. 

### Create resources (POST)

To create a new resource, use this command:

```bash
curl -X "POST" "http://localhost:8081/oriole/resources" \
     -H 'X-Okapi-Tenant: diku' \
     -H 'Content-Type: application/json' \
     -H 'Accept: application/json' \
     -d $'{
  "id": "c7b8911a-6983-11ea-bc55-0242ac130003",
  "title": "PubMed",
  "description": "PubMed lists journal articles and more back to 1947. It indexes about 5,400 journals and covers the areas of medicine, nursing, dentistry, veterinary medicine, health care systems, preclinical sciences, and related areas. PubMed also links to online books and to most of the other NCBI databases. PubMed is a free database developed by the National Library of Medicine (NLM) and the National Center for Biotechnology Information (NCBI), both at the National Institutes of Health (NIH) in Bethesda, MD.",
  "url": "https://www.ncbi.nlm.nih.gov/pubmed/"
}'
```

The response should be a HTTP 201 response: 

``` 
HTTP/1.1 201 Created
Transfer-Encoding: chunked
Content-Type: application/json
Location: /oriole/resources/c7b8911a-6983-11ea-bc55-0242ac130003
x-okapi-tenant: diku
accept: application/json
host: localhost:8081
content-length: 651
Connection: close

{
  "id" : "c7b8911a-6983-11ea-bc55-0242ac130003",
  "altId" : "JHU00002",
  "url" : "https://www.ncbi.nlm.nih.gov/pubmed/",
  "title" : "PubMed",
  "description" : "PubMed lists journal articles and more back to 1947. It indexes about 5,400 journals and covers the areas of medicine, nursing, dentistry, veterinary medicine, health care systems, preclinical sciences, and related areas. PubMed also links to online books and to most of the other NCBI databases. PubMed is a free database developed by the National Library of Medicine (NLM) and the National Center for Biotechnology Information (NCBI), both at the National Institutes of Health (NIH) in Bethesda, MD.",
  "identifier" : [ ],
  "terms" : [ ],
  "accessRestrictions" : [ ],
  "availability" : [ ]
}
```

Try fetch again, and this time there should be one record found. 

``` 
$ curl -D - -w '\n' -H "X-Okapi-Tenant: diku"  -H "Accept: application/json" http://localhost:8081/oriole/resources

HTTP/1.1 200 OK
Transfer-Encoding: chunked
Content-Type: application/json
x-okapi-tenant: diku
accept: application/json
host: localhost:8081
user-agent: Paw/3.1.10 (Macintosh; OS X/10.15.3) GCDHTTPRequest
Connection: close

{
  "resources" : [ {
    "id" : "c7b8911a-6983-11ea-bc55-0242ac130003",
    "altId" : "JHU00002",
    "url" : "https://www.ncbi.nlm.nih.gov/pubmed/",
    "title" : "PubMed",
    "description" : "PubMed lists journal articles and more back to 1947. It indexes about 5,400 journals and covers the areas of medicine, nursing, dentistry, veterinary medicine, health care systems, preclinical sciences, and related areas. PubMed also links to online books and to most of the other NCBI databases. PubMed is a free database developed by the National Library of Medicine (NLM) and the National Center for Biotechnology Information (NCBI), both at the National Institutes of Health (NIH) in Bethesda, MD.",
    "identifier" : [ ],
    "terms" : [ ],
    "accessRestrictions" : [ ],
    "availability" : [ ]
  } ],
  "totalRecords" : 1,
  "resultInfo" : {
    "totalRecords" : 1,
    "facets" : [ ],
    "diagnostics" : [ ]
  }
}

```

### Fetch Resource by ID

To fetch the resource by the ID (which is required to be a UUID), use the following: 

```bash
curl "http://localhost:8081/oriole/resources/c7b8911a-6983-11ea-bc55-0242ac130003" \
     -H 'X-Okapi-Tenant: diku' \
     -H 'Accept: application/json'
```

The response would be like: 

``` 
HTTP/1.1 200 OK
Transfer-Encoding: chunked
Content-Type: application/json
x-okapi-tenant: diku
accept: application/json
host: localhost:8081
Connection: close

{
  "id" : "c7b8911a-6983-11ea-bc55-0242ac130003",
  "altId" : "JHU00002",
  "url" : "https://www.ncbi.nlm.nih.gov/pubmed/",
  "title" : "PubMed",
  "description" : "PubMed lists journal articles and more back to 1947. It indexes about 5,400 journals and covers the areas of medicine, nursing, dentistry, veterinary medicine, health care systems, preclinical sciences, and related areas. PubMed also links to online books and to most of the other NCBI databases. PubMed is a free database developed by the National Library of Medicine (NLM) and the National Center for Biotechnology Information (NCBI), both at the National Institutes of Health (NIH) in Bethesda, MD.",
  "identifier" : [ ],
  "terms" : [ ],
  "keywords" : "c7b8911a-6983-11ea-bc55-0242ac130003 https://www.ncbi.nlm.nih.gov/pubmed/ JHU00002 PubMed PubMed lists journal articles and more back to 1947. It indexes about 5,400 journals and covers the areas of medicine, nursing, dentistry, veterinary medicine, health care systems, preclinical sciences, and related areas. PubMed also links to online books and to most of the other NCBI databases. PubMed is a free database developed by the National Library of Medicine (NLM) and the National Center for Biotechnology Information (NCBI), both at the National Institutes of Health (NIH) in Bethesda, MD.",
  "accessRestrictions" : [ ],
  "availability" : [ ]
}
```

### Update Resource (PUT)

Update the resource with this command: 

```bash 
curl -X "PUT" "http://localhost:8081/oriole/resources/c7b8911a-6983-11ea-bc55-0242ac130003" \
     -H 'X-Okapi-Tenant: diku' \
     -H 'Content-Type: application/json' \
     -H 'Accept: application/json' \
     -d $'{
  "id": "c7b8911a-6983-11ea-bc55-0242ac130003",
  "title": "PubMed",
  "description": "short description...",
  "url": "https://www.ncbi.nlm.nih.gov/pubmed/"
}'
```

The response would look like: 

``` 
HTTP/1.1 204 No Content
x-okapi-tenant: diku
accept: application/json
host: localhost:8081
Connection: close
```


### Delete a resource by ID (DELETE)

```bash
curl -X "DELETE" "http://localhost:8081/oriole/resources/c7b8911a-6983-11ea-bc55-0242ac130003" \
     -H 'X-Okapi-Tenant: diku' \
     -H 'Accept: application/json'
```

It returns a HTTP 204 response

```
HTTP/1.1 204 No Content
x-okapi-tenant: diku
accept: application/json
host: localhost:8081
Connection: close
```

## Build Docker Image

See wiki page: [Package and publish docker images](https://github.com/jhu-sheridan-libraries/mod-oriole/wiki/Package-and-publish-docker-images)

## Build on Vagrant Virtual Box

First start vagrant

```
vagrant up
```

Logon to the vagrant virtual box

``` 
vagrant ssh 
```

Register the module

```
curl -w '\n' -D - -X POST -H "Content-type: application/json" -d @/vagrant/target/ModuleDescriptor.json http://localhost:9130/_/proxy/modules 
```

Deploy it

```
curl -w '\n' -D - -s -X POST -H "Content-type: application/json" -d @/vagrant/target/DeploymentDescriptor.json http://localhost:9130/_/discovery/modules
```

Start the fat jar

```
cd /vagrant; java -jar /vagrant/target/mod-oriole-fat.jar db_connection=/vagrant/postgres-conf.json
```

Enable for a tenent

```
curl -w '\n' -X POST -D - -H "Content-type: application/json" -d@/vagrant/target/EnableModule.json http://localhost:9130/_/proxy/tenants/diku/modules
```

We need to run the following step manually. 

``` 
curl -D - -w '\n' -H "X-Okapi-Tenant: diku" -H "Content-Type: application/json"   -X POST -d '{"module_to": "mod-oriole-1.0-SNAPSHOT"}'   http://localhost:8081/_/tenant
```

Add permissions

```
python3 scripts/add-permissions.py 
```




