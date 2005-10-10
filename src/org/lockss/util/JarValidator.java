/*
 * $Id$
 */

/*

Copyright (c) 2000-2003 Board of Trustees of Leland Stanford Jr. University,
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

package org.lockss.util;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.lockss.util.*;
import org.lockss.daemon.*;
import org.lockss.plugin.*;
import java.util.jar.*;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.*;

/**
 * Verify a signed JAR stored in the repository.
 */
public class JarValidator {
  private KeyStore m_keystore;
  private File m_pluginDir;

  private static Logger log = Logger.getLogger("JarValidator");

  public JarValidator(KeyStore keystore, File pluginDir) {
    this.m_keystore = keystore;
    this.m_pluginDir = pluginDir;
  }

  /**
   * Validate the jar CU, and copy it into the blessed
   * working directory, if it passes inspection.
   * @return The blessed file, or null if the file could not be
   *         copied or blessed.
   */
  public File getBlessedJar(CachedUrl cu)
      throws IOException, JarValidationException {

    if (m_pluginDir == null) {
      throw new JarValidationException("No plugin directory, can't continue.");
    }

    File f = null;
    InputStream cuis = null;
    FileOutputStream fos = null;
    try {
      validatePluginJar(cu);
      cuis = cu.getUnfilteredInputStream();
      f = File.createTempFile("plugin-", ".jar", m_pluginDir);
      fos = new FileOutputStream(f);
      long bytes = StreamUtil.copy(cuis, fos);
    } finally {
      if (fos != null) {
	fos.close();
      }
      if (cuis != null) {
	cuis.close();
      }
    }

    return f;
  }

  /**
   * alidate the JAR file represented by this CachedUrl.
   * @throws JarValidationException if the CU does not represent
   *         a valid signed JAR.
   */
  private void validatePluginJar(CachedUrl cu)
      throws IOException, JarValidationException {
    boolean foundJarEntry = false;

    // If the keystore is null we can't continue.
    if (m_keystore == null) {
      throw new JarValidationException("No keystore, can't continue.");
    }

    JarInputStream jstr = null;
    try {
      jstr = new JarInputStream(cu.getUnfilteredInputStream(), true);

      Manifest manifest = jstr.getManifest();

      if (manifest == null) {
	throw new JarValidationException("No manifest");
      }

      // Iterate across all the JAR entries, validating each
      // one.
      JarEntry je;

      while ((je = jstr.getNextJarEntry()) != null) {
	// Only entries in the manifest matter.  Nothing else
	// will get loaded.
	if (inManifest(je.getName(), manifest)) {
	  verifyJarEntry(jstr, je);
	  if (!foundJarEntry) foundJarEntry = true;
	}
      }
    } finally {
      if (jstr != null)
	jstr.close();
    }

    if (!foundJarEntry) {
      throw new JarValidationException("No valid entries found in manifest.");
    }
  }

  /**
   * Check to see if the entry name exists in the manifest.
   */
  private boolean inManifest(String entryName, Manifest manifest) {
    return (manifest.getAttributes(entryName) != null)
      || (manifest.getAttributes ("./" + entryName) != null)
      || (manifest.getAttributes ("/" + entryName) != null);
  }

  /**
   * Verify the integrity of a Jar Entry by simply reading it in its
   * entirety from the input stream.  If the jar entry's digest is
   * invalid, this will throw a SecurityException.
   *
   * NOTE: It is very important not to close the input stream at the
   * end.  Let the caller worry about that.
   */
  private void verifyDigests(JarInputStream is, JarEntry entry)
      throws SecurityException, IOException {
    byte[] buffer = new byte[8192];
    int n = 0;
    while ((n = is.read (buffer, 0, buffer.length)) != -1) {
      // Read the entry, do nothing with the contents.  This will
      // throw a SecurityException if the jar entry is tainted.
    }
  }

