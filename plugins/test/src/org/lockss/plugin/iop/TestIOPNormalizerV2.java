package org.lockss.plugin.iop;

import org.lockss.plugin.FormUrlHelper;
import org.lockss.test.LockssTestCase;

public class TestIOPNormalizerV2 extends LockssTestCase {
	public void testDuplicationValues() {
		IOPNormalizerV2 norm = new IOPNormalizerV2();
		String url = "http://iopscience.iop.org/export?articleId=2043-6262%2F1%2F2%2F025001&exportFormat=iopexport_csv&exportType=refs&navsubmit=Export+Results";
		String url2 = url + "&navsubmit=Export+Results";
		assertEquals(url, norm.normalizeUrl(url2, null));
	}
}
