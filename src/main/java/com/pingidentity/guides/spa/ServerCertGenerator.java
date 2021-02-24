/*
 * Copyright (C) 2020 Ping Identity Corporation
 * All rights reserved.
 *
 * The contents of this file are the property of Ping Identity Corporation.
 * You may not copy or use this file, in either source code or executable
 * form, except in compliance with terms set by Ping Identity Corporation.
 * For further information please contact:
 *
 * Ping Identity Corporation
 * 1001 17th St Suite 100
 * Denver, CO 80202
 * 303.468.2900
 * http://www.pingidentity.com
 */

package com.pingidentity.guides.spa;

import sun.security.x509.*;
import sun.security.util.ObjectIdentifier;

import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.sql.Date;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Vector;

/**
 * A utility to generate the server.p12 file to be used by the Spring Boot server.
 */
@SuppressWarnings("sunapi")
public final class ServerCertGenerator
{
    private static final String P12_PASSWORD = "password";
    private static final int[] SERVER_AUTH_KEY_USAGE_OID = {1, 3, 6, 1, 5, 5, 7, 3, 1};

    private ServerCertGenerator()
    {
    }

    public static void main(String[] args) throws Exception
    {
        if (args.length != 1)
        {
            System.out.println("Usage: program <destination path for server certificate .p12 file>");
            System.exit(1);
        }

        Path serverCertPath = Paths.get(args[0]);
        if (Files.exists(serverCertPath))
        {
            // Don't recreate the server cert, it already exists
            System.out.printf("%s already exists, not generating a server certificate.%n", serverCertPath);
            return;
        }

        generateCert(serverCertPath);
        System.out.printf("Generated server certificate in %s.", serverCertPath);
    }

    private static void generateCert(Path serverCertPath) throws Exception
    {
        SecureRandom secureRandom = SecureRandom.getInstance("SHA1PRNG");

        KeyPair keyPair = generateKeyPair();

        X500Name name = new X500Name("CN=localhost");

        Instant now = Instant.now();
        Instant notBefore = now.minus(10, ChronoUnit.MINUTES);
        Instant notAfter = now.plus(365, ChronoUnit.DAYS);

        CertificateSerialNumber serialNumber = new CertificateSerialNumber(new BigInteger(64, secureRandom));

        X509CertInfo certInfo = new X509CertInfo();
        certInfo.set(X509CertInfo.VERSION, new CertificateVersion(CertificateVersion.V3));
        certInfo.set(X509CertInfo.SERIAL_NUMBER, serialNumber);
        certInfo.set(X509CertInfo.SUBJECT, name);
        certInfo.set(X509CertInfo.ISSUER, name);
        certInfo.set(X509CertInfo.VALIDITY, new CertificateValidity(Date.from(notBefore), Date.from(notAfter)));
        certInfo.set(X509CertInfo.KEY, new CertificateX509Key(keyPair.getPublic()));
        certInfo.set(X509CertInfo.ALGORITHM_ID, new CertificateAlgorithmId(AlgorithmId.get("SHA256withRSA")));

        CertificateExtensions extensions = new CertificateExtensions();

        GeneralNames names = new GeneralNames();
        names.add(new GeneralName(new DNSName("localhost")));
        names.add(new GeneralName(new DNSName("host.docker.internal")));
        SubjectAlternativeNameExtension altNameExtension = new SubjectAlternativeNameExtension(false, names);
        extensions.set(PKIXExtensions.SubjectAlternativeName_Id.toString(), altNameExtension);

        /*
        AuthorityKeyIdentifierExtension authorityKeyIdentifierExtension =
                new AuthorityKeyIdentifierExtension(new KeyIdentifier(keyPair.getPublic()),
                                                    null,
                                                    serialNumber.get(CertificateSerialNumber.NUMBER));
        extensions.set(PKIXExtensions.AuthorityKey_Id.toString(), authorityKeyIdentifierExtension);
         */

        Vector<ObjectIdentifier> oids = new Vector<>();
        oids.add(new ObjectIdentifier(SERVER_AUTH_KEY_USAGE_OID));
        ExtendedKeyUsageExtension keyUsageExtension = new ExtendedKeyUsageExtension(false, oids);
        extensions.set(ExtendedKeyUsageExtension.NAME, keyUsageExtension);

        certInfo.set(CertificateExtensions.NAME, extensions);

        X509CertImpl cert = new X509CertImpl(certInfo);
        cert.sign(keyPair.getPrivate(), "SHA256withRSA");

        try (OutputStream out = Files.newOutputStream(serverCertPath))
        {
            KeyStore p12File = KeyStore.getInstance("PKCS12");
            p12File.load(null, P12_PASSWORD.toCharArray());
            p12File.setEntry("cert",
                             new KeyStore.PrivateKeyEntry(keyPair.getPrivate(),
                                                          new Certificate[]{ cert }),
                             new KeyStore.PasswordProtection(P12_PASSWORD.toCharArray()));

            p12File.store(out, P12_PASSWORD.toCharArray());
            out.flush();
        }
    }

    private static KeyPair generateKeyPair() throws NoSuchAlgorithmException
    {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
