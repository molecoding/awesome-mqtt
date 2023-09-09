package com.molecoding.nobs;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.mqttv5.client.*;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import picocli.CommandLine;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.UUID;
import java.util.concurrent.Callable;

@Slf4j
@CommandLine.Command(
  name = "awesome-mqtt",
  mixinStandardHelpOptions = true,
  description = "awesome mqtt cli to test connection and write")
public class AwesomeMqttApp implements Callable<Integer> {
  @CommandLine.Option(
    names = {"-h", "--help"},
    usageHelp = true,
    description = "show help message")
  private boolean helpRequested;

  @CommandLine.Option(
    names = {"-u", "--user"},
    description = "username")
  private String user;

  @CommandLine.Option(
    names = {"-p", "--password"},
    description = "password")
  private String password;

  @CommandLine.Option(
    names = {"-t", "--topic"},
    description = "topic")
  private String topic;

  @CommandLine.Option(
    names = {"-m", "--message"},
    description = "message")
  private String message;

  @CommandLine.Option(
    names = {"-c", "--cert"},
    description = "ca cert file")
  private String cacert;

  @CommandLine.Parameters(
    paramLabel = "<url>",
    index = "0",
    arity = "1",
    description = "server url")
  private String uri;

  public static void main(String[] args) throws InterruptedException {
    int exitCode = new CommandLine(new AwesomeMqttApp()).execute(args);
    Thread.currentThread().join();
    System.exit(exitCode);
  }

  public static SSLSocketFactory getSocketFactory(String caCertPath) throws Exception {
    CertificateFactory certFactory = CertificateFactory.getInstance("X.509");

    InputStream fis = null;
    Path p = Paths.get(caCertPath);
    if (Files.exists(p)) {
      try {
        fis = new FileInputStream(p.toFile());
        log.info("load cert from file system: {}", caCertPath);
      } catch (Exception ignored) {

      }
    } else {
      try {
        fis = AwesomeMqttApp.class.getClassLoader().getResourceAsStream(caCertPath);
        log.info("load cert from classpath: {}", caCertPath);
      } catch (Exception ignored) {

      }
    }
    if (fis == null) {
      throw new Exception("cert file " + caCertPath + "not found");
    }
    Certificate caCert = certFactory.generateCertificate(fis);
    X509Certificate x509CaCert = (X509Certificate) caCert;

    KeyStore caKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
    caKeyStore.load(null, null);
    caKeyStore.setCertificateEntry("cacert", x509CaCert);

    TrustManagerFactory tmFactory =
      TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    tmFactory.init(caKeyStore);

    SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
    sslContext.init(null, tmFactory.getTrustManagers(), null);

    return sslContext.getSocketFactory();
  }

  @Override
  public Integer call() {
    log.info("Try to connect to uri [{}]", uri);
    MqttClient client = null;
    try {
      final String clientId = UUID.randomUUID().toString();
      client = new MqttClient(uri, clientId, new MemoryPersistence());

      client.setCallback(new MqttCallback() {
        @Override
        public void disconnected(MqttDisconnectResponse disconnectResponse) {
          log.info("disconnected");
        }

        @Override
        public void mqttErrorOccurred(MqttException exception) {
          log.error("error", exception);
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) throws Exception {
          log.info("message in callback: {}", new String(message.getPayload()));
        }

        @Override
        public void deliveryComplete(IMqttToken token) {

        }

        @Override
        public void connectComplete(boolean reconnect, String serverURI) {
          log.info("connect complete");
        }

        @Override
        public void authPacketArrived(int reasonCode, MqttProperties properties) {

        }
      });

      MqttConnectionOptions options = new MqttConnectionOptions();
      options.setAutomaticReconnect(true);
      options.setCleanStart(true);
      options.setConnectionTimeout(10);

      //      options.setHttpsHostnameVerificationEnabled(false);
      if (cacert != null) {
        options.setSocketFactory(getSocketFactory(cacert));
      }

      if (user != null) {
        options.setUserName(user);
      }
      if (password != null) {
        options.setPassword(password.getBytes());
      }

      client.connect(options);
      log.info("Client [{}] connected to server [{}]", clientId, client.getServerURI());

      if (message != null && !message.isEmpty()) {
        if (topic == null) {
          log.error("If a message is provided, please specify the *topic* too");
          client.disconnect();
          return 4;
        }
        byte[] payload = message.getBytes();
        MqttMessage msg = new MqttMessage(payload);

        msg.setQos(0);
        msg.setRetained(false);

        client.publish(topic, msg);

        log.info("Message [{}] published to topic [{}]", message, topic);

        client.disconnect();
        log.info("Client disconnected");
      } else {
        if (topic != null) {
          log.info("try to subscribe on topic [{}]", topic);

          client.subscribe(
            new MqttSubscription[]{new MqttSubscription(topic, 0)},
            new IMqttMessageListener[]{
              (topic, message) ->
                log.info("received message: [{}]", new String(message.getPayload()))
            });
          log.info("topic subscribed");
        }
      }

    } catch (MqttException e) {
      log.error("Mqtt error", e);
      return 2;
    } catch (Exception e) {
      log.error("Common error", e);
      return 1;
    }
    return 0;
  }
}
