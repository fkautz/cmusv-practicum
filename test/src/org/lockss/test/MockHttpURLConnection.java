/*
 * $Id$
 */

/*

Copyright (c) 2000-2002 Board of Trustees of Leland Stanford Jr. University,
all rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
STANFORD UNIVERSITY BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

Except as contained in this notice, the name of Stanford University shall not
be used in advertising or otherwise to promote the sale, use or other dealings
in this Software without prior written authorization from Stanford University.

*/

package org.lockss.test;

import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.io.InputStream;
import java.io.IOException;
import java.security.Permission;

public class MockHttpURLConnection extends HttpURLConnection{
    private int responseCode = -1;

    public void setResponseCode(int responseCode){
	this.responseCode = responseCode;
    }


    //Methods defined in HttpURLConnection
    protected MockHttpURLConnection(){
	super(null);
    }

    public static void setFollowRedirects(boolean set) {
      throw new UnsupportedOperationException("Not Implemented");
    }

    public static boolean getFollowRedirects() {
      throw new UnsupportedOperationException("Not Implemented");
    }

    public void setInstanceFollowRedirects(boolean followRedirects) {
      throw new UnsupportedOperationException("Not Implemented");
    }

    public boolean getInstanceFollowRedirects() {
      throw new UnsupportedOperationException("Not Implemented");
    }

    public void setRequestMethod(String method) throws ProtocolException {
      throw new UnsupportedOperationException("Not Implemented");
    }

    public String getRequestMethod() {
      throw new UnsupportedOperationException("Not Implemented");
    }
    
    public int getResponseCode() throws IOException {
	return this.responseCode;
    }

    public String getResponseMessage() throws IOException {
      throw new UnsupportedOperationException("Not Implemented");
    }

    public long getHeaderFieldDate(String name, long Default) {
      throw new UnsupportedOperationException("Not Implemented");
    }


    public void connect(){
      throw new UnsupportedOperationException("Not Implemented");
    }

    public void disconnect(){
      throw new UnsupportedOperationException("Not Implemented");
    }

    public boolean usingProxy(){
      throw new UnsupportedOperationException("Not Implemented");
    }

    public Permission getPermission() throws IOException {
      throw new UnsupportedOperationException("Not Implemented");
    }

    public InputStream getErrorStream() {
      throw new UnsupportedOperationException("Not Implemented");
    }
}
