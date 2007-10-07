/*
 * $HeadURL$
 * $Revision$
 * $Date$
 * 
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.impl.cookie;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.NameValuePair;
import org.apache.http.cookie.ClientCookie;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieAttributeHandler;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.MalformedCookieException;
import org.apache.http.cookie.SM;
import org.apache.http.message.BufferedHeader;
import org.apache.http.util.CharArrayBuffer;

/**
 * <p>RFC 2965 specific cookie management functions.</p>
 * 
 * @author jain.samit@gmail.com (Samit Jain)
 * @author <a href="mailto:oleg at ural.ru">Oleg Kalnichevski</a>
 *
 * @since 3.1
 */
public class RFC2965Spec extends RFC2109Spec {

    /** 
     * Default constructor 
     * 
     */
    public RFC2965Spec() {
        this(null, false);
    }
    
    public RFC2965Spec(final String[] datepatterns, boolean oneHeader) {
        super(datepatterns, oneHeader);
        registerAttribHandler(ClientCookie.DOMAIN_ATTR, new RFC2965DomainAttributeHandler());
        registerAttribHandler(ClientCookie.PORT_ATTR, new RFC2965PortAttributeHandler());
        registerAttribHandler(ClientCookie.COMMENTURL_ATTR, new RFC2965CommentUrlAttributeHandler());
        registerAttribHandler(ClientCookie.DISCARD_ATTR, new RFC2965DiscardAttributeHandler());
        registerAttribHandler(ClientCookie.VERSION_ATTR, new RFC2965VersionAttributeHandler());
    }
    
    private BasicClientCookie createCookie(
            final String name, final String value, final CookieOrigin origin) {
        BasicClientCookie cookie = new BasicClientCookie(name, value);
        cookie.setPath(getDefaultPath(origin));
        cookie.setDomain(getDefaultDomain(origin));
        return cookie;
    }
    
    private BasicClientCookie createCookie2(
            final String name, final String value, final CookieOrigin origin) {
        BasicClientCookie2 cookie = new BasicClientCookie2(name, value);
        cookie.setPath(getDefaultPath(origin));
        cookie.setDomain(getDefaultDomain(origin));
        cookie.setPorts(new int [] { origin.getPort() });
        return cookie;
    }
    
    public Cookie[] parse(
            final Header header, 
            CookieOrigin origin) throws MalformedCookieException {
        if (header == null) {
            throw new IllegalArgumentException("Header may not be null");
        }
        if (origin == null) {
            throw new IllegalArgumentException("Cookie origin may not be null");
        }
        
        origin = adjustEffectiveHost(origin);
        
        HeaderElement[] elems = header.getElements();

        Cookie[] cookies = new Cookie[elems.length];
        for (int i = 0; i < elems.length; i++) {
            HeaderElement headerelement = elems[i];

            String name = headerelement.getName();
            String value = headerelement.getValue();
            if (name == null || name.equals("")) {
                throw new MalformedCookieException("Cookie name may not be empty");
            }

            BasicClientCookie cookie;
            if (header.getName().equals(SM.SET_COOKIE2)) {
                cookie = createCookie2(name, value, origin);
            } else {
                cookie = createCookie(name, value, origin);
            }
            
            // cycle through the parameters
            NameValuePair[] attribs = headerelement.getParameters();
            
            // Eliminate duplicate attributes. The first occurrence takes precedence
            // See RFC2965: 3.2  Origin Server Role
            Map attribmap = new HashMap(attribs.length); 
            for (int j = attribs.length - 1; j >= 0; j--) {
                NameValuePair param = attribs[j];
                attribmap.put(param.getName().toLowerCase(), param);
            }
            for (Iterator it = attribmap.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry entry = (Map.Entry) it.next();
                NameValuePair attrib = (NameValuePair) entry.getValue();
                String s = attrib.getName().toLowerCase();
                
                cookie.setAttribute(s, attrib.getValue());
                
                CookieAttributeHandler handler = findAttribHandler(s);
                if (handler != null) {
                    handler.parse(cookie, attrib.getValue());
                }
            }
            cookies[i] = cookie;
        }
        return cookies;
    }

    public void validate(final Cookie cookie, CookieOrigin origin)
            throws MalformedCookieException {
        if (cookie == null) {
            throw new IllegalArgumentException("Cookie may not be null");
        }
        if (origin == null) {
            throw new IllegalArgumentException("Cookie origin may not be null");
        }
        origin = adjustEffectiveHost(origin);
        super.validate(cookie, origin);
    }

    public boolean match(final Cookie cookie, CookieOrigin origin) {
        if (cookie == null) {
            throw new IllegalArgumentException("Cookie may not be null");
        }
        if (origin == null) {
            throw new IllegalArgumentException("Cookie origin may not be null");
        }
        origin = adjustEffectiveHost(origin);
        return super.match(cookie, origin);
    }

    /**
     * Adds valid Port attribute value, e.g. "8000,8001,8002"
     */
    protected void formatCookieAsVer(final CharArrayBuffer buffer, 
            final Cookie cookie, int version) {
        super.formatCookieAsVer(buffer, cookie, version);
        // format port attribute
        if (cookie instanceof ClientCookie) {
            // Test if the port attribute as set by the origin server is not blank
            String s = ((ClientCookie) cookie).getAttribute(ClientCookie.PORT_ATTR);
            if (s != null) {
                buffer.append("; $Port");
                buffer.append("=\"");
                if (s.trim().length() > 0) {
                    int[] ports = cookie.getPorts();
                    if (ports != null) {
                        for (int i = 0, len = ports.length; i < len; i++) {
                            if (i > 0) {
                                buffer.append(",");
                            }
                            buffer.append(Integer.toString(ports[i]));
                        }
                    }
                }
                buffer.append("\"");
            }
        }
    }
    
    /**
     * Set 'effective host name' as defined in RFC 2965.
     * <p>
     * If a host name contains no dots, the effective host name is
     * that name with the string .local appended to it.  Otherwise
     * the effective host name is the same as the host name.  Note
     * that all effective host names contain at least one dot.
     *
     * @param host host name where cookie is received from or being sent to.
     * @return
     */
    private static CookieOrigin adjustEffectiveHost(final CookieOrigin origin) {
        String effectiveHost = origin.getHost();
        if (effectiveHost.indexOf('.') < 0) {
            effectiveHost += ".local";
            return new CookieOrigin(
                    effectiveHost, 
                    origin.getPort(), 
                    origin.getPath(), 
                    origin.isSecure());
        } else {
            return origin;
        }
    }

    public int getVersion() {
        return 1;
    }

    public Header getVersionHeader() {
        CharArrayBuffer buffer = new CharArrayBuffer(40);
        buffer.append(SM.COOKIE2);
        buffer.append(": ");
        buffer.append("$Version=");
        buffer.append(Integer.toString(getVersion()));
        return new BufferedHeader(buffer);
    }
    
}

