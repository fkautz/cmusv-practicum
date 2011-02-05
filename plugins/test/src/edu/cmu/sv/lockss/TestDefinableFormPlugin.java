package edu.cmu.sv.lockss;

import java.net.MalformedURLException;
import java.util.Properties;

import org.lockss.config.Configuration;
import org.lockss.daemon.ConfigParamDescr;
import org.lockss.daemon.MimeTypeInfo;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.ArchivalUnit.ConfigurationException;
import org.lockss.plugin.definable.DefinableArchivalUnit;
import org.lockss.plugin.definable.DefinablePlugin;
import org.lockss.plugin.wrapper.WrapperUtil;
import org.lockss.test.ConfigurationUtil;
import org.lockss.test.LockssTestCase;
import org.lockss.util.ListUtil;
import org.lockss.util.urlconn.HttpResultMap;
import org.lockss.util.urlconn.CacheException.RetryDeadLinkException;

public class TestDefinableFormPlugin extends LockssTestCase {
	private DefinablePlugin plugin;

	static final String BASE_URL_KEY = ConfigParamDescr.BASE_URL.getKey();
	// static final String YEAR_KEY = ConfigParamDescr.YEAR.getKey();
	static final String VOL_KEY = ConfigParamDescr.VOLUME_NUMBER.getKey();

	public TestDefinableFormPlugin(String msg) {
		super(msg);
	}

	public void setUp() throws Exception {
		super.setUp();
		plugin = new DefinableFormPlugin();
		plugin.initPlugin(getMockLockssDaemon(),
				"edu.cmu.sv.lockss.TestFormPlugin");
	}

	public void testGetAuNullConfig()
			throws ArchivalUnit.ConfigurationException {
		try {
			plugin.configureAu(null, null);
			fail("Didn't throw ArchivalUnit.ConfigurationException");
		} catch (ArchivalUnit.ConfigurationException e) {
			// passed
		}
	}

	public void testCreateAu() {
		Properties props = new Properties();
		props.setProperty(BASE_URL_KEY, "http://www.example.com/");
		props.setProperty(VOL_KEY, "32");
		// props.setProperty(YEAR_KEY, "2004");
		DefinableArchivalUnit au = null;
		try {
			au = makeAuFromProps(props);
			assertNotNull(au);
		} catch (ConfigurationException ex) {
			throw new RuntimeException(ex);
		}
	}

	private DefinableArchivalUnit makeAuFromProps(Properties props)
			throws ArchivalUnit.ConfigurationException {
		Configuration config = ConfigurationUtil.fromProps(props);
		return (DefinableArchivalUnit) plugin.configureAu(config, null);
	}

	public void testGetAuHandlesBadUrl()
			throws ArchivalUnit.ConfigurationException, MalformedURLException {
		Properties props = new Properties();
		props.setProperty(VOL_KEY, "322");
		props.setProperty(BASE_URL_KEY, "blah");
		// props.setProperty(YEAR_KEY, "2004");

		try {
			DefinableArchivalUnit au = makeAuFromProps(props);
			fail("Didn't throw InstantiationException when given a bad url");
		} catch (ArchivalUnit.ConfigurationException auie) {
			ConfigParamDescr.InvalidFormatException murle = (ConfigParamDescr.InvalidFormatException) auie
					.getCause();
			assertNotNull(auie.getCause());
		}
	}

	public void testGetAuConstructsProperAu()
			throws ArchivalUnit.ConfigurationException, MalformedURLException {
		Properties props = new Properties();
		props.setProperty(VOL_KEY, "322");
		props.setProperty(BASE_URL_KEY, "http://www.example.com/");
		// props.setProperty(YEAR_KEY, "2004");

		DefinableArchivalUnit au = makeAuFromProps(props);
		assertEquals(
				"Test Form Plugin, Base URL http://www.example.com/, Volume 322",
				au.getName());
	}

	public void testGetPluginId() {
		assertEquals("edu.cmu.sv.lockss.TestFormPlugin", plugin.getPluginId());
	}

	public void testGetAuConfigProperties() {
		assertEquals(ListUtil.list(ConfigParamDescr.BASE_URL,
				ConfigParamDescr.VOLUME_NUMBER),
		// ConfigParamDescr.YEAR),
				plugin.getLocalAuConfigDescrs());
	}

	public void testHandles404Result() throws Exception {
		assertClass(RetryDeadLinkException.class,
				((HttpResultMap) plugin.getCacheResultMap()).mapException(null,
						null, 404, null));

	}

	public void testGetArticleMetadataExtractor() { // XXX Uncomment when
													// iterators and extractors
													// are back
		// Properties props = new Properties();
		// props.setProperty(BASE_URL_KEY, "http://www.example.com/");
		// props.setProperty(VOL_KEY, "32");
		// // props.setProperty(YEAR_KEY, "2004");
		// DefinableArchivalUnit au = null;
		// try {
		// au = makeAuFromProps(props);
		// }
		// catch (ConfigurationException ex) {
		// }
		// assertTrue(""+plugin.getArticleMetadataExtractor(MetadataTarget.Any,
		// au),
		// plugin.getArticleMetadataExtractor(null, au) instanceof
		// HighWireArticleIteratorFactory.HighWireArticleMetadataExtractor);
		// assertTrue(""+plugin.getFileMetadataExtractor(MetadataTarget.Any,
		// "text/html", au),
		// plugin.getFileMetadataExtractor(MetadataTarget.Any, "text/html", au)
		// instanceof
		// org.lockss.extractor.SimpleMetaTagMetadataExtractor);
	}

	public void testGetHashFilterFactory() {
		assertNull(plugin.getHashFilterFactory("BogusFilterFactory"));
		assertNotNull(plugin.getHashFilterFactory("application/pdf"));
		assertTrue(WrapperUtil.unwrap(plugin
				.getHashFilterFactory("application/pdf")) instanceof org.lockss.plugin.highwire.HighWirePdfFilterFactory);
	}

	public void testGetArticleIteratorFactory() { // XXX Uncomment when
													// iterators and extractors
													// are back
		// assertTrue(WrapperUtil.unwrap(plugin.getArticleIteratorFactory())
		// instanceof
		// org.lockss.plugin.highwire.HighWireArticleIteratorFactory);
	}

	public void testGetDefaultArticleMimeType() {
		assertNotNull(plugin.getDefaultArticleMimeType());
		assertEquals("text/html", plugin.getDefaultArticleMimeType());
	}
}
