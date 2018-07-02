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
> curl -D - -w '\n' -H "X-Okapi-Tenant: test"  http://localhost:8081/resources
HTTP/1.1 401 Unauthorized
Content-Type: text/plain
host: localhost:8081
user-agent: curl/7.54.0
accept: */*
x-okapi-tenant: test
Transfer-Encoding: chunked

No oriole.domain.* permissions
```