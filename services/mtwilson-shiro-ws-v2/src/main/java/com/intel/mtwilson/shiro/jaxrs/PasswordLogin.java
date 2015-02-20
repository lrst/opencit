/*
 * Copyright (C) 2013 Intel Corporation
 * All rights reserved.
 */
package com.intel.mtwilson.shiro.jaxrs;

import com.intel.mtwilson.shiro.EncryptedTokenContent;
import com.intel.dcsg.cpg.authz.token.TokenFactory;
import com.intel.mtwilson.jaxrs2.mediatype.DataMediaType;
import com.intel.mtwilson.launcher.ws.ext.V2;
import com.intel.mtwilson.shiro.authc.password.LoginPasswordId;
import com.intel.mtwilson.shiro.UserId;
import com.intel.mtwilson.shiro.Username;
import com.thoughtworks.xstream.XStream;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.Iterator;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.annotation.RequiresGuest;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.Subject;

/**
 * The token generated by this class contains the following information: UserId
 * Username LoginPasswordId
 *
 * These correspond to the principals that are set by the JdbcPasswordRealm
 * against which we are authenticating the user.
 *
 * We explicitly serialize these three values and then reconstruct the
 * principals from them as necessary because the authorization token should not
 * allow arbitrary (blind) reconstruction of principals, or else an attacker who
 * compromises the authorization token key would be able to construct any set of
 * principals and pass them to the server in an attack token and elevate
 * privileges. By explicitly storing the LoginPasswordId, UserId, and Username
 * we limit any hacking of the token to the same privileges that would have been
 * available if the attacker had stolen the user's password.
 *
 * @author jbuhacoff
 */
@V2
@Path("/login")
public class PasswordLogin {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PasswordLogin.class);

//    private TokenFactory factory;
    @RequiresGuest
    @POST
    @Consumes({MediaType.APPLICATION_FORM_URLENCODED})
//    public void submitLoginForm(@Context final HttpServletRequest request, @Context final HttpServletResponse response, @FormParam("username") String username, @FormParam("password") String password) {
    public void submitLoginForm(@Context final HttpServletRequest request, @Context final HttpServletResponse response, @BeanParam PasswordLoginRequest passwordLoginRequest) throws GeneralSecurityException {
//        log.debug("submitLoginForm username {} password {}", username, password);
        log.debug("submitLoginForm beanparam username {} password {}", passwordLoginRequest.getUsername(), passwordLoginRequest.getPassword());
        log.debug("request from {}", request.getRemoteHost());

        PasswordLoginResponse passwordLoginResponse = loginRequest(request, response, passwordLoginRequest);
        log.debug("Successfully processed login request with auth token {}.", passwordLoginResponse.getAuthorizationToken());
    }

    @RequiresGuest
    @POST
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, DataMediaType.APPLICATION_YAML, DataMediaType.TEXT_YAML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML, DataMediaType.APPLICATION_YAML, DataMediaType.TEXT_YAML})
    public PasswordLoginResponse loginRequest(@Context final HttpServletRequest request, @Context final HttpServletResponse response, PasswordLoginRequest loginForm) throws GeneralSecurityException {
        log.debug("loginRequest username {} password {}", loginForm.getUsername(), loginForm.getPassword());
        log.debug("request from {}", request.getRemoteHost());

        // authenticate the user with JdbcPasswordRealm and PasswordCredentialsMatcher (configured in shiro.ini)
        Subject currentUser = SecurityUtils.getSubject();
//        if( !currentUser.isAuthenticated() ) { // shouldn't need this because we have @RequiresGuest annotation...
        log.debug("authenticating...");
        // for this junit test we're using mtwilson.api.username and mtwilson.api.password properties from  mtwilson.properties on the local system, c:/mtwilson/configuration/mtwilson.properties is default location on windows 
        UsernamePasswordToken loginToken = new UsernamePasswordToken(loginForm.getUsername(), loginForm.getPassword());
//            UsernamePasswordToken token = new UsernamePasswordToken("root", "root"); // guest doesn't need a password
        loginToken.setRememberMe(false); // we could pass in a parameter with the form but we don't need this
        currentUser.login(loginToken); // throws UnknownAccountException , IncorrectCredentialsException , LockedAccountException , other specific exceptions, and AuthenticationException 

        if (!currentUser.isAuthenticated()) {
            throw new WebApplicationException(Status.UNAUTHORIZED);
        }

        log.info("logged in as {}", currentUser.getPrincipal());
        PrincipalCollection principals = currentUser.getPrincipals();

        Collection<Username> usernames = principals.byType(Username.class);
        log.debug("Found {} username principals", usernames.size());
//        Collection<UserId> userIds = principals.byType(UserId.class);
//        Collection<LoginPasswordId> loginPasswordIds = principals.byType(LoginPasswordId.class);

        Username username = getFirstElementFromCollection(usernames);
//        UserId userId = getFirstElementFromCollection(userIds);
//        LoginPasswordId loginPasswordId = getFirstElementFromCollection(loginPasswordIds);
        if ( username == null /* || userId == null || loginPasswordId == null */ ) {
            log.error("One of the required parameters is missing. Login request cannot be processed");
            throw new IllegalStateException();
        }
        
        // this block of code repeated in EncryptedTokenAuthenticationFilter
        EncryptedTokenContent tokenContent = new EncryptedTokenContent();
//        tokenContent.loginPasswordId = loginPasswordId.getLoginPasswordId().toString(); // passwordLoginIds.iterator().next().getLoginPasswordId().toString();
//        tokenContent.userId = userId.getUserId().toString(); // userIds.iterator().next().getUserId().toString();
        tokenContent.username = username.getUsername(); // usernames.iterator().next().getUsername();
        XStream xs = new XStream();
        String tokenContentXml = xs.toXML(tokenContent);
        log.debug("tokenContent xml: {}", tokenContentXml);


        // create the token
        TokenFactory factory = new TokenFactory();
        String authorizationToken = factory.create(tokenContentXml);

        // include token in response headers
        response.addHeader("Authorization-Token", authorizationToken);


        PasswordLoginResponse passwordLoginResponse = new PasswordLoginResponse();
        passwordLoginResponse.setAuthorizationToken(authorizationToken);

        return passwordLoginResponse;
    }
    
    private <T> T getFirstElementFromCollection(Collection<T> collection) {
        if( collection != null ) {
            Iterator<T> iterator = collection.iterator();
            if (iterator.hasNext()) {
                return iterator.next();
            }
        }
        return null;
    }
}
