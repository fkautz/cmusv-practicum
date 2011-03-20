package de.thiemeconnect.plugin;

import java.net.MalformedURLException;
import java.util.Properties;

import org.lockss.config.Configuration;
import org.lockss.daemon.ConfigParamDescr;
import org.lockss.plugin.ArchivalUnit;
import org.lockss.plugin.ArchivalUnit.ConfigurationException;
import org.lockss.plugin.definable.DefinableArchivalUnit;
import org.lockss.plugin.definable.DefinablePlugin;
import org.lockss.test.ConfigurationUtil;
import org.lockss.test.LockssTestCase;
import org.lockss.util.ListUtil;

public class TestThiemePlugin extends LockssTestCase {
	private DefinablePlugin plugin;
	static final String BASE_URL_KEY = ConfigParamDescr.BASE_URL.getKey();
	static final String YEAR_KEY = ConfigParamDescr.YEAR.getKey();
	static final String JOURNAL_NAME = "journal_name";
	static final String SESSION_START_URL = "session_start_url";

	private ConfigParamDescr JOURNAL_NAME_DESC = new ConfigParamDescr()
			.setKey(JOURNAL_NAME).setDisplayName("Journal Name")
			.setType(ConfigParamDescr.TYPE_STRING).setSize(40)
			.setDescription("Journal Name");

	private ConfigParamDescr SESSION_START_URL_DESC = new ConfigParamDescr()
			.setKey(SESSION_START_URL).setDisplayName("Session Start URL")
			.setType(ConfigParamDescr.TYPE_URL).setSize(40)
			.setDescription("Session Start URL");

	public void setUp() throws Exception {
		super.setUp();
		plugin = new DefinablePlugin();
		plugin.initPlugin(getMockLockssDaemon(),
				"de.thiemeconnect.plugin.ThiemeConnectPlugin");
	}

	public void testCreateAu() throws ConfigurationException {
		Properties props = getProperties();

		DefinableArchivalUnit au = null;
		try {
			au = makeAuFromProps(props);
		} catch (ConfigurationException ex) {
			throw ex;
		}
	}

	public Properties getProperties() {
		Properties props = new Properties();
		props.setProperty(BASE_URL_KEY, "http://www.example.com/");
		props.setProperty(YEAR_KEY, "2003");
		props.setProperty(JOURNAL_NAME, "example journal");
		props.setProperty(SESSION_START_URL,
				"http://www.example.com/index.html");
		return props;
	}

	public void testGetAuConstructsProperAu()
			throws ArchivalUnit.ConfigurationException, MalformedURLException {
		Properties props = getProperties();

		DefinableArchivalUnit au = makeAuFromProps(props);
		assertEquals(
				"Thieme Connect Plugin, Base URL http://www.example.com/, Year 2003",
				au.getName());
	}

	public void testGetAuNullConfig()
			throws ArchivalUnit.ConfigurationException {
		try {
			plugin.configureAu(null, null);
			fail("Didn't throw ArchivalUnit.ConfigurationException");
		} catch (ArchivalUnit.ConfigurationException e) {
		}
	}

	public void testGetAuHandlesBadUrl()
			throws ArchivalUnit.ConfigurationException, MalformedURLException {
		Properties props = getProperties();
		props.setProperty(BASE_URL_KEY, "blah");
		props.setProperty(YEAR_KEY, "2003");

		try {
			DefinableArchivalUnit au = makeAuFromProps(props);
			fail("Didn't throw InstantiationException when given a bad url");
		} catch (ArchivalUnit.ConfigurationException auie) {
			ConfigParamDescr.InvalidFormatException murle = (ConfigParamDescr.InvalidFormatException) auie
					.getCause();
			assertNotNull(auie.getCause());
		}
	}

	public void testGetPluginId() {
		assertEquals("de.thiemeconnect.plugin.ThiemeConnectPlugin",
				plugin.getPluginId());
	}

	private DefinableArchivalUnit makeAuFromProps(Properties props)
			throws ArchivalUnit.ConfigurationException {
		Configuration config = ConfigurationUtil.fromProps(props);
		return (DefinableArchivalUnit) plugin.configureAu(config, null);
	}

	public void testGetAuConfigProperties() {

		/* order seems to matter in the list */
		assertEquals(ListUtil.list(ConfigParamDescr.BASE_URL,
				JOURNAL_NAME_DESC, 
				ConfigParamDescr.YEAR
				), plugin.getLocalAuConfigDescrs());
	}
}
