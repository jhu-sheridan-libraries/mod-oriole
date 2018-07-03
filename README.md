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

To run the fat jar file directly, with embedded postresql:  

``` 
java -jar target/mod-oriole-fat.jar embed_postgres=true
```

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

### Register a tenant to the module

First you need to initialize the module. This will create a database table for this module and the tenant. 
Future CRUD operations from the same tenant will be based on that database table. 

```bash 
curl -D - -w '\n' -H "X-Okapi-Tenant: test" -H "Content-Type: application/json" \
  --request POST --data '{"module_to": "mod-oriole-1.0"}' \
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

### Fetch resources

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