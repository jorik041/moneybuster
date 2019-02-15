package net.eneiluj.moneybuster.util;

import junit.framework.TestCase;

/**
 * Tests the CospendClientUtil
 * Created by stefan on 24.09.15.
 */
public class MoneyBusterClientUtilTest extends TestCase {
    public void testFormatURL() {
        assertEquals("https://example.com/", CospendClientUtil.formatURL("example.com/"));
        assertEquals("http://example.com/", CospendClientUtil.formatURL("http://example.com/"));
        assertEquals("https://example.com/", CospendClientUtil.formatURL("example.com/index.php"));
        assertEquals("https://example.com/", CospendClientUtil.formatURL("example.com/index.php/"));
        assertEquals("https://example.com/", CospendClientUtil.formatURL("example.com/index.php/apps"));
        assertEquals("https://example.com/", CospendClientUtil.formatURL("example.com/index.php/apps/ihatemoney"));
        assertEquals("https://example.com/", CospendClientUtil.formatURL("example.com/index.php/apps/ihatemoney/api"));
        assertEquals("https://example.com/", CospendClientUtil.formatURL("example.com/index.php/apps/ihatemoney/api/v0.2"));
        assertEquals("https://example.com/", CospendClientUtil.formatURL("example.com/index.php/apps/ihatemoney/api/v0.2/ihatemoney"));
        assertEquals("https://example.com/nextcloud/", CospendClientUtil.formatURL("example.com/nextcloud"));
        assertEquals("http://example.com:443/nextcloud/", CospendClientUtil.formatURL("http://example.com:443/nextcloud/index.php/apps/ihatemoney/api/v0.2/ihatemoney"));
    }

    public void testIsHttp() {
        assertTrue(CospendClientUtil.isHttp("http://example.com"));
        assertTrue(CospendClientUtil.isHttp("http://www.example.com/"));
        assertFalse(CospendClientUtil.isHttp("https://www.example.com/"));
        assertFalse(CospendClientUtil.isHttp(null));
    }

    public void testIsValidURLTest() {
        assertTrue(CospendClientUtil.isValidURL(null, "https://demo.nextcloud.org/"));
        assertFalse(CospendClientUtil.isValidURL(null, "https://www.example.com/"));
        assertFalse(CospendClientUtil.isValidURL(null, "htp://www.example.com/"));
        assertFalse(CospendClientUtil.isValidURL(null, null));
    }
}