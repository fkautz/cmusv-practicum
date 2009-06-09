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

package org.lockss.alert;

import java.util.*;
import java.text.*;

import org.lockss.app.*;
import org.lockss.config.*;
import org.lockss.mail.*;
import org.lockss.util.*;

/** An AlertAction that records the Alert by emailing it */
public class AlertActionMail extends AbstractAlertAction {
  private static Logger log = Logger.getLogger("AlertActionMail");

  /** If specified, the sender address on alert emails.  If not specified,
   * uses the admin email adress */
  static final String PARAM_EMAIL_SENDER = PREFIX + "mail.sender";
  /** Default sender, if PARAM_EMAIL_SENDER not present */
  static final String PARAM_DEFAULT_EMAIL_SENDER =
    ConfigManager.PARAM_PLATFORM_ADMIN_EMAIL;
  // Make default param name show up as default in param listing.
  static final String DEFAULT_EMAIL_SENDER =
    "(value of " + PARAM_DEFAULT_EMAIL_SENDER + ")";

  /** printf string applied to cache-name, email-sender */
  static final String PARAM_EMAIL_FROM = PREFIX + "mail.from";
  static final String DEFAULT_EMAIL_FROM = "LOCKSS box %s <%s>";

  public static final String PARAM_ENABLED = PREFIX + "mail.enabled";
  static final boolean DEFAULT_ENABLED = false;

  static DateFormat headerDf =
    new SimpleDateFormat("EEE dd MMM yyyy HH:mm:ss zzz"
					     /*, Locale.US */);
//   headerDf.setTimeZone(TimeZone.getTimeZone("GMT"));

  private String recipients;

  /** Create an action that mails to a single recipient */
  public AlertActionMail(String to) {
    this.recipients = to;
  }

  /** Create an action that mails to the recipient in the Alert.  If the
   * Alert has no ATTR_EMAIL_TO, no mail is sent */
  public AlertActionMail() {
    this.recipients = null;
  }

//   /** Create an action that mails to a list of recipients */
//   public AlertActionMail(List recipients) {
//     this(StringUtil.separatedString(recipients, ", "));
//   }

  /** Return the recipients */
  public String getRecipients(Alert alert) {
    if (recipients != null) {
      return recipients;
    }
    return alert.getString(Alert.ATTR_EMAIL_TO);
  }

  /** Set the recipients */
  public void setRecipients(String recipients) {
    this.recipients = recipients;
  }

  /** Create and send a message */
  public void record(LockssDaemon daemon, Alert alert) {
    send(daemon, alert, "", alert.getMailBody());
  }

  /** Create and send a single message for a list of alerts */
  public void record(LockssDaemon daemon, List alerts) {
    if (log.isDebug2()) {
      log.debug2("Recording " + alerts.size() + " actions");
      log.debug3(alerts.toString());
    }
    if (alerts == null || alerts.isEmpty()) return;
    Alert firstAlert = (Alert)alerts.get(0);
    Configuration config = CurrentConfig.getCurrentConfig();
    if (config.getBoolean(PARAM_ENABLED, DEFAULT_ENABLED)) {
      StringBuffer sb = new StringBuffer();
      for (Iterator iter = alerts.iterator(); iter.hasNext(); ) {
	Alert alert = (Alert)iter.next();
	sb.append(alert.getMailBody());
	if (iter.hasNext()) {
	  sb.append("\n==========================================================================\n");
	}
      }
      send(daemon, firstAlert, " (multiple)", sb.toString());
    }
  }

  private void send(LockssDaemon daemon, Alert oneAlert,
		    String subjSuff, String body) {
    Configuration config = CurrentConfig.getCurrentConfig();
    if (config.getBoolean(PARAM_ENABLED, DEFAULT_ENABLED)) {
      String recip = getRecipients(oneAlert);
      if (recipients == null) {
	log.warning("Alert has no email recipient, not sending: " + oneAlert);
      }
      MailService mailSvc = daemon.getMailService();
      TextMessage msg = new TextMessage();
      msg.addHeader("From", getFrom(oneAlert, config));
      msg.addHeader("To", recip);
      msg.addHeader("Date", headerDf.format(TimeBase.nowDate()));
      msg.addHeader("Subject", oneAlert.getMailSubject() + subjSuff);
      msg.addHeader("X-Mailer", getXMailer());
      msg.setText(body);
      mailSvc.sendMail(getSender(config), recip, msg);
    }
  }

  String getXMailer() {
    String release = BuildInfo.getBuildProperty(BuildInfo.BUILD_RELEASENAME);
    if (release != null) {
      return "LOCKSS daemon " + release;
    }
    return "LOCKSS daemon";
  }

  public boolean isGroupable() {
    return true;
  }

  /** Return the maximum time an alert should remain pending before it's
   * reported in a group */
  public long getMaxPendTime() {
    return Constants.DAY;
  }

  private String getSender(Configuration config) {
    String defaultSender = config.get(PARAM_DEFAULT_EMAIL_SENDER);
    return config.get(PARAM_EMAIL_SENDER, defaultSender);
  }

  private String getFrom(Alert alert, Configuration config) {
    try {
      String fmt = config.get(PARAM_EMAIL_FROM, DEFAULT_EMAIL_FROM);
      PrintfFormat pf = new PrintfFormat(fmt);
      // if not locally generated alert, should use our name instead
      String cacheName = alert.getString(Alert.ATTR_CACHE);
      String[] args = { cacheName, getSender(config) };
      return pf.sprintf(args);
    } catch (Exception e) {
      log.warning("getFrom()", e);
      return "LOCKSS cache";
    }
  }

  public boolean equals(Object obj) {
    if (obj instanceof AlertActionMail ) {
      return StringUtil.equalStrings(recipients,
				     ((AlertActionMail)obj).recipients);
    }
    return false;
  }

  public int hashCode() {
    if (recipients == null) return 17;
    return recipients.hashCode();
  }

  public String toString() {
    return "[AlertActions.Mail: " + recipients + "]";
  }
}
