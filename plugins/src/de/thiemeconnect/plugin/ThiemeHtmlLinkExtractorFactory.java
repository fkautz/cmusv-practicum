/**
 * 
 */
package de.thiemeconnect.plugin;

import org.lockss.daemon.PluginException;
import org.lockss.extractor.LinkExtractor;
import org.lockss.extractor.LinkExtractorFactory;

/**
 * @author nvibhor
 *
 */
public class ThiemeHtmlLinkExtractorFactory implements LinkExtractorFactory {

	/* (non-Javadoc)
	 * @see org.lockss.extractor.LinkExtractorFactory#createLinkExtractor(java.lang.String)
	 */
	@Override
	public LinkExtractor createLinkExtractor(String mimeType)
			throws PluginException {
		return new ThiemeHtmlLinkExtractor();
	}

}
