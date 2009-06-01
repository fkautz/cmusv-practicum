/*
 * $Id$
 */

/*

Copyright (c) 2000-2009 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.account;

import java.io.*;
import java.util.*;
import java.security.*;
import org.apache.oro.text.regex.*;
import org.apache.commons.lang.*;
import org.apache.commons.lang.time.*;
import org.mortbay.util.B64Code;
import org.mortbay.util.Credential;

import org.lockss.config.*;
import org.lockss.util.*;
import org.lockss.util.SerializationException.FileNotFound;
import org.lockss.jetty.*;

/** User account data.
 */
public abstract class UserAccount implements LockssSerializable, Comparable {
  static Logger log = Logger.getLogger("UserAccount");

  protected final String userName;
  protected String email;
  protected String currentPassword;
  protected String[] passwordHistory;
  protected String hashAlg;
  protected String roles;
  // most recent password change
  protected long lastPasswordChange;
  // most recent password change by the user (used only for too-frequent
  // password change check
  protected long lastUserPasswordChange;
  protected long lastPasswordReminderTime;
  protected boolean isDisabled;
  protected String disableReason;

  protected transient AccountManager acctMgr;
  protected transient String fileName;

  protected transient Set roleSet = null;
  protected transient Credential credential = null;
  protected transient int failedAttempts = 0;
  protected transient boolean isChanged = false;

  public UserAccount(String name) {
    this.userName = name;
  }

  /** Setup configuration before first use.  Called by factory. */
  protected void init(AccountManager acctMgr, Configuration config) {
    postLoadInit(acctMgr, config);
    init(acctMgr);
  }

  /** Setup configuration before first use.  Called by factory. */
  protected void init(AccountManager acctMgr) {
    if (getHistorySize() > 0) {
      this.passwordHistory = new String[getHistorySize()];
    }
    hashAlg = getDefaultHashAlgorithm();
    lastPasswordChange = -1;
    lastUserPasswordChange = -1;
  }

  /** Setup configuration before first use.  Called by factory. */
  protected void postLoadInit(AccountManager acctMgr, Configuration config) {
    this.acctMgr = acctMgr;
  }

  /** Return the username */
  public String getName() {
    return userName;
  }

  /** Return the email address */
  public String getEmail() {
    return email;
  }

  /** Return the encrypted password */
  public String getPassword() {
    return currentPassword;
  }

  /** Return the encrypted password */
  public boolean hasPassword() {
    return credential != null || currentPassword != null;
  }

  /** Return the user's roles as a string */
  public String getRoles() {
    return roles;
  }

  /** Return a collection of the user's roles  */
  public boolean isUserInRole(String role) {
    return getRoleSet().contains(role);
  }

  /** Return a collection of the user's roles  */
  public Set getRoleSet() {
    if (roleSet == null) {
      roleSet =
	SetUtil.theSet(StringUtil.breakAt(getRoles(), ',', -1, true, true));
    }
    return roleSet;
  }

  /** Set the email address */
  public void setEmail(String val) {
    setChanged(!StringUtil.equalStrings(email, val));
    email = val;
  }

  /** Set the filename */
  public void setFilename(String val) {
    fileName = val;
  }

  /** Get the filename */
  public String getFilename() {
    return fileName;
  }

  /** Get the time the password was last changed */
  public long getLastPasswordChange() {
    return lastPasswordChange;
  }
  /** Get the time the password was last changed by the user */
  public long getLastUserPasswordChange() {
    return lastUserPasswordChange;
  }

  /** Set the user's roles */
  public void setRoles(String val) {
    if (log.isDebug2()) log.debug2(userName + ".setRoles(" + val + ")");
    setChanged(!StringUtil.equalStrings(roles, val));
    roles = val;
    roleSet = null;
  }

  /** Set the user's roles */
  public void setRoles(Set roleSet) {
    if (log.isDebug2()) log.debug2(userName + ".setRoles(" + roleSet + ")");
    setChanged(!roleSet.equals(getRoleSet()));
    this.roles = StringUtil.separatedString(roleSet, ",");
    this.roleSet = roleSet;
  }

  /** Return the hash algorithm name */
  public String getHashAlgorithm() {
    return hashAlg;
  }

  public void setCredential(String cred) throws NoSuchAlgorithmException {
    if (credential != null) {
      throw new UnsupportedOperationException("Can't reset credential");
    }
    credential = MDCredential.makeCredential(cred);
    setChanged(true);
  }

