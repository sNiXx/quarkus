package io.quarkus.vertx.http.mtls;

import static org.hamcrest.Matchers.is;

import java.io.File;
import java.net.URL;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.vertx.http.runtime.security.QuarkusHttpUser;
import io.restassured.RestAssured;
import io.vertx.ext.web.Router;
import me.escoffier.certs.Format;
import me.escoffier.certs.junit5.Certificate;
import me.escoffier.certs.junit5.Certificates;

@Certificates(baseDir = "target/certs", certificates = @Certificate(name = "mtls-test", password = "secret", formats = {
        Format.JKS, Format.PKCS12, Format.PEM }, client = true))
public class MtlsRequiredTest {

    private static final String configuration = """
            quarkus.http.ssl.certificate.key-store-file=server-keystore.jks
            quarkus.http.ssl.certificate.key-store-password=secret
            quarkus.http.ssl.certificate.trust-store-file=server-truststore.jks
            quarkus.http.ssl.certificate.trust-store-password=secret
            quarkus.http.ssl.client-auth=REQUIRED
            quarkus.http.insecure-requests=enabled

            quarkus.http.auth.permission.default.paths=/*
            quarkus.http.auth.permission.default.policy=authenticated
            """;

    @TestHTTPResource(value = "/mtls", ssl = true)
    URL url;

    @TestHTTPResource(value = "/mtls", ssl = false)
    URL urlNoTls;

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot((jar) -> jar
                    .addClasses(MyBean.class)
                    .addAsResource(new StringAsset(configuration), "application.properties")
                    .addAsResource(new File("target/certs/mtls-test-keystore.jks"), "server-keystore.jks")
                    .addAsResource(new File("target/certs/mtls-test-server-truststore.jks"), "server-truststore.jks"));

    @Test
    public void testClientAuthentication() {
        RestAssured.given()
                .keyStore("target/certs/mtls-test-client-keystore.jks", "secret")
                .trustStore("target/certs/mtls-test-client-truststore.jks", "secret")
                .get(url).then().statusCode(200).body(is("CN=localhost"));
    }

    @Test
    public void testNoClientCert() {
        RestAssured.given()
                .get(urlNoTls).then().statusCode(401);
    }

    @ApplicationScoped
    static class MyBean {

        public void register(@Observes Router router) {
            router.get("/mtls").handler(rc -> {
                rc.response().end(QuarkusHttpUser.class.cast(rc.user()).getSecurityIdentity().getPrincipal().getName());
            });
        }

    }
}