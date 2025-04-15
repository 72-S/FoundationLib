package dev.consti.foundationlib.utils;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import javax.net.ssl.*;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public final class TLSUtils {

    private TLSUtils() {
        throw new UnsupportedOperationException("TLSUtils is a utility class and cannot be instantiated.");
    }

    public static SSLContext createServerSSLContext(String SAN, Path certDir) throws Exception {
        bouncyCastleProvider();
        Path certPath = certDir.resolve("cert.pem");
        Path keyPath = certDir.resolve("key.pem");

        KeyPair keyPair;
        X509Certificate certificate;

        if (Files.exists(certPath) && Files.exists(keyPath)) {
            keyPair = loadKeyPair(certPath, keyPath);
            certificate = loadCertificate(certPath);
        } else {
            keyPair = generateKeyPair();
            certificate = generateSelfSignedCertificate(keyPair, SAN);
            Files.createDirectories(certDir);
            saveKeyPair(keyPair, keyPath);
            saveCertificate(certificate, certPath);
        }

        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null, null);
        keyStore.setKeyEntry("server", keyPair.getPrivate(), "password".toCharArray(), new Certificate[]{certificate});

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, "password".toCharArray());

        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        sslContext.init(kmf.getKeyManagers(), null, new SecureRandom());
        return sslContext;
    }

    public static SSLContext createClientSSLContext(Path certPath) throws Exception {
        byte[] certBytes = Files.readAllBytes(certPath);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Certificate cert = cf.generateCertificate(new ByteArrayInputStream(certBytes));

        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        trustStore.load(null, null);
        trustStore.setCertificateEntry("velocity", cert);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLSv1.3");
        sslContext.init(null, tmf.getTrustManagers(), new SecureRandom());
        return sslContext;
    }

    private static void saveKeyPair(KeyPair keyPair, Path keyPath) throws IOException {
        byte[] encoded = keyPair.getPrivate().getEncoded();
        writePemFile("PRIVATE KEY", encoded, keyPath);
    }

    private static void saveCertificate(X509Certificate certificate, Path certPath) throws Exception {
        writePemFile("CERTIFICATE", certificate.getEncoded(), certPath);
    }

    private static KeyPair loadKeyPair(Path certPath, Path keyPath) throws Exception {
        byte[] certBytes = Files.readAllBytes(certPath);
        byte[] keyBytes = readPemFile(keyPath, "PRIVATE KEY");

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Certificate cert = cf.generateCertificate(new ByteArrayInputStream(certBytes));

        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        RSAPrivateKey privateKey = (RSAPrivateKey) kf.generatePrivate(keySpec);
        return new KeyPair(cert.getPublicKey(), privateKey);
    }

    private static X509Certificate loadCertificate(Path certPath) throws Exception {
        byte[] certBytes = Files.readAllBytes(certPath);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certBytes));
    }

    private static void writePemFile(String type, byte[] content, Path path) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write("-----BEGIN " + type + "-----\n");
            writer.write(Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(content));
            writer.write("\n-----END " + type + "-----\n");
        }
    }

    private static byte[] readPemFile(Path path, String type) throws IOException {
        String content = Files.readString(path);
        String base64 = content.replace("-----BEGIN " + type + "-----", "")
                .replace("-----END " + type + "-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }

    private static void bouncyCastleProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        return keyGen.generateKeyPair();
    }

    private static X509Certificate generateSelfSignedCertificate(KeyPair keyPair, String SAN) throws Exception {
        X500Name issuer = new X500Name("CN=Server");
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Date notBefore = new Date(System.currentTimeMillis() - 10000);
        Date notAfter = new Date(System.currentTimeMillis() + 365L * 86400000L);

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer, serial, notBefore, notAfter, issuer, keyPair.getPublic());

        certBuilder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));

        GeneralName sanName = SAN.matches("\\d+\\.\\d+\\.\\d+\\.\\d+")
                ? new GeneralName(GeneralName.iPAddress, SAN)
                : new GeneralName(GeneralName.dNSName, SAN);

        GeneralNames subjectAltNames = new GeneralNames(new GeneralName[]{
                new GeneralName(GeneralName.dNSName, "localhost"),
                new GeneralName(GeneralName.iPAddress, "127.0.0.1"),
                sanName
        });
        certBuilder.addExtension(Extension.subjectAlternativeName, false, subjectAltNames);

        certBuilder.addExtension(Extension.extendedKeyUsage, false,
                new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(keyPair.getPrivate());
        X509CertificateHolder certHolder = certBuilder.build(signer);
        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(certHolder);
    }

    public static String generateSecret() {
        return new BigInteger(256, new SecureRandom()).toString(32);
    }
} 