  /** Return the credential string (ALG:encrypted_pwd */
  public Credential getCredential() {
    if (credential == null) {
      String credString = getCredentialString();
      log.info("credString: " + credString);
      if (credString == null) {
	return null;
      }
      try {
	credential = MDCredential.makeCredential(credString);
	if (log.isDebug2()) log.debug2("Made credential for "
				       + ": " + credential);
      } catch (NoSuchAlgorithmException e) {
	log.error("No credential; account disabled: " + getName(), e);
	credential = new NullCredential();
      } catch (RuntimeException e) {
	log.error("No credential; account disabled: " + getName(), e);
	credential = new NullCredential();
      }
    }
    return credential;
  }

  /** Check the credentials against this account's password. */
  public boolean check(Object credentials) {
    if (!isEnabled()) {
      return false;
    }
    Credential cred = getCredential();
    if (cred == null) {
      return false;
    }
    boolean res = cred.check(credentials);
    if (res) {
      setChanged(failedAttempts != 0);
      failedAttempts = 0;
    } else {
      ++failedAttempts;
      log.info("failedAttempts: " + failedAttempts);
      if (getMaxFailedAttempts() > 0
	  && failedAttempts >= getMaxFailedAttempts()) {
	String msg = ("Disabled: " + getMaxFailedAttempts() +
		      " failed login attempts at "
		      + expireDf.format(TimeBase.nowDate()));
	disable(msg);
	if (acctMgr != null) {
	  acctMgr.notifyAccountChange(this, msg);
	}
      }
    }
    return res;
  }

  /** Return the credential string (ALG:encrypted_pwd */
  String getCredentialString() {
    if (currentPassword == null) {
      return null;
    }
    return hashAlg + ":" + currentPassword;
  }

  public boolean isEditable() {
    return !isStaticUser();
  }

  public boolean isStaticUser() {
    return false;
  }

  // Must be implemented by subclasses

  /** Return the minimum password length */
  abstract protected int getMinPasswordLength();
  /** Return the number of previous passwords that may not be reused */
  abstract protected int getHistorySize();
  /** Return the shortest interval before which a user may again change his
   * password */
  abstract protected long getMinPasswordChangeInterval();
  /** Return the interval before which a user must change his password */
  abstract protected long getMaxPasswordChangeInterval();
  /** Return the interval before password expiration when a password
   * reminder message should be generated */
  abstract protected long getPasswordChangeReminderInterval();
  /** Return the amount of time after which an inactive user must re-login */
  abstract public long getInactivityLogout();
  /** Number of consecutive failed password attempts after which the
   * account is disabled */
  abstract protected int getMaxFailedAttempts();
  /** Return the hash algorithm to be used for new accounts */
  abstract protected String getDefaultHashAlgorithm();

  /** Change the password
   * @throws IllegalPassword if the new password is not legal
   * @throws IllegalPasswordChange
   */
  public void setPassword(String newPwd) throws IllegalPasswordChange {
    setPassword(newPwd, false);
  }

  public void setPassword(String newPwd, boolean isAdmin)
      throws IllegalPasswordChange {
    String hash = hashPassword(newPwd);
    checkLegalPassword(newPwd, hash, isAdmin);
    if (currentPassword != null && passwordHistory != null) {
      shiftArrayUp(passwordHistory);
      passwordHistory[0] = currentPassword;
    }
    currentPassword = hash;
    lastPasswordChange = TimeBase.nowMs();
    if (isAdmin) {
      lastUserPasswordChange = -1;
    } else {
      lastUserPasswordChange = lastPasswordChange;
    }
    enable();
    setChanged(true);
    clearCaches();
  }

  private void clearCaches() {
    credential = null;
  }

  void shiftArrayUp(String[] array) {
    System.arraycopy(array, 0, array, 1, array.length-1);
  }

  String hashPassword(String pwd) throws IllegalPassword {
    if (pwd == null) {
      throw new IllegalPassword("Password may not be empty");
    }
    return b64Hash(pwd);
  }

  String b64Hash(String pwd) {
    try {
      MessageDigest md = getMessageDigest();
      md.update(pwd.getBytes(Constants.DEFAULT_ENCODING));

      return ByteArray.toHexString(md.digest());
//       return String.valueOf(B64Code.encode(md.digest()));
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Unsupported hash algorithm: " + hashAlg);
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("Unsupported Encoding: " +
				 Constants.DEFAULT_ENCODING +
				 " (shouldn't happen)");
    }
  }

  private MessageDigest getMessageDigest()
      throws NoSuchAlgorithmException {
    return MessageDigest.getInstance(hashAlg);
  }

  private static FastDateFormat expireDf =
    FastDateFormat.getInstance("EEE dd MMM, HH:mm zzz");


