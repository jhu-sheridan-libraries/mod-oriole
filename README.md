[![Build Status](https://travis-ci.org/jhu-sheridan-libraries/mod-oriole.svg?branch=master)](https://travis-ci.org/jhu-sheridan-libraries/mod-oriole)

## Getting Started

### Clone from GitHub

To clone the porject from github, make sure you inlucde the submodules:

```bash
git clone --recurse-submodules -j8 git@github.com:jhu-sheridan-libraries/mod-oriole.git
```

### Build

```bash 
mvn clean install 
```

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
CREATE ROLE folio WITH PASSWORD 'folio123' LOGIN SUPERUSER;
CREATE DATABASE folio WITH OWNER folio;
```

Create a postgres-conf.json file, and use it as the connection conf. 

```
cat > /tmp/postgres-conf.json <<END
{
  "host": "localhost",
  "port": 5432,
  "username": "folio",
  "password": "folio123",
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
curl -D - -w '\n' -H "X-Okapi-Tenant: test"  http://localhost:8081/resources
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
curl -D - -w '\n' -H "X-Okapi-Tenant: test" -H "Content-Type: application/json" \
  -X POST -d '{"module_to": "mod-oriole-1.0"}' \
  http://localhost:8081/_/tenant 
```

The output is like the following: 

``` 
HTTP/1.1 201 Created
Content-Type: application/json
host: localhost:8081
user-agent: curl/7.54.0
accept: */*
x-okapi-tenant: test
content-length: 31
Transfer-Encoding: chunked

"[ ]"
```

### Fetch resources (GET)

To fetch the resources, use this command: 

```bash 
curl -D - -w '\n' -H "X-Okapi-Tenant: test"  http://localhost:8081/resources
```

The results would be: 

``` 
HTTP/1.1 200 OK
Content-Type: application/json
host: localhost:8081
user-agent: curl/7.54.0
accept: */*
x-okapi-tenant: test
Transfer-Encoding: chunked

{
  "resources" : [ ],
  "totalRecords" : 0
}
```

Note that it returns a 200 response, instead of the 500 error earlier in the Getting Started section. This is because 
the database table has been created for this tenant and this module. There is no records found because we
haven't post any resources in the database yet. 

### Create resources (POST)

Create a resource record in JSON: 

```bash
cat > /tmp/mod-oriole-resource-1.json <<END
{
  "id": "11111111-1111-1111-a111-111111111111",
  "title": "PubMed",
  "link": "https://www.ncbi.nlm.nih.gov/pubmed/",
  "description": "PubMed is a free search engine accessing primarily the MEDLINE database of references and abstracts on life sciences and biomedical topics."
}
END
```

To post it to the API, use the following:

```bash
curl -D - -w '\n' -X POST -H "X-Okapi-Tenant: test" \
  -H "Content-type: application/json" \
  -d @/tmp/mod-oriole-resource-1.json \
  http://localhost:8081/resources
```

The response should be a HTTP 201 response: 

``` 
HTTP/1.1 201 Created
Content-Type: application/json
Location: /resources/11111111-1111-1111-a111-111111111111
host: localhost:8081
user-agent: curl/7.54.0
accept: */*
x-okapi-tenant: test
content-length: 276
Transfer-Encoding: chunked

{
  "id" : "11111111-1111-1111-a111-111111111111",
  "link" : "https://www.ncbi.nlm.nih.gov/pubmed/",
  "title" : "PubMed",
  "description" : "PubMed is a free search engine accessing primarily the MEDLINE database of references and abstracts on life sciences and biomedical topics."
}
```

Try fetch again, and this time there should be one record found. 

``` 
$ curl -D - -w '\n' -H "X-Okapi-Tenant: test"  http://localhost:8081/resources
HTTP/1.1 200 OK
Content-Type: application/json
host: localhost:8081
user-agent: curl/7.54.0
accept: */*
x-okapi-tenant: test
Transfer-Encoding: chunked

{
  "resources" : [ {
    "resources" : [ ],
    "link" : "https://www.ncbi.nlm.nih.gov/pubmed/",
    "description" : "PubMed is a free search engine accessing primarily the MEDLINE database of references and abstracts on life sciences and biomedical topics.",
    "id" : "11111111-1111-1111-a111-111111111111",
    "title" : "PubMed"
  } ],
  "totalRecords" : 1
}

```

### Fetch by ID

To fetch the record by the ID (which is required to be a UUID), use the following: 

```bash
curl -D - -w '\n' -H "X-Okapi-Tenant: test" http://localhost:8081/resources/11111111-1111-1111-a111-111111111111
```

The response would be like: 

``` 
HTTP/1.1 200 OK
Content-Type: application/json
host: localhost:8081
user-agent: curl/7.54.0
accept: */*
x-okapi-tenant: test
Transfer-Encoding: chunked

{
  "id" : "11111111-1111-1111-a111-111111111111",
  "link" : "https://www.ncbi.nlm.nih.gov/pubmed/",
  "title" : "PubMed",
  "description" : "PubMed is a free search engine accessing primarily the MEDLINE database of references and abstracts on life sciences and biomedical topics."
}
```

### Update record (PUT)

Create an updated version of the resource record in JSON: 

```bash
cat > /tmp/mod-oriole-resource-2.json <<END
{
  "id": "11111111-1111-1111-a111-111111111111",
  "title": "PubMed",
  "link": "https://www.ncbi.nlm.nih.gov/pubmed/",
  "description": "PubMed lists journal articles and more back to 1947."
}
END
```

Update the record with the file. 

```bash 
curl -D - -w '\n' -X PUT \
  -H "X-Okapi-Tenant: test" \
  -H "Content-Type: application/json" \
  -d @/tmp/mod-oriole-resource-2.json \
  http://localhost:8081/resources/11111111-1111-1111-a111-111111111111
```

The response would look like: 

``` 
HTTP/1.1 204 No Content
host: localhost:8081
user-agent: curl/7.54.0
accept: */*
x-okapi-tenant: test
content-length: 189
```


### Delete a resource by ID (DELETE)

```bash
curl -D - -w '\n' -X DELETE \
  -H "X-Okapi-Tenant: test" \
  http://localhost:8081/resources/11111111-1111-1111-a111-111111111111
```

It returns a HTTP 204 response

```
HTTP/1.1 204 No Content
host: localhost:8081
user-agent: curl/7.54.0
accept: */*
x-okapi-tenant: test
Content-Length: 0 
```

## Build Docker Image

Build the docker image locally

```bash 
docker build -t dockerusername/oriole:version . 
```

Push the new image to docker hub

```bash
docker push dockerusername/oriole 
```

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


docker build --no-cache --rm --tag=farooqsadiq/mod-oriole:1.0.2 --tag=farooqsadiq/mod-oriole:latest .
docker images | grep mod-oriole
docker push farooqsadiq/mod-oriole:1.0.2
docker push farooqsadiq/mod-oriole:latest