  /**
   * Check the validity of each of the supplied certificates.  If
   * at least one certificate is a valid, trusted X509 certificate
   * in our keystore, the JAR passes.
   */
  private void verifyJarEntry(JarInputStream jstr, JarEntry je)
      throws JarValidationException {
    // An unfortunate side-effect of the way JarEntries work.  We must
    // read the entire input of the jar entry, otherwise
    // "getCertificates()" will always return null.  On the plus side,
    // this will check that the entry's digest is good.
    try {
      verifyDigests(jstr, je);
    } catch (IOException ex) {
      log.warning("IOException trying to read jar entry " + je.getName(), ex);
      throw new JarValidationException("IOException trying to read jar entry " + je.getName());
    } catch (SecurityException ex) {
      throw new JarValidationException("Jar entry " + je.getName() + " is tainted.");
    }

    Certificate[] certs = getCertificates(je);

    if (certs == null) {
      throw new JarValidationException("Jar entry " + je.getName() + " is not signed. " +
				       "Unsigned entries are not allowed.");
    }

    for (int i = 0; i < certs.length; i++) {
      X509Certificate jarEntryCert = (X509Certificate)certs[i];
      try {
	jarEntryCert.checkValidity();
      } catch (CertificateExpiredException ex) {
	log.warning("Certificate is no longer valid, skipping.");
	continue;
      } catch (CertificateNotYetValidException ex) {
	log.warning("Certificate is not yet valid, skipping.");
	continue;
      }

      X509Certificate issuerCert = null;

      try {
	String signer = m_keystore.getCertificateAlias(jarEntryCert);
	if (signer != null && m_keystore.isCertificateEntry(signer)) {
	  // Shortcut: If the keystore returns an alias for this cert,
	  // and isCertificateEntry() returns true, we know this is a
	  // valid, trusted certificate.  If not, we'll have to do
	  // more work to see if our keystore holds the signing public
	  // key under a different certificate (this should usually
	  // not be the case!)
	  log.debug2("Found trusted certificate alias: " + signer);
	  return;
	}

	String name = jarEntryCert.getSubjectDN().getName();

	// Loop through all the entries in the keystore looking for a
	// certificate that matches the jar entry certificate we're
	// currently checking.
	for (Enumeration aliases = m_keystore.aliases();
	     aliases.hasMoreElements(); ) {
	  String s = (String)aliases.nextElement();
	  X509Certificate aliasCert = null;

	  try {
	    aliasCert = (X509Certificate)m_keystore.getCertificate(s);
	  } catch (Exception ex) {
	    continue; // Not a 509 cert, try the next one.
	  }

	  if (name.equals(aliasCert.getSubjectDN().getName())) {
	    // Aha!  This is a match for the issuer's certificate,
	    // so let's try to validate this one.
	    issuerCert = aliasCert;
	    break;
	  }
	}
      } catch (KeyStoreException ex) {
	log.error("Exception while working with the keystore, " +
		  "skipping certificate.", ex);
	continue; // Try the next certificate.
      }

      if (issuerCert == null) {
	throw new JarValidationException("No issuer certificate could be found matching " +
					 "jar entry: " + je.getName());
      }

      try {
	// Verify that the jar entry certificate was verified with the
	// private key corresponding to issuerCert's public key.
	jarEntryCert.verify(issuerCert.getPublicKey());
      } catch (Exception ex) {
	throw new JarValidationException("Jar entry's certificate does not validate: " +
					 je.getName());
      }
    }
  }

  /**
   * JAVA1.5
   *
   * Method made necessary by changes in Java 1.5.  Return all the certificates
   * for a specified JarEntry.
   *
   * @param entry
   */
  private Certificate[] getCertificates(JarEntry entry) {
    Certificate[] certs = entry.getCertificates();

    // Try it the 1.5 way.
    if (certs == null) {
      try {
        // JAVA1.5
        // CodeSigner[] codeSigners = entry.getCodeSigners()
        Method getCodeSigners =
          entry.getClass().getMethod("getCodeSigners", new Class[0]);
        // This call will fail in 1.4, let the catch handle it.
        Object[] codeSigners =
          (Object[])getCodeSigners.invoke(entry, new Object[0]);
        if (codeSigners != null && codeSigners.length > 0) {
          // JAVA1.5
          // List<Certificate> list = codeSigner.getSignerCertPath().getCertificates();
          Class codeSigner = Class.forName("java.security.CodeSigner");
          Method getSignerCertPath =
            codeSigner.getMethod("getSignerCertPath", new Class[0]);
          CertPath certPath =
            (CertPath)getSignerCertPath.invoke(Array.get(codeSigners, 0),
                                               new Object[0]);
          List list = certPath.getCertificates();
          certs = (Certificate[])list.toArray(new Certificate[list.size()]);
        }
      } catch (NoSuchMethodException ex) {
        // Silently fall-through, Java version < 1.5.
      } catch (Exception ex) {
        log.error("Unexpected exception getting certificates for JarEntry "
                  + entry, ex);
      }
    }

    return certs;
  }

  /**
   * If the Jar doesn't verify, this exception will
   * explain exactly why.
   */
  public static class JarValidationException extends Exception {
    JarValidationException(String s) {
      super(s);
    }
  }
}