  public boolean hasPasswordExpired() {
    return hasPasswordExpired(getMaxPasswordChangeInterval());
  }

  private boolean hasPasswordExpired(long expireInterval) {
    return lastPasswordChange > 0 && expireInterval > 0
      && TimeBase.msSince(lastPasswordChange) >= expireInterval;
  }

  public long getLastPasswordReminderTime() {
    return lastPasswordReminderTime;
  }

  public void setLastPasswordReminderTime(long val) {
    lastPasswordReminderTime = val;
  }

  public String getPasswordChangeReminder() {
    if (lastPasswordChange > 0) {
      long expireInterval = getMaxPasswordChangeInterval();
      if (hasPasswordExpired(expireInterval)) {
	return "Password has expired";
      } else {
	long reminderInterval = getPasswordChangeReminderInterval();
	if (reminderInterval > 0
	    && (TimeBase.msSince(lastPasswordChange)
		>= (expireInterval - reminderInterval))) {
	  return "You must change your password before "
	    + expireDf.format(lastPasswordChange + expireInterval);
	}
      }
    }
    return null;
  }

  public void enable() {
    setChanged(isDisabled);
    isDisabled = false;
    disableReason = null;
  }

  public void disable(String reason) {
    setChanged(!isDisabled || !StringUtil.equalStrings(disableReason, reason));
    log.debug("Disabled account " + getName() + ": " + reason);
    isDisabled = true;
    disableReason = reason;
  }

  public boolean isEnabled() {
    if (isDisabled) {
      return false;
    }
    if (hasPasswordExpired()) {
      return false;
    }
    return true;
  }

  public String getDisabledMessage() {
    if (isEnabled()) {
      return null;
    }
    if (disableReason != null) {
      return disableReason;
    }
    if (hasPasswordExpired()) {
      return "Password has expired";
    }
    return null;
  }

  public boolean isChanged() {
    return isChanged;
  }

  public void notChanged() {
    isChanged = false;
  }

  void setChanged(boolean changed) {
    isChanged |= changed;
  }

  void checkLegalPassword(String newPwd, String hash, boolean isAdmin)
      throws IllegalPasswordChange {
    if (!isAdmin && lastUserPasswordChange > 0 &&
	(TimeBase.msSince(lastUserPasswordChange)
	 < getMinPasswordChangeInterval())) {
      String msg = "May not change password more than once per "
	+ StringUtil.timeIntervalToString(getMinPasswordChangeInterval());
      throw new IllegalPasswordChange(msg);
    }

    if (newPwd == null || newPwd.length() < getMinPasswordLength()) {
      throw new IllegalPassword("Password must be at least "
				+ getMinPasswordLength() + " characters");
    }
    if (hasIllegalCharacter(newPwd)) {
      throw new IllegalPassword("Password must contain only ascii alphanumeric and special chars");
    }
    if ((currentPassword != null && currentPassword.equals(hash))) {
      throw new IllegalPassword("May not repeat previous password");
    } else if (getHistorySize() > 0
	       && ArrayUtils.contains(passwordHistory, hash)) {
      throw new IllegalPassword("May not repeat any of the previous "
				+ passwordHistory.length + " passwords");
    }
  }

  static String SPECIAL_CHARS = "!@#$%^&*()+=:;_<>,.?/{}~'`\\-\\|\\\\\"\\[\\]";

  static Pattern passwdCharPat =
    RegexpUtil.uncheckedCompile("[^a-zA-Z0-9" + SPECIAL_CHARS + "]",
				Perl5Compiler.READ_ONLY_MASK);

  boolean hasIllegalCharacter(String str) {
    Perl5Matcher matcher = RegexpUtil.getMatcher();
    return matcher.contains(str, passwdCharPat);
  }

  public int compareTo(Object o) {
    return compareTo((UserAccount)o);
  }

  public int compareTo(UserAccount other) {
    return getName().compareTo(other.getName());
  }

  public class NullCredential extends Credential {
    public boolean check(Object credentials) {
      return false;
    }
  }

  public class IllegalPasswordChange extends Exception {
    IllegalPasswordChange(String reason) {
      super(reason);
    }
  }

  public class IllegalPassword extends IllegalPasswordChange {
    IllegalPassword(String reason) {
      super(reason);
    }
  }

  public static abstract class Factory {
    public abstract UserAccount newUser(String name,
					AccountManager acctMgr,
					Configuration config);
    public UserAccount newUser(String name, AccountManager acctMgr) {
      return newUser(name, acctMgr, ConfigManager.getCurrentConfig());
    }
  }
}
