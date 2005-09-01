/*
 * $Id$
 */

/*

Copyright (c) 2000-2005 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.proxy.icp;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.SocketException;

import org.lockss.app.BaseLockssDaemonManager;
import org.lockss.app.ConfigurableManager;
import org.lockss.app.LockssAppException;
import org.lockss.config.Configuration;
import org.lockss.config.Configuration.Differences;
import org.lockss.daemon.ResourceManager;
import org.lockss.plugin.CachedUrl;
import org.lockss.plugin.PluginManager;
import org.lockss.proxy.ProxyManager;
import org.lockss.util.Logger;
import org.lockss.util.RateLimiter;

public class IcpManager
    extends BaseLockssDaemonManager
    implements ConfigurableManager, IcpHandler {

  private IcpBuilder icpBuilder;
  
  private IcpFactory icpFactory;
  
  private IcpSocketImpl icpSocket;
  
  private RateLimiter limiter;
  
  private PluginManager pluginManager;
  
  private int port;
  
  private ProxyManager proxyManager;
  
  private ResourceManager resourceManager;
  
  private DatagramSocket udpSocket;

  public RateLimiter getLimiter() {
    return limiter;
  }

  public void icpReceived(IcpReceiver source, IcpMessage message) {
    if (message.getOpcode() == IcpMessage.ICP_OP_QUERY) {
        IcpMessage response;
        try {
          // Prepare response
          if (!proxyManager.isIpAuthorized(message.getUdpAddress().getHostAddress())) {
            logger.debug2("Response: DENIED");
            response = icpBuilder.makeDenied(message);          
          }
          else {
            String urlString = message.getPayloadUrl();
            CachedUrl cu = pluginManager.findOneCachedUrl(urlString);
            if (cu != null && cu.hasContent()) {
              logger.debug2("Response: HIT");
              response = icpBuilder.makeHit(message);
            }
            else {
              logger.debug2("Response: MISS_NOFETCH");
              response = icpBuilder.makeMissNoFetch(message);
            }
          }
        }
        catch (IcpProtocolException ipe) {
          logger.debug2("Encountered an IcpProtocolException", ipe);
          logger.debug2("Response: ERR");
          try {
            // Last attempt to make something out of it
            response = icpBuilder.makeError(message);
          }
          catch (IcpProtocolException ipe2) {
            logger.warning(
                "Two consecutive IcpProtocolExceptions thrown; aborting", ipe2);
            return;
          }
        }
        
        // Send response
        try {
          icpSocket.send(response, message.getUdpAddress(), message.getUdpPort());
        }
        catch (IOException ioe) {
          logger.warning("IOException while sending ICP response", ioe);
        }
    }
    else {
      if (logger.isDebug3()) {
        StringBuffer buffer = new StringBuffer();
        buffer.append("Received a non-query ICP message from ");
        buffer.append(message.getUdpAddress());
        buffer.append(": ");
        buffer.append(message.toString());
        logger.debug3(buffer.toString());
      }
    }
  }
  
  public void setConfig(Configuration newConfig,
                        Configuration prevConfig,
                        Differences changedKeys) {
    
    // ICP rate limiter
    if (changedKeys.contains(PARAM_ICP_INCOMING_RATE)) {
      limiter = RateLimiter.getConfiguredRateLimiter(newConfig,
          limiter, PARAM_ICP_INCOMING_RATE, DEFAULT_ICP_INCOMING_RATE);
    }
    
    // ICP enabled/disabled and ICP port
    if (   changedKeys.contains(PARAM_ICP_ENABLED)
        || changedKeys.contains(PARAM_ICP_PORT)) {
      boolean enable = newConfig.getBoolean(PARAM_ICP_ENABLED,
                                            DEFAULT_ICP_ENABLED);
      stopSocket();
      if (enable) {
        startSocket();
      }
    }
  }

  public void startService() {
    super.startService();
    pluginManager = getDaemon().getPluginManager();
    proxyManager = getDaemon().getProxyManager();
    limiter = RateLimiter.getConfiguredRateLimiter(
        Configuration.getCurrentConfig(), limiter,
        PARAM_ICP_INCOMING_RATE, DEFAULT_ICP_INCOMING_RATE);
    boolean start = Configuration.getBooleanParam(PARAM_ICP_ENABLED,
                                                  DEFAULT_ICP_ENABLED);
    if (start) {
      startSocket();
    }
  }

  public void stopService() {
    icpSocket.requestStop();
    super.stopService();
  }
  
  private void forget() {
    icpBuilder = null; 
    icpFactory = null;
    icpSocket = null;
    resourceManager = null;
    limiter = null;
    port = -1;
  }
  
  private void startSocket() {
    if (isAppInited()) {
      try {
        logger.debug2("startSocket in IcpManager: action");
        port = Configuration.getIntParam(PARAM_ICP_PORT,
                                         DEFAULT_ICP_PORT);
        resourceManager = getDaemon().getResourceManager();
        if (!resourceManager.reserveUdpPort(port, getClass())) {
          logger.error("Could not reserve UDP port " + port);
          throw new SocketException();
        }
        
        udpSocket = new DatagramSocket(port);
        icpFactory = IcpFactoryImpl.makeIcpFactory();
        icpBuilder = icpFactory.makeIcpBuilder(); 
        icpSocket = new IcpSocketImpl("IcpSocketImpl",
                                      udpSocket,
                                      icpFactory.makeIcpEncoder(),
                                      icpFactory.makeIcpDecoder(),
                                      this);
        icpSocket.addIcpHandler(this);
        new Thread(icpSocket).start();
      }
      catch (SocketException se) {
        forget(); // revert instantions
        logger.error("Could not start ICP socket", se);
      }
    }
  }
  
  private void stopSocket() {
    if (icpSocket != null) {
      logger.debug2("stopSocket in IcpManager: action");
      icpSocket.requestStop();
      resourceManager.releaseUdpPort(port, getClass());
      forget(); // minimize footprint
    }
  }
  
  public static final boolean DEFAULT_ICP_ENABLED = false;
  
  public static final int DEFAULT_ICP_PORT = IcpMessage.ICP_PORT;

  public static final String PARAM_ICP_ENABLED =
    "org.lockss.proxy.icp.enabled";

  public static final String PARAM_ICP_PORT =
    "org.lockss.proxy.icp.port";
  
  private static final String DEFAULT_ICP_INCOMING_RATE =
    "50/1s";
  
  private static Logger logger = Logger.getLogger("IcpManager");
  
  private static final String PARAM_ICP_INCOMING_RATE =
  "org.lockss.proxy.icp.incomingRequestsPerSecond";

}
