package app.pathwise.mobile;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ServerUrlTest {

    @Test
    public void normalisesWhatPeopleType() {
        assertEquals("http://192.168.1.5:8080", ServerUrl.normalise(" 192.168.1.5:8080/ "));
        assertEquals("https://pathwise.example.org", ServerUrl.normalise("HTTPS://pathwise.example.org/"));
        assertEquals("http://10.0.2.2:8080", ServerUrl.normalise("http://10.0.2.2:8080"));
        assertNull(ServerUrl.normalise(""));
        assertNull(ServerUrl.normalise("http://host/some/path"));
        assertNull(ServerUrl.normalise("http://"));
    }

    @Test
    public void warnsOnlyForPlainHttpBeyondTheLocalNetwork() {
        assertFalse(ServerUrl.insecureRemote("http://10.0.2.2:8080"));
        assertFalse(ServerUrl.insecureRemote("http://192.168.0.12:8080"));
        assertFalse(ServerUrl.insecureRemote("http://172.20.1.1:8080"));
        assertFalse(ServerUrl.insecureRemote("https://abc.trycloudflare.com"));
        assertTrue(ServerUrl.insecureRemote("http://abc.trycloudflare.com"));
        assertTrue(ServerUrl.insecureRemote("http://172.32.0.1"));
    }
}
