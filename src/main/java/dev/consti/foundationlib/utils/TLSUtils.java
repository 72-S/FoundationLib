package dev.consti.foundationlib.utils;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Date;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * TLSUtils provides utility methods to create SSLContext instances
 * for server and client, as well as a method to generate random secrets.
 */
public final class TLSUtils {

    private TLSUtils() {
        throw new UnsupportedOperationException("TLSUtils is a utility class and cannot be instantiated.");
    }

    // static {
    // Security.addProvider(new BouncyCastleProvider());
    // }

    /**
     * Creates an SSLContext for use by a server, using a self-signed certificate.
     * 
     * @return an SSLContext configured for server use.
     * @throws Exception if there is an error generating the SSLContext.
     */
    public static SSLContext createServerSSLContext(String SAN) throws Exception {
        bouncyCastleProvider();
        KeyPair keyPair = generateKeyPair();
        X509Certificate certificate = generateSelfSignedCertificate(keyPair, SAN);
        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, null);
        keyStore.setKeyEntry("server", keyPair.getPrivate(), "password".toCharArray(),
                new X509Certificate[] { certificate });

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, "password".toCharArray());

        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        sslContext.init(kmf.getKeyManagers(), null, new SecureRandom());
        return sslContext;
    }

    private static void bouncyCastleProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Creates an SSLContext for use by a client that trusts all certificates.
     * 
     * @return an SSLContext configured for client use, trusting all certificates.
     * @throws Exception if there is an error generating the SSLContext.
     */
    public static SSLContext createClientSSLContext() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                        }

                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                        }

                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
        };

        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        sslContext.init(null, trustAllCerts, new SecureRandom());
        return sslContext;
    }

    /**
     * Generates a self-signed X509 certificate for the server.
     * 
     * @param keyPair the KeyPair used to generate the certificate.
     * @return a self-signed X509Certificate.
     * @throws Exception if there is an error creating the certificate.
     */

    private static X509Certificate generateSelfSignedCertificate(KeyPair keyPair, String SAN) throws Exception {
        X500Name issuer = new X500Name("CN=Server");
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Date notBefore = new Date(System.currentTimeMillis() - 10000);
        Date notAfter = new Date(System.currentTimeMillis() + 365L * 86400000L);

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer, serial, notBefore, notAfter, issuer, keyPair.getPublic());

        certBuilder.addExtension(
                Extension.keyUsage,
                true,
                new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));

        GeneralName sanName = SAN.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")
                ? new GeneralName(GeneralName.iPAddress, SAN)
                : new GeneralName(GeneralName.dNSName, SAN);
        GeneralNames subjectAltNames = new GeneralNames(new GeneralName[] {
                new GeneralName(GeneralName.dNSName, "localhost"),
                new GeneralName(GeneralName.iPAddress, "127.0.0.1"),
                sanName
        });
        certBuilder.addExtension(Extension.subjectAlternativeName, false, subjectAltNames);

        certBuilder.addExtension(
                Extension.extendedKeyUsage,
                false,
                new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(keyPair.getPrivate());
        X509CertificateHolder certHolder = certBuilder.build(signer);
        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(certHolder);
    }

    /**
     * Generates an RSA KeyPair for use in certificate generation.
     * 
     * @return a KeyPair containing RSA keys.
     * @throws Exception if there is an error generating the KeyPair.
     */
    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        return keyGen.generateKeyPair();
    }

    /**
     * Generates a random secret key as a string.
     * 
     * @return a randomly generated secret string.
     */
    public static String generateSecret() {
        return new BigInteger(256, new SecureRandom()).toString(32);
    }
}
