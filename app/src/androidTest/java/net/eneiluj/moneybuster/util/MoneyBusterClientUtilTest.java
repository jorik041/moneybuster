package net.eneiluj.moneybuster.util;

import junit.framework.TestCase;

/**
 * Tests the SpendClientUtil
 * Created by stefan on 24.09.15.
 */
public class MoneyBusterClientUtilTest extends TestCase {
    public void testFormatURL() {
        assertEquals("https://example.com/", SpendClientUtil.formatURL("example.com/"));
        assertEquals("http://example.com/", SpendClientUtil.formatURL("http://example.com/"));
        assertEquals("https://example.com/", SpendClientUtil.formatURL("example.com/index.php"));
        assertEquals("https://example.com/", SpendClientUtil.formatURL("example.com/index.php/"));
        assertEquals("https://example.com/", SpendClientUtil.formatURL("example.com/index.php/apps"));
        assertEquals("https://example.com/", SpendClientUtil.formatURL("example.com/index.php/apps/ihatemoney"));
        assertEquals("https://example.com/", SpendClientUtil.formatURL("example.com/index.php/apps/ihatemoney/api"));
        assertEquals("https://example.com/", SpendClientUtil.formatURL("example.com/index.php/apps/ihatemoney/api/v0.2"));
        assertEquals("https://example.com/", SpendClientUtil.formatURL("example.com/index.php/apps/ihatemoney/api/v0.2/ihatemoney"));
        assertEquals("https://example.com/nextcloud/", SpendClientUtil.formatURL("example.com/nextcloud"));
        assertEquals("http://example.com:443/nextcloud/", SpendClientUtil.formatURL("http://example.com:443/nextcloud/index.php/apps/ihatemoney/api/v0.2/ihatemoney"));
    }

    public void testIsHttp() {
        assertTrue(SpendClientUtil.isHttp("http://example.com"));
        assertTrue(SpendClientUtil.isHttp("http://www.example.com/"));
        assertFalse(SpendClientUtil.isHttp("https://www.example.com/"));
        assertFalse(SpendClientUtil.isHttp(null));
    }

    public void testIsValidURLTest() {
        assertTrue(SpendClientUtil.isValidURL(null, "https://demo.nextcloud.org/"));
        assertFalse(SpendClientUtil.isValidURL(null, "https://www.example.com/"));
        assertFalse(SpendClientUtil.isValidURL(null, "htp://www.example.com/"));
        assertFalse(SpendClientUtil.isValidURL(null, null));
    }
}