/* Copyright (c) 2001 - 2013 OpenPlans - www.openplans.org. All rights reserved.
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */


package org.geoserver.security.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.geoserver.data.test.SystemTestData;
import org.geoserver.security.ConstantFilterChain;
import org.geoserver.security.GeoServerSecurityFilterChain;
import org.geoserver.security.GeoServerSecurityManager;
import org.geoserver.security.RequestFilterChain;
import org.geoserver.security.config.BasicAuthenticationFilterConfig;
import org.geoserver.security.config.DigestAuthenticationFilterConfig;
import org.geoserver.security.config.J2eeAuthenticationFilterConfig;
import org.geoserver.security.config.LogoutFilterConfig;
import org.geoserver.security.config.PreAuthenticatedUserNameFilterConfig.RoleSource;
import org.geoserver.security.config.RequestHeaderAuthenticationFilterConfig;
import org.geoserver.security.config.SecurityFilterConfig;
import org.geoserver.security.config.SecurityManagerConfig;
import org.geoserver.security.config.UsernamePasswordAuthenticationFilterConfig;
import org.geoserver.security.config.X509CertificateAuthenticationFilterConfig;
import org.geoserver.security.filter.GeoServerBasicAuthenticationFilter;
import org.geoserver.security.filter.GeoServerDigestAuthenticationFilter;
import org.geoserver.security.filter.GeoServerJ2eeAuthenticationFilter;
import org.geoserver.security.filter.GeoServerLogoutFilter;
import org.geoserver.security.filter.GeoServerRequestHeaderAuthenticationFilter;
import org.geoserver.security.filter.GeoServerRoleFilter;
import org.geoserver.security.filter.GeoServerUserNamePasswordAuthenticationFilter;
import org.geoserver.security.filter.GeoServerX509CertificateAuthenticationFilter;
import org.geoserver.security.impl.GeoServerRole;
import org.geoserver.security.impl.GeoServerUser;
import org.geoserver.test.RunTestSetup;
import org.geoserver.test.SystemTest;
import org.geoserver.test.TestSetup;
import org.geoserver.test.TestSetupFrequency;
import org.geotools.data.Base64;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import com.mockrunner.mock.web.MockFilterChain;
import com.mockrunner.mock.web.MockHttpServletRequest;
import com.mockrunner.mock.web.MockHttpServletResponse;

@Category(SystemTest.class)
@TestSetup(run=TestSetupFrequency.REPEAT)
public class AuthenticationFilterTest extends AbstractAuthenticationProviderTest {
    
    public final static String testFilterName = "basicAuthTestFilter";
    public final static String testFilterName2 = "digestAuthTestFilter";
    public final static String testFilterName3 = "j2eeAuthTestFilter";
    public final static String testFilterName4 = "requestHeaderTestFilter";
    public final static String testFilterName5 = "basicAuthTestFilterWithRememberMe";
    public final static String testFilterName6 = "formLoginTestFilter";
    public final static String testFilterName7 = "formLoginTestFilterWithRememberMe";
    public final static String testFilterName8 = "x509TestFilter";
    public final static String testFilterName9 = "logoutTestFilter";

    @Override
    protected void onSetUp(SystemTestData testData) throws Exception {
        super.onSetUp(testData);
        
        LogoutFilterConfig loConfig = new LogoutFilterConfig();
        loConfig.setClassName(GeoServerLogoutFilter.class.getName());
        loConfig.setName(testFilterName9);
        loConfig.setRedirectURL(GeoServerLogoutFilter.URL_AFTER_LOGOUT);
        getSecurityManager().saveFilter(loConfig);
        
        BasicAuthenticationFilterConfig bconfig = new BasicAuthenticationFilterConfig();
        bconfig.setClassName(GeoServerBasicAuthenticationFilter.class.getName());
        bconfig.setUseRememberMe(false);
        bconfig.setName(testFilterName);
        getSecurityManager().saveFilter(bconfig);
    }

    @Before
    public void revertFilters() throws Exception {
        GeoServerSecurityManager secMgr = getSecurityManager();
        if (secMgr.listFilters().contains(testFilterName2)) {
            SecurityFilterConfig config = secMgr.loadFilterConfig(testFilterName2);
            secMgr.removeFilter(config);
        }
    }

    @Test
    public void testBasicAuth() throws Exception{
//        BasicAuthenticationFilterConfig config = new BasicAuthenticationFilterConfig();
//        config.setClassName(GeoServerBasicAuthenticationFilter.class.getName());
//        config.setUseRememberMe(false);
//        config.setName(testFilterName);
        
//        getSecurityManager().saveFilter(config);
        prepareFilterChain(pattern,                
            testFilterName);

        modifyChain(pattern, false, true,null);

        SecurityContextHolder.getContext().setAuthentication(null);
        
        // Test entry point                
        MockHttpServletRequest request= createRequest("/foo/bar");
        MockHttpServletResponse response= new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();        
        
        
        getProxy().doFilter(request, response, chain);
        String tmp = response.getHeader("WWW-Authenticate");
        assertNotNull(tmp);
        assert(tmp.indexOf(GeoServerSecurityManager.REALM) !=-1 );
        assert(tmp.indexOf("Basic") !=-1 );
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getErrorCode());
        SecurityContext ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNull(ctx);
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        
        modifyChain(pattern, false, true,GeoServerSecurityFilterChain.ROLE_FILTER);
        // check success
        request= createRequest("/foo/bar");
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();        

        request.addHeader("Authorization",  "Basic " + 
                new String(Base64.encodeBytes((testUserName+":"+testPassword).getBytes())));
        getProxy().doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
        ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNotNull(ctx);
        Authentication auth = ctx.getAuthentication();
        assertNotNull(auth);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        checkForAuthenticatedRole(auth);
        assertEquals(testUserName, ((UserDetails) auth.getPrincipal()).getUsername());
        assertTrue(auth.getAuthorities().contains(new GeoServerRole(rootRole)));
        assertTrue(auth.getAuthorities().contains(new GeoServerRole(derivedRole)));
        
