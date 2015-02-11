/*
 * Copyright (C) 2014 Intel Corporation
 * All rights reserved.
 */
package com.intel.mtwilson.trustagent.setup;

import com.intel.dcsg.cpg.tls.policy.TlsConnection;
import com.intel.dcsg.cpg.tls.policy.TlsPolicy;
import com.intel.dcsg.cpg.tls.policy.TlsPolicyBuilder;
import com.intel.mtwilson.as.rest.v2.model.SigningKeyEndorsementRequest;
import com.intel.mtwilson.attestation.client.jaxrs.HostTpmKeys;
import com.intel.mtwilson.setup.AbstractSetupTask;
import com.intel.mtwilson.trustagent.TrustagentConfiguration;
import java.io.File;
import java.net.URL;
import java.util.Properties;

/**
 *
 * @author ssbangal
 */
public class CertifySigningKey extends AbstractSetupTask {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CertifySigningKey.class);
    
    private TrustagentConfiguration trustagentConfiguration;
    private File signingKeyPem;
    private String url;
    private String username;
    private String password;
    private File keystoreFile;
    private String keystorePassword;
    
    @Override
    protected void configure() throws Exception {
        trustagentConfiguration = new TrustagentConfiguration(getConfiguration());
        url = trustagentConfiguration.getMtWilsonApiUrl();
        if( url == null || url.isEmpty() ) {
            configuration("Mt Wilson URL is not set");
        }
        username = trustagentConfiguration.getMtWilsonApiUsername();
        password = trustagentConfiguration.getMtWilsonApiPassword();
        if( username == null || username.isEmpty() ) {
            configuration("Mt Wilson username is not set");
        }
        if( password == null || password.isEmpty() ) {
            configuration("Mt Wilson password is not set");
        }
        
        keystoreFile = trustagentConfiguration.getTrustagentKeystoreFile();
        if( keystoreFile == null || !keystoreFile.exists() ) {
            configuration("Trust Agent keystore does not exist");
        }
        keystorePassword = trustagentConfiguration.getTrustagentKeystorePassword();
        if( keystorePassword == null || keystorePassword.isEmpty() ) {
            configuration("Trust Agent keystore password is not set");
        }        
        
    }

    @Override
    protected void validate() throws Exception {
        trustagentConfiguration = new TrustagentConfiguration(getConfiguration());
        
        // Now check for the existence of the MTW signed PEM file.
        signingKeyPem = trustagentConfiguration.getSigningKeyX509CertificateFile();
        if (signingKeyPem == null || !signingKeyPem.exists()) {
            validation("MTW signed Signing Key certificate does not exist.");
        }        
    }

    @Override
    protected void execute() throws Exception {
               
        String tcgCertPath = trustagentConfiguration.getSigningKeyTCGCertificateFile().getAbsolutePath(); 
        String pubKeyModulus = trustagentConfiguration.getSigningKeyModulusFile().getAbsolutePath();
        
        log.debug("TCG Cert path is : {}", tcgCertPath);
        log.debug("Public key modulus path is : {}", pubKeyModulus);
        
        SigningKeyEndorsementRequest obj = new SigningKeyEndorsementRequest();
        obj.setPublicKeyModulus(SetupUtils.readblob(pubKeyModulus));
        obj.setTpmCertifyKey(SetupUtils.readblob(tcgCertPath));
        
        log.debug("Creating TLS policy");
        TlsPolicy tlsPolicy = TlsPolicyBuilder.factory().strictWithKeystore(trustagentConfiguration.getTrustagentKeystoreFile(), 
                trustagentConfiguration.getTrustagentKeystorePassword()).build();
        TlsConnection tlsConnection = new TlsConnection(new URL(url), tlsPolicy);
        
        Properties clientConfiguration = new Properties();
        clientConfiguration.setProperty(TrustagentConfiguration.MTWILSON_API_USERNAME, username);
        clientConfiguration.setProperty(TrustagentConfiguration.MTWILSON_API_PASSWORD, password);
        
        HostTpmKeys client = new HostTpmKeys(clientConfiguration, tlsConnection);
        String signingKeyPemCertificate = client.createSigningKeyCertificate(obj);
        log.debug("MTW signed PEM certificate is {} ", signingKeyPemCertificate);
        
        SetupUtils.writeString(trustagentConfiguration.getSigningKeyX509CertificateFile().getAbsolutePath(), signingKeyPemCertificate);
        log.info("Successfully created the MTW signed X509Certificate for the signing key and stored at {}.", 
                trustagentConfiguration.getSigningKeyX509CertificateFile().getAbsolutePath());
    }    
}
