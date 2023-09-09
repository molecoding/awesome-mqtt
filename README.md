# App

Package and run jar,

```shell
# Linux/macOS
./mvnw package
# Windows
mvnw.cmd package

java -jar target/awesome-mqtt-9999-SNAPSHOT.jar
```

Or run a class directly,

```shell
# Linux/macOS
./mvnw exec:java -Dexec.mainClass=com.molecoding.nobs.AwesomeMqttApp

# Windows
mvnw.cmd exec:java -Dexec.mainClass=com.molecoding.nobs.AwesomeMqttApp
```

## emqx server for local dev

### TCP listener only
Run a EMQX server in docker.

```shell
docker run --rm --name emqx \
  -p 18083:18083 -p 1883:1883 \
  emqx:latest
```

You should test like this:

```shell
./mvnw exec:java -Dexec.mainClass=com.molecoding.nobs.AwesomeMqttApp \
  -Dexec.args="tcp://localhost:1883 -m abc -t nobs"
```

### TCP over SSL/TLS

Or, if you want start the server with some ssl, follow the below.

Create folder for storing the certificates:

```shell
mkdir -p $(pwd)/tmp/localhost
```

Generate certificates:

```shell
openssl req -x509 \
  -newkey ec -pkeyopt ec_paramgen_curve:secp384r1 \
  -days 3650 \
  -nodes \
  -keyout $(pwd)/tmp/localhost/privkey.pem \
  -out $(pwd)/tmp/localhost/cert.pem \
  -subj "/CN=localhost"
```

Run with self-signed ssl certificates:

```shell
docker run --rm --name emqx \
  -v $(pwd)/tmp/localhost:/etc/ssl/localhost \
  -e EMQX_LISTENERS__SSL__DEFAULT__ENABLED=false \
  -e EMQX_LISTENERS__SSL__NOBS__ENABLED=true \
  -e EMQX_LISTENERS__SSL__NOBS__ENABLE_AUTHN=false \
  -e EMQX_LISTENERS__SSL__NOBS__SSL_OPTIONS__CACERTFILE=/etc/ssl/localhost/cert.pem \
  -e EMQX_LISTENERS__SSL__NOBS__SSL_OPTIONS__CERTFILE=/etc/ssl/localhost/cert.pem \
  -e EMQX_LISTENERS__SSL__NOBS__SSL_OPTIONS__KEYFILE=/etc/ssl/localhost/privkey.pem \
  -e EMQX_LISTENERS__SSL__NOBS__BIND=8883 \
  -p 18083:18083 -p 1883:1883 -p8883:8883 \
  emqx:latest
```

You should test like this:

```shell
./mvnw exec:java -Dexec.mainClass=com.molecoding.nobs.AwesomeMqttApp \
  -Dexec.args="ssl://localhost:8883 -m abc -t nobs -c tmp/localhost/cert.pem"
```