        String roleString=response.getHeader(GeoServerRoleFilter.DEFAULT_HEADER_ATTRIBUTE);
        assertNotNull(roleString);
        String[] roles = roleString.split(";");
        assertEquals(3, roles.length);
        List<String> roleList = Arrays.asList(roles);
        assertTrue(roleList.contains(GeoServerRole.AUTHENTICATED_ROLE.getAuthority()));
        assertTrue(roleList.contains(rootRole));
        assertTrue(roleList.contains(derivedRole));
        
        // check wrong password
        request= createRequest("/foo/bar");
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();

        request.addHeader("Authorization",  "Basic " + 
                new String(Base64.encodeBytes((testUserName+":wrongpass").getBytes())));
        getProxy().doFilter(request, response, chain);
        tmp = response.getHeader("WWW-Authenticate");
        assertNotNull(tmp);
        assert(tmp.indexOf(GeoServerSecurityManager.REALM) !=-1 );
        assert(tmp.indexOf("Basic") !=-1 );
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getErrorCode());
        ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNull(ctx);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        
        // check unknown user
        request= createRequest("/foo/bar");
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();

        request.addHeader("Authorization",  "Basic " + 
                new String(Base64.encodeBytes(("unknwon:"+testPassword).getBytes())));
        getProxy().doFilter(request, response, chain);
        tmp = response.getHeader("WWW-Authenticate");
        assertNotNull(tmp);
        assert(tmp.indexOf(GeoServerSecurityManager.REALM) !=-1 );
        assert(tmp.indexOf("Basic") !=-1 );
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getErrorCode());
        ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNull(ctx);
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        // check root user
        request= createRequest("/foo/bar");
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();        
        
        request.addHeader("Authorization",  "Basic " + 
                new String(Base64.encodeBytes((GeoServerUser.ROOT_USERNAME+":"+getMasterPassword()).getBytes())));
        getProxy().doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
        ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        auth = ctx.getAuthentication();
        assertNotNull(auth);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        //checkForAuthenticatedRole(auth);
        assertEquals(GeoServerUser.ROOT_USERNAME, auth.getPrincipal());
        assertTrue(auth.getAuthorities().size()==1);
        assertTrue(auth.getAuthorities().contains(GeoServerRole.ADMIN_ROLE));
        
        // check root user with wrong password
        request= createRequest("/foo/bar");
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();        
        
        request.addHeader("Authorization",  "Basic " + 
                new String(Base64.encodeBytes((GeoServerUser.ROOT_USERNAME+":geoserver1").getBytes())));
        getProxy().doFilter(request, response, chain);
        tmp = response.getHeader("WWW-Authenticate");
        assertNotNull(tmp);
        assert(tmp.indexOf(GeoServerSecurityManager.REALM) !=-1 );
        assert(tmp.indexOf("Basic") !=-1 );
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getErrorCode());
        ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNull(ctx);
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        
        // check disabled user, clear cache first
        getSecurityManager().getAuthenticationCache().removeAll();
        updateUser("ug1", testUserName, false);
        request= createRequest("/foo/bar");
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();        

        request.addHeader("Authorization",  "Basic " + 
                new String(Base64.encodeBytes((testUserName+":"+testPassword).getBytes())));
        getProxy().doFilter(request, response, chain);
        tmp = response.getHeader("WWW-Authenticate");
        assertNotNull(tmp);
        assert(tmp.indexOf(GeoServerSecurityManager.REALM) !=-1 );
        assert(tmp.indexOf("Basic") !=-1 );
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getErrorCode());
        ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNull(ctx);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        
        updateUser("ug1", testUserName, true);
        
        // Test anonymous
        insertAnonymousFilter();
        request= createRequest("/foo/bar");
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();                        
        getProxy().doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
        // Anonymous context is not stored in http session, no further testing
        removeAnonymousFilter();

    }
    
    @Test
    public void testJ2eeProxy() throws Exception{

        J2eeAuthenticationFilterConfig config = new J2eeAuthenticationFilterConfig();        
        config.setClassName(GeoServerJ2eeAuthenticationFilter.class.getName());        
        config.setName(testFilterName3);
        config.setRoleServiceName("rs1");        
        getSecurityManager().saveFilter(config);
        
        prepareFilterChain(pattern,                
            testFilterName3);
        
        modifyChain(pattern, false, true,null);


        SecurityContextHolder.getContext().setAuthentication(null);
        
        // Test entry point                
        MockHttpServletRequest request= createRequest("/foo/bar");
        MockHttpServletResponse response= new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();                
        
        getProxy().doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_FORBIDDEN,response.getErrorCode());
        SecurityContext ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNull(ctx);
        assertNull(SecurityContextHolder.getContext().getAuthentication());


        // test preauthenticated with dedicated role service        
        request= createRequest("/foo/bar");
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();                
        request.setUserPrincipal(new Principal() {            
            @Override
            public String getName() {
                return testUserName;
            }
        });
        request.setUserInRole(derivedRole,true);
        request.setUserInRole(rootRole,false);
        getProxy().doFilter(request, response, chain);
        
        assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
        ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNotNull(ctx);
        Authentication auth = ctx.getAuthentication();
        assertNotNull(auth);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        checkForAuthenticatedRole(auth);
        assertEquals(testUserName, auth.getPrincipal());
        assertTrue(auth.getAuthorities().contains(new GeoServerRole(rootRole)));
        assertTrue(auth.getAuthorities().contains(new GeoServerRole(derivedRole)));
        
        // test root                
        request= createRequest("/foo/bar");
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();                
        request.setUserPrincipal(new Principal() {            
            @Override
            public String getName() {
                return GeoServerUser.ROOT_USERNAME;
            }
        });
        getProxy().doFilter(request, response, chain);
        
        assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
        ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNotNull(ctx);
        auth = ctx.getAuthentication();
        assertNotNull(auth);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        //checkForAuthenticatedRole(auth);
        assertEquals(GeoServerUser.ROOT_USERNAME, auth.getPrincipal());
        assertTrue(auth.getAuthorities().size()==1);
        assertTrue(auth.getAuthorities().contains(GeoServerRole.ADMIN_ROLE));

        config.setRoleServiceName(null);
        getSecurityManager().saveFilter(config);
        
        // test preauthenticated with active role service                
        request= createRequest("/foo/bar");
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();                
        request.setUserPrincipal(new Principal() {            
            @Override
            public String getName() {
                return testUserName;
            }
        });
        request.setUserInRole(derivedRole,true);
        request.setUserInRole(rootRole,false);
        getProxy().doFilter(request, response, chain);
        
        assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
        ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNotNull(ctx);
        auth=ctx.getAuthentication();
        assertNotNull(auth);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        checkForAuthenticatedRole(auth);
        assertEquals(testUserName, auth.getPrincipal());
        assertTrue(auth.getAuthorities().contains(new GeoServerRole(rootRole)));
        assertTrue(auth.getAuthorities().contains(new GeoServerRole(derivedRole)));
        
        // Test anonymous
        insertAnonymousFilter();
        request= createRequest("/foo/bar");
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();                        
        getProxy().doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
        // Anonymous context is not stored in http session, no further testing
        removeAnonymousFilter();

                
    }
    
    @Test
    public void testRequestHeaderProxy() throws Exception{

        RequestHeaderAuthenticationFilterConfig config = 
                new RequestHeaderAuthenticationFilterConfig();        
        config.setClassName(GeoServerRequestHeaderAuthenticationFilter.class.getName());        
        config.setName(testFilterName4);
        config.setRoleServiceName("rs1");
        config.setPrincipalHeaderAttribute("principal");
        config.setRoleSource(RoleSource.RoleService);
        config.setUserGroupServiceName("ug1");
        config.setPrincipalHeaderAttribute("principal");
        config.setRolesHeaderAttribute("roles");;
        getSecurityManager().saveFilter(config);
        
        prepareFilterChain(pattern,            
            testFilterName4);
        
        modifyChain(pattern, false, true,null);


        SecurityContextHolder.getContext().setAuthentication(null);
        
        // Test entry point                
        MockHttpServletRequest request= createRequest("/foo/bar");
        MockHttpServletResponse response= new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();                
        
        getProxy().doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_FORBIDDEN,response.getErrorCode());
        SecurityContext ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNull(ctx);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        
        
        for (RoleSource rs : RoleSource.values()) {
            config.setRoleSource(rs);
            getSecurityManager().saveFilter(config);
            request= createRequest("/foo/bar");
            response= new MockHttpServletResponse();
            chain = new MockFilterChain();            
            request.setHeader("principal", testUserName);
            if (rs==RoleSource.Header) {
                request.setHeader("roles", derivedRole+";"+rootRole);
            }
            getProxy().doFilter(request, response, chain);            
            assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
            ctx = (SecurityContext)request.getSession(true).getAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
            assertNotNull(ctx);
            Authentication auth = ctx.getAuthentication();
            assertNotNull(auth);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
            checkForAuthenticatedRole(auth);
            assertEquals(testUserName, auth.getPrincipal());
            assertTrue(auth.getAuthorities().contains(new GeoServerRole(rootRole)));
            assertTrue(auth.getAuthorities().contains(new GeoServerRole(derivedRole)));        
        }

        // unknown user
        for (RoleSource rs : RoleSource.values()) {
            config.setRoleSource(rs);
            getSecurityManager().saveFilter(config);

            config.setRoleSource(rs);
            request= createRequest("/foo/bar");
            response= new MockHttpServletResponse();
            chain = new MockFilterChain();            
            request.setHeader("principal", "unknwon");
            getProxy().doFilter(request, response, chain);            
            assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
            ctx = (SecurityContext)request.getSession(true).getAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
            assertNotNull(ctx);
            Authentication auth = ctx.getAuthentication();
            assertNotNull(auth);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
            checkForAuthenticatedRole(auth);
            assertEquals("unknwon", auth.getPrincipal());
        }

        // test disabled user
        updateUser("ug1", testUserName, false);
        config.setRoleSource(RoleSource.UserGroupService);
        getSecurityManager().saveFilter(config);
        request= createRequest("/foo/bar");
        request.setHeader("principal", testUserName);
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();            
        getProxy().doFilter(request, response, chain);            
        assertEquals(HttpServletResponse.SC_FORBIDDEN,response.getErrorCode());
        ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNull(ctx);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        
        // Test anonymous
        insertAnonymousFilter();
        request= createRequest("/foo/bar");
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();                        
        getProxy().doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
        // Anonymous context is not stored in http session, no further testing
        removeAnonymousFilter();

                
    }        


    @Test
    public void testDigestAuth() throws Exception{

        DigestAuthenticationFilterConfig config = new DigestAuthenticationFilterConfig();
        config.setClassName(GeoServerDigestAuthenticationFilter.class.getName());
        config.setName(testFilterName2);
        config.setUserGroupServiceName("ug1");
        
        getSecurityManager().saveFilter(config);
        prepareFilterChain(pattern,                    
                testFilterName2);
        modifyChain(pattern, false, true,null);

        SecurityContextHolder.getContext().setAuthentication(null);
            
        // Test entry point                
        MockHttpServletRequest request= createRequest("/foo/bar");
        MockHttpServletResponse response= new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();                
            
        getProxy().doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED,response.getErrorCode());
        String tmp = response.getHeader("WWW-Authenticate");
        assertNotNull(tmp);
        assert(tmp.indexOf(GeoServerSecurityManager.REALM) !=-1 );
        assert(tmp.indexOf("Digest") !=-1 );
        SecurityContext ctx = (SecurityContext)request.getSession(true).getAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNull(ctx);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        
        
        // test successful login
        request= createRequest("/foo/bar");
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();        

        String headerValue=clientDigestString(tmp, testUserName, testPassword, request.getMethod());
        request.addHeader("Authorization",  headerValue);
        getProxy().doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
        ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNotNull(ctx);
        Authentication auth = ctx.getAuthentication();
        assertNotNull(auth);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        checkForAuthenticatedRole(auth);
        assertEquals(testUserName, ((UserDetails) auth.getPrincipal()).getUsername());
        assertTrue(auth.getAuthorities().contains(new GeoServerRole(rootRole)));
        assertTrue(auth.getAuthorities().contains(new GeoServerRole(derivedRole)));
        
        
        // check wrong password
        request= createRequest("/foo/bar");
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();

        headerValue=clientDigestString(tmp, testUserName, "wrongpass", request.getMethod());
        request.addHeader("Authorization",  headerValue);        
        getProxy().doFilter(request, response, chain);
        tmp = response.getHeader("WWW-Authenticate");
        assertNotNull(tmp);
        assert(tmp.indexOf(GeoServerSecurityManager.REALM) !=-1 );
        assert(tmp.indexOf("Digest") !=-1 );
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getErrorCode());
        ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNull(ctx);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        
        // check unknown user
        request= createRequest("/foo/bar");
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();

        headerValue=clientDigestString(tmp, "unknown", testPassword, request.getMethod());
        request.addHeader("Authorization",  headerValue);        
        getProxy().doFilter(request, response, chain);
        tmp = response.getHeader("WWW-Authenticate");
        assertNotNull(tmp);
        assert(tmp.indexOf(GeoServerSecurityManager.REALM) !=-1 );
        assert(tmp.indexOf("Digest") !=-1 );
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getErrorCode());
        ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNull(ctx);
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        // check root user
        request= createRequest("/foo/bar");
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();        
        
        headerValue=clientDigestString(tmp, GeoServerUser.ROOT_USERNAME, getMasterPassword(), request.getMethod());
        request.addHeader("Authorization",  headerValue);        
        getProxy().doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
        ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        auth = ctx.getAuthentication();
        assertNotNull(auth);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        //checkForAuthenticatedRole(auth);
        assertEquals(GeoServerUser.ROOT_USERNAME, ((UserDetails) auth.getPrincipal()).getUsername());
        assertTrue(auth.getAuthorities().size()==1);
        assertTrue(auth.getAuthorities().contains(GeoServerRole.ADMIN_ROLE));
        
        // check root user with wrong password
        request= createRequest("/foo/bar");
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();        
        
        headerValue=clientDigestString(tmp, GeoServerUser.ROOT_USERNAME, "geoserver1", request.getMethod());
        request.addHeader("Authorization",  headerValue);        
        getProxy().doFilter(request, response, chain);
        tmp = response.getHeader("WWW-Authenticate");
        assertNotNull(tmp);
        assert(tmp.indexOf(GeoServerSecurityManager.REALM) !=-1 );
        assert(tmp.indexOf("Digest") !=-1 );
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getErrorCode());
        ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNull(ctx);
        assertNull(SecurityContextHolder.getContext().getAuthentication());


        
        // check disabled user
        updateUser("ug1", testUserName, false);
        request= createRequest("/foo/bar");
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();        

        headerValue=clientDigestString(tmp, "unknown", testPassword, request.getMethod());
        request.addHeader("Authorization",  headerValue);        
        getProxy().doFilter(request, response, chain);
        tmp = response.getHeader("WWW-Authenticate");
        assertNotNull(tmp);
        assert(tmp.indexOf(GeoServerSecurityManager.REALM) !=-1 );
        assert(tmp.indexOf("Digest") !=-1 );
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getErrorCode());
        ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNull(ctx);
        assertNull(SecurityContextHolder.getContext().getAuthentication());        
        updateUser("ug1", testUserName, true);        


        // Test anonymous
        insertAnonymousFilter();
        request= createRequest("/foo/bar");
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();                        
        getProxy().doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
        // Anonymous context is not stored in http session, no further testing
        removeAnonymousFilter();
    }

    @Test
    public void testBasicAuthWithRememberMe() throws Exception{
    
        BasicAuthenticationFilterConfig config = new BasicAuthenticationFilterConfig();
        config.setClassName(GeoServerBasicAuthenticationFilter.class.getName());
        config.setUseRememberMe(true);
        config.setName(testFilterName5);
        
        getSecurityManager().saveFilter(config);
        prepareFilterChain(pattern,                
            testFilterName5,
            GeoServerSecurityFilterChain.REMEMBER_ME_FILTER);
        
        modifyChain(pattern, false, true,null);
    
    
        SecurityContextHolder.getContext().setAuthentication(null);
        
        // Test entry point                
        MockHttpServletRequest request= createRequest("/foo/bar");
        request.setupAddParameter("_spring_security_remember_me", "yes");
        MockHttpServletResponse response= new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();        
                
        getProxy().doFilter(request, response, chain);
        assertEquals(0, response.getCookies().size());
        String tmp = response.getHeader("WWW-Authenticate");
        assertNotNull(tmp);
    
        
        // check success
        request= createRequest("/foo/bar");
        request.setupAddParameter("_spring_security_remember_me", "yes");
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();        
    
                
        request.addHeader("Authorization",  "Basic " + 
                new String(Base64.encodeBytes(("abc@xyz.com:abc").getBytes())));
        getProxy().doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
        SecurityContext ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNotNull(ctx);
        Authentication auth = ctx.getAuthentication();
        assertNotNull(auth);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        checkForAuthenticatedRole(auth);
        assertEquals(1,response.getCookies().size());
        Cookie cookie = (Cookie) response.getCookies().get(0);

        request= createRequest("/foo/bar");
        request.setupAddParameter("_spring_security_remember_me", "yes");
        request.addCookie(cookie);
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();
        getProxy().doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
        ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNotNull(ctx);
        auth = ctx.getAuthentication();
        assertNotNull(auth);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        checkForAuthenticatedRole(auth);
        assertEquals("abc@xyz.com", ((UserDetails) auth.getPrincipal()).getUsername());
//        assertTrue(auth.getAuthorities().contains(new GeoServerRole(rootRole)));
//        assertTrue(auth.getAuthorities().contains(new GeoServerRole(derivedRole)));

        // send cookie + auth header
        request= createRequest("/foo/bar");
        request.setupAddParameter("_spring_security_remember_me", "yes");
        request.addCookie(cookie);
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();
        request.addHeader("Authorization",  "Basic " + 
                new String(Base64.encodeBytes(("abc@xyz.com:abc").getBytes())));
        getProxy().doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
        ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNotNull(ctx);
        auth = ctx.getAuthentication();
        assertNotNull(auth);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        checkForAuthenticatedRole(auth);
        assertEquals("abc@xyz.com", ((UserDetails) auth.getPrincipal()).getUsername());

        // check no remember me for root user
        request= createRequest("/foo/bar");
        request.setupAddParameter("_spring_security_remember_me", "yes");
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();        
    
                
        request.addHeader("Authorization",  "Basic " + 
                new String(Base64.encodeBytes((GeoServerUser.ROOT_USERNAME+":"+getMasterPassword()).getBytes())));
        getProxy().doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
        ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNotNull(ctx);
        auth = ctx.getAuthentication();
        assertNotNull(auth);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        //checkForAuthenticatedRole(auth);
        // no cookie for root user
        assertEquals(0,response.getCookies().size());
        
        // check disabled user
        updateUser("ug1", "abc@xyz.com", false);
        
        request= createRequest("/foo/bar");
        request.setupAddParameter("_spring_security_remember_me", "yes");
        request.addCookie(cookie);
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();
        getProxy().doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getErrorCode());
        // check for cancel cookie
        assertEquals(1,response.getCookies().size());
        Cookie cancelCookie = (Cookie) response.getCookies().get(0);
        assertNull(cancelCookie.getValue());
        updateUser("ug1", "abc@xyz.com", true);

        
    }

    @Test
    public void testFormLogin() throws Exception {
            
            
        UsernamePasswordAuthenticationFilterConfig config = new UsernamePasswordAuthenticationFilterConfig();
        config.setClassName(GeoServerUserNamePasswordAuthenticationFilter.class.getName());
        config.setUsernameParameterName("username");
        config.setPasswordParameterName("password");
        config.setName(testFilterName6);
        getSecurityManager().saveFilter(config);
        
//        LogoutFilterConfig loConfig = new LogoutFilterConfig();
//        loConfig.setClassName(GeoServerLogoutFilter.class.getName());
//        loConfig.setName(testFilterName9);
//        getSecurityManager().saveFilter(loConfig);
        
        prepareFilterChain(pattern,
            GeoServerSecurityFilterChain.FORM_LOGIN_FILTER);
        
        modifyChain(pattern, false, true,null);
        

        
        prepareFilterChain(ConstantFilterChain.class,"/j_spring_security_check_foo/",                    
                testFilterName6);
        modifyChain("/j_spring_security_check_foo/", false, true,null);
        
//        prepareFilterChain(LogoutFilterChain.class,"/j_spring_security_logout_foo",
//                GeoServerSecurityFilterChain.SECURITY_CONTEXT_ASC_FILTER,    
//                testFilterName9);
        
        SecurityContextHolder.getContext().setAuthentication(null);
        
        
        // Test entry point                
        MockHttpServletRequest request= createRequest("/foo/bar");
        MockHttpServletResponse response= new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();        
        
        
        getProxy().doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
        assertTrue(response.wasRedirectSent());
        String tmp = response.getHeader("Location");
        assertTrue(tmp.endsWith(GeoServerUserNamePasswordAuthenticationFilter.URL_LOGIN_FORM));
        SecurityContext ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNull(ctx);
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        
        
        // check success
        request= createRequest("/j_spring_security_check_foo");
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();
        request.setMethod("POST");
        request.setupAddParameter(config.getUsernameParameterName(), testUserName);
        request.setupAddParameter(config.getPasswordParameterName(), testPassword);
        getProxy().doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
        assertTrue(response.wasRedirectSent());
        assertTrue(response.getHeader("Location").endsWith(GeoServerUserNamePasswordAuthenticationFilter.URL_LOGIN_SUCCCESS));
        ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNotNull(ctx);
        Authentication auth = ctx.getAuthentication();
        assertNotNull(auth);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        checkForAuthenticatedRole(auth);
        assertEquals(testUserName, ((UserDetails) auth.getPrincipal()).getUsername());
        assertTrue(auth.getAuthorities().contains(new GeoServerRole(rootRole)));
        assertTrue(auth.getAuthorities().contains(new GeoServerRole(derivedRole)));

        // Test logout                
        
        GeoServerLogoutFilter logoutFilter= (GeoServerLogoutFilter) getSecurityManager().loadFilter(GeoServerSecurityFilterChain.FORM_LOGOUT_FILTER);
        request= createRequest("/j_spring_security_logout_foo");
        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx);
        SecurityContextHolder.getContext().setAuthentication(auth);

        response= new MockHttpServletResponse();
        chain = new MockFilterChain();             
        //getProxy().doFilter(request, response, chain);
        logoutFilter.doFilter(request, response,chain);
        assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
        assertTrue(response.wasRedirectSent());
        tmp = response.getHeader("Location");
        assertNotNull(tmp);
        assertTrue(tmp.endsWith(GeoServerLogoutFilter.URL_AFTER_LOGOUT));
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        
        
        // test invalid password
        request= createRequest("/j_spring_security_check_foo");
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();
        request.setMethod("POST");
        request.setupAddParameter(config.getUsernameParameterName(), testUserName);
        request.setupAddParameter(config.getPasswordParameterName(), "wrongpass");
        getProxy().doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
        assertTrue(response.wasRedirectSent());
        assertTrue(response.getHeader("Location").endsWith(GeoServerUserNamePasswordAuthenticationFilter.URL_LOGIN_FAILURE));

        ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNull(ctx);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        
        // check unknown user
        request= createRequest("/j_spring_security_check_foo");
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();
        request.setMethod("POST");
        request.setupAddParameter(config.getUsernameParameterName(), "unknwon");
        request.setupAddParameter(config.getPasswordParameterName(), testPassword);
        getProxy().doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
        assertTrue(response.wasRedirectSent());
        assertTrue(response.getHeader("Location").endsWith(GeoServerUserNamePasswordAuthenticationFilter.URL_LOGIN_FAILURE));
        
        ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNull(ctx);
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        // check root user
        request= createRequest("/j_spring_security_check_foo");
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();
        request.setMethod("POST");
        request.setupAddParameter(config.getUsernameParameterName(), GeoServerUser.ROOT_USERNAME);
        request.setupAddParameter(config.getPasswordParameterName(), getMasterPassword());
        getProxy().doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
        assertTrue(response.wasRedirectSent());
        assertTrue(response.getHeader("Location").endsWith(GeoServerUserNamePasswordAuthenticationFilter.URL_LOGIN_SUCCCESS));
        ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        auth = ctx.getAuthentication();
        assertNotNull(auth);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        //checkForAuthenticatedRole(auth);
        assertEquals(GeoServerUser.ROOT_USERNAME, auth.getPrincipal());
        assertTrue(auth.getAuthorities().size()==1);
        assertTrue(auth.getAuthorities().contains(GeoServerRole.ADMIN_ROLE));
        
        // check root user with wrong password
        request= createRequest("/j_spring_security_check_foo");
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();
        request.setMethod("POST");
        request.setupAddParameter(config.getUsernameParameterName(), GeoServerUser.ROOT_USERNAME);
        request.setupAddParameter(config.getPasswordParameterName(), "geoserver1");
        getProxy().doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
        assertTrue(response.wasRedirectSent());
        assertTrue(response.getHeader("Location").endsWith(GeoServerUserNamePasswordAuthenticationFilter.URL_LOGIN_FAILURE));
        ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNull(ctx);
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        
        // check disabled user
        updateUser("ug1", testUserName, false);
        request= createRequest("/j_spring_security_check_foo");
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();
        request.setMethod("POST");
        request.setupAddParameter(config.getUsernameParameterName(), testUserName);
        request.setupAddParameter(config.getPasswordParameterName(), testPassword);
        getProxy().doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
        assertTrue(response.wasRedirectSent());
        assertTrue(response.getHeader("Location").endsWith(GeoServerUserNamePasswordAuthenticationFilter.URL_LOGIN_FAILURE));
        ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNull(ctx);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        
        updateUser("ug1", testUserName, true);
        
        // Test anonymous
        insertAnonymousFilter();
        request= createRequest("foo/bar");
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();
        getProxy().doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
        // Anonymous context is not stored in http session, no further testing
        removeAnonymousFilter();

        

    }

    
    @Test
    public void testFormLoginWithRememberMe() throws Exception{
        
        
        UsernamePasswordAuthenticationFilterConfig config = new UsernamePasswordAuthenticationFilterConfig();
        config.setClassName(GeoServerUserNamePasswordAuthenticationFilter.class.getName());
        config.setUsernameParameterName("username");
        config.setPasswordParameterName("password");
        config.setName(testFilterName7);
        getSecurityManager().saveFilter(config);
        
//        LogoutFilterConfig loConfig = new LogoutFilterConfig();
//        loConfig.setClassName(GeoServerLogoutFilter.class.getName());
//        loConfig.setName(testFilterName9);
//        getSecurityManager().saveFilter(loConfig);
        
        prepareFilterChain(pattern,            
            GeoServerSecurityFilterChain.REMEMBER_ME_FILTER,
            GeoServerSecurityFilterChain.FORM_LOGIN_FILTER);
        modifyChain(pattern, false, true,null);
                
        
        prepareFilterChain("/j_spring_security_check_foo/",                    
                testFilterName7);
        modifyChain("/j_spring_security_check_foo/", false, true,null);
                
        SecurityContextHolder.getContext().setAuthentication(null);

        

        // Test entry point                
        MockHttpServletRequest request= createRequest("/foo/bar");
        request.setupAddParameter("_spring_security_remember_me", "yes");
        MockHttpServletResponse response= new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();        
                
        getProxy().doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
        assertTrue(response.wasRedirectSent());
        String tmp = response.getHeader("Location");
        assertTrue(tmp.endsWith(GeoServerUserNamePasswordAuthenticationFilter.URL_LOGIN_FORM));
        SecurityContext ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNull(ctx);
        assertNull(SecurityContextHolder.getContext().getAuthentication());                    

        //check success
        request= createRequest("/j_spring_security_check_foo");
        request.setupAddParameter("_spring_security_remember_me", "yes");
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();
        request.setMethod("POST");
        request.setupAddParameter(config.getUsernameParameterName(), testUserName);
        request.setupAddParameter(config.getPasswordParameterName(), testPassword);
        getProxy().doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
        assertTrue(response.wasRedirectSent());
        assertTrue(response.getHeader("Location").endsWith(GeoServerUserNamePasswordAuthenticationFilter.URL_LOGIN_SUCCCESS));
        HttpSession session = request.getSession(true);
        ctx = (SecurityContext)session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNotNull(ctx);
        Authentication auth = ctx.getAuthentication();
        assertNotNull(auth);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        checkForAuthenticatedRole(auth);
        assertEquals(testUserName, ((UserDetails) auth.getPrincipal()).getUsername());
        assertTrue(auth.getAuthorities().contains(new GeoServerRole(rootRole)));
        assertTrue(auth.getAuthorities().contains(new GeoServerRole(derivedRole)));
        assertEquals(1,response.getCookies().size());
        Cookie cookie = (Cookie) response.getCookies().get(0);
        assertNotNull(cookie.getValue());
        
          
        // check logout
        GeoServerLogoutFilter logoutFilter= (GeoServerLogoutFilter) getSecurityManager().loadFilter(GeoServerSecurityFilterChain.FORM_LOGOUT_FILTER);
        request= createRequest("/j_spring_security_logout_foo");
        session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, ctx);
        SecurityContextHolder.getContext().setAuthentication(auth);        
        response= new MockHttpServletResponse();        
        chain = new MockFilterChain();        
        
        //getProxy().doFilter(request, response, chain);
        logoutFilter.doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
        assertTrue(response.wasRedirectSent());
        tmp = response.getHeader("Location");
        assertNotNull(tmp);
        assertTrue(tmp.endsWith(GeoServerLogoutFilter.URL_AFTER_LOGOUT));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        Cookie cancelCookie = (Cookie) response.getCookies().get(0);
        assertNull(cancelCookie.getValue());


        // check no remember me for root user
        request= createRequest("/j_spring_security_check_foo");
        request.setupAddParameter("_spring_security_remember_me", "yes");
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();
        request.setMethod("POST");
        request.setupAddParameter(config.getUsernameParameterName(), GeoServerUser.ROOT_USERNAME);
        request.setupAddParameter(config.getPasswordParameterName(), getMasterPassword());
        getProxy().doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
        assertTrue(response.wasRedirectSent());
        assertTrue(response.getHeader("Location").endsWith(GeoServerUserNamePasswordAuthenticationFilter.URL_LOGIN_SUCCCESS));
        ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNotNull(ctx);
        auth = ctx.getAuthentication();
        assertNotNull(auth);
        //checkForAuthenticatedRole(auth);
        assertEquals(GeoServerUser.ROOT_USERNAME,auth.getPrincipal());
        assertEquals(0,response.getCookies().size());
        
        // check disabled user
        updateUser("ug1", testUserName, false);
        
        request= createRequest("/foo/bar");
        request.addCookie(cookie);        
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();
        getProxy().doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
        assertTrue(response.wasRedirectSent());
        tmp = response.getHeader("Location");
        assertTrue(tmp.endsWith(GeoServerUserNamePasswordAuthenticationFilter.URL_LOGIN_FORM));
        // check for cancel cookie
        assertEquals(1,response.getCookies().size());
        cancelCookie = (Cookie) response.getCookies().get(0);
        assertNull(cancelCookie.getValue());
        updateUser("ug1", testUserName, true);
       
        

    }

    @Test
    public void testX509Auth() throws Exception{

        X509CertificateAuthenticationFilterConfig config = 
                new X509CertificateAuthenticationFilterConfig();        
        config.setClassName(GeoServerX509CertificateAuthenticationFilter.class.getName());        
        config.setName(testFilterName8);
        config.setRoleServiceName("rs1");
        config.setRoleSource(org.geoserver.security.config.X509CertificateAuthenticationFilterConfig.RoleSource.RoleService);
        config.setUserGroupServiceName("ug1");        
        config.setRolesHeaderAttribute("roles");
        getSecurityManager().saveFilter(config);
        
        prepareFilterChain(pattern,
            testFilterName8);
        
        modifyChain(pattern, false, true,null);


        SecurityContextHolder.getContext().setAuthentication(null);
        
        // Test entry point                
        MockHttpServletRequest request= createRequest("/foo/bar");
        MockHttpServletResponse response= new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();                
        
        getProxy().doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_FORBIDDEN,response.getErrorCode());
        SecurityContext ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNull(ctx);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        
        
        for (org.geoserver.security.config.X509CertificateAuthenticationFilterConfig.RoleSource rs : 
            org.geoserver.security.config.X509CertificateAuthenticationFilterConfig.RoleSource.values()) {
            config.setRoleSource(rs);
            getSecurityManager().saveFilter(config);
            request= createRequest("/foo/bar");
            response= new MockHttpServletResponse();
            chain = new MockFilterChain();
            if (rs==RoleSource.Header) {
                request.setHeader("roles", derivedRole+";"+rootRole);
            }

            setCertifacteForUser(testUserName, request);                        
            getProxy().doFilter(request, response, chain);            
            assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
            ctx = (SecurityContext)request.getSession(true).getAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
            assertNotNull(ctx);
            Authentication auth = ctx.getAuthentication();
            assertNotNull(auth);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
            checkForAuthenticatedRole(auth);
            assertEquals(testUserName, auth.getPrincipal());
            assertTrue(auth.getAuthorities().contains(new GeoServerRole(rootRole)));
            assertTrue(auth.getAuthorities().contains(new GeoServerRole(derivedRole)));        
        }

        // unknown user
        for (org.geoserver.security.config.X509CertificateAuthenticationFilterConfig.RoleSource rs : 
            org.geoserver.security.config.X509CertificateAuthenticationFilterConfig.RoleSource.values()) {
            config.setRoleSource(rs);
            getSecurityManager().saveFilter(config);

            config.setRoleSource(rs);
            request= createRequest("/foo/bar");
            response= new MockHttpServletResponse();
            chain = new MockFilterChain();
            //TODO
            setCertifacteForUser("unknown", request);
            getProxy().doFilter(request, response, chain);            
            assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
            ctx = (SecurityContext)request.getSession(true).getAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
            assertNotNull(ctx);
            Authentication auth = ctx.getAuthentication();
            assertNotNull(auth);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
            checkForAuthenticatedRole(auth);
            assertEquals("unknown", auth.getPrincipal());
        }

        // test disabled user
        updateUser("ug1", testUserName, false);
        config.setRoleSource(org.geoserver.security.config.X509CertificateAuthenticationFilterConfig.RoleSource.UserGroupService);
        getSecurityManager().saveFilter(config);
        request= createRequest("/foo/bar");
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();
        setCertifacteForUser(testUserName, request);
        getProxy().doFilter(request, response, chain);            
        assertEquals(HttpServletResponse.SC_FORBIDDEN,response.getErrorCode());
        ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNull(ctx);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        
        // Test anonymous
        insertAnonymousFilter();
        request= createRequest("/foo/bar");
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();                        
        getProxy().doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
        // Anonymous context is not stored in http session, no further testing
        removeAnonymousFilter();

                
    }      
    
    @Test
    @RunTestSetup
    public void testCascadingFilters() throws Exception{

//        BasicAuthenticationFilterConfig bconfig = new BasicAuthenticationFilterConfig();
//        bconfig.setClassName(GeoServerBasicAuthenticationFilter.class.getName());
//        bconfig.setUseRememberMe(false);
//        bconfig.setName(testFilterName);
//        getSecurityManager().saveFilter(bconfig);

        
        DigestAuthenticationFilterConfig config = new DigestAuthenticationFilterConfig();
        config.setClassName(GeoServerDigestAuthenticationFilter.class.getName());
        config.setName(testFilterName2);
        config.setUserGroupServiceName("ug1");
        
        getSecurityManager().saveFilter(config);
        prepareFilterChain(pattern,
                testFilterName,
                testFilterName2);
        
        modifyChain(pattern, false, true,null);

        SecurityContextHolder.getContext().setAuthentication(null);
            
        // Test entry point, must be digest                
        MockHttpServletRequest request= createRequest("/foo/bar");
        MockHttpServletResponse response= new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();                
            
        getProxy().doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_UNAUTHORIZED,response.getErrorCode());
        String tmp = response.getHeader("WWW-Authenticate");
        assertNotNull(tmp);
        assert(tmp.indexOf(GeoServerSecurityManager.REALM) !=-1 );
        assert(tmp.indexOf("Digest") !=-1 );
        SecurityContext ctx = (SecurityContext)request.getSession(true).getAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNull(ctx);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        
        
        // test successful login for digest
        request= createRequest("/foo/bar");
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();        

        String headerValue=clientDigestString(tmp, testUserName, testPassword, request.getMethod());
        request.addHeader("Authorization",  headerValue);
        getProxy().doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
        ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNotNull(ctx);
        Authentication auth = ctx.getAuthentication();
        assertNotNull(auth);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        checkForAuthenticatedRole(auth);
        assertEquals(testUserName, ((UserDetails) auth.getPrincipal()).getUsername());
        assertTrue(auth.getAuthorities().contains(new GeoServerRole(rootRole)));
        assertTrue(auth.getAuthorities().contains(new GeoServerRole(derivedRole)));
        
        // check success for basic authentication
        request= createRequest("/foo/bar");
        response= new MockHttpServletResponse();
        chain = new MockFilterChain();        

        request.addHeader("Authorization",  "Basic " + 
                new String(Base64.encodeBytes((testUserName+":"+testPassword).getBytes())));
        getProxy().doFilter(request, response, chain);
        assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());
        ctx = (SecurityContext)request.getSession(true).getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);        
        assertNotNull(ctx);
        auth = ctx.getAuthentication();
        assertNotNull(auth);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        checkForAuthenticatedRole(auth);
        assertEquals(testUserName, ((UserDetails) auth.getPrincipal()).getUsername());
        assertTrue(auth.getAuthorities().contains(new GeoServerRole(rootRole)));
        assertTrue(auth.getAuthorities().contains(new GeoServerRole(derivedRole)));

    }
 
    //@Test disabled, builds locally but not onmaster
    public void testSSL() throws Exception {
        
        
        prepareFilterChain(pattern,GeoServerSecurityFilterChain.ANONYMOUS_FILTER);
        modifyChain(pattern, false, true, null);
        
        SecurityManagerConfig secConfig = getSecurityManager().getSecurityConfig();        
        RequestFilterChain chain = secConfig.getFilterChain().getRequestChainByName("testChain");
        chain.setRequireSSL(true);        
        getSecurityManager().saveSecurityConfig(secConfig);
        
        MockHttpServletRequest request= createRequest("/foo/bar?request=getCapabilities&a=b");
        request.setProtocol("https");
        MockHttpServletResponse response= new MockHttpServletResponse();
        
        MockFilterChain authchain = new MockFilterChain();                            
        getProxy().doFilter(request, response, authchain);
        assertEquals(HttpServletResponse.SC_OK, response.getErrorCode());

        request= createRequest("/foo/bar?request=getCapabilities&a=b");        
        response= new MockHttpServletResponse();
        
        authchain = new MockFilterChain();                            
        getProxy().doFilter(request, response, authchain);
        assertTrue(response.wasRedirectSent());
        String urlString = response.getHeader("Location");
        assertNotNull(urlString);
        assertTrue(urlString.startsWith("https"));
        assertTrue(urlString.indexOf("a=b")!=-1);
        assertTrue(urlString.indexOf("443")!=-1);

        chain.setRequireSSL(false);
        getSecurityManager().saveSecurityConfig(secConfig);
        

    }
}
