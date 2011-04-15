package org.lockss.prototypes.plugin;

import org.lockss.daemon.PluginException;
import org.lockss.extractor.LinkExtractor;
import org.lockss.extractor.LinkExtractorFactory;

public class SeleniumLinkExtractorFactory implements LinkExtractorFactory {

	@Override
	public LinkExtractor createLinkExtractor(String mimeType)
			throws PluginException {
		return new SeleniumLinkExtractor();
	}

}
