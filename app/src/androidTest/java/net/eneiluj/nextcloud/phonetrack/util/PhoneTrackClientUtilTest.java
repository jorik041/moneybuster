package net.eneiluj.nextcloud.phonetrack.util;

import junit.framework.TestCase;

/**
 * Tests the PhoneTrackClientUtil
 * Created by stefan on 24.09.15.
 */
public class PhoneTrackClientUtilTest extends TestCase {
    public void testFormatURL() {
        assertEquals("https://example.com/", PhoneTrackClientUtil.formatURL("example.com/"));
        assertEquals("http://example.com/", PhoneTrackClientUtil.formatURL("http://example.com/"));
        assertEquals("https://example.com/", PhoneTrackClientUtil.formatURL("example.com/index.php"));
        assertEquals("https://example.com/", PhoneTrackClientUtil.formatURL("example.com/index.php/"));
        assertEquals("https://example.com/", PhoneTrackClientUtil.formatURL("example.com/index.php/apps"));
        assertEquals("https://example.com/", PhoneTrackClientUtil.formatURL("example.com/index.php/apps/phonetrack"));
        assertEquals("https://example.com/", PhoneTrackClientUtil.formatURL("example.com/index.php/apps/phonetrack/api"));
        assertEquals("https://example.com/", PhoneTrackClientUtil.formatURL("example.com/index.php/apps/phonetrack/api/v0.2"));
        assertEquals("https://example.com/", PhoneTrackClientUtil.formatURL("example.com/index.php/apps/phonetrack/api/v0.2/phonetrack"));
        assertEquals("https://example.com/nextcloud/", PhoneTrackClientUtil.formatURL("example.com/nextcloud"));
        assertEquals("http://example.com:443/nextcloud/", PhoneTrackClientUtil.formatURL("http://example.com:443/nextcloud/index.php/apps/phonetrack/api/v0.2/phonetrack"));
    }

    public void testIsHttp() {
        assertTrue(PhoneTrackClientUtil.isHttp("http://example.com"));
        assertTrue(PhoneTrackClientUtil.isHttp("http://www.example.com/"));
        assertFalse(PhoneTrackClientUtil.isHttp("https://www.example.com/"));
        assertFalse(PhoneTrackClientUtil.isHttp(null));
    }

    public void testIsValidURLTest() {
        assertTrue(PhoneTrackClientUtil.isValidURL(null, "https://demo.nextcloud.org/"));
        assertFalse(PhoneTrackClientUtil.isValidURL(null, "https://www.example.com/"));
        assertFalse(PhoneTrackClientUtil.isValidURL(null, "htp://www.example.com/"));
        assertFalse(PhoneTrackClientUtil.isValidURL(null, null));
    }
}