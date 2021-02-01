import com.devo.feeds.data.TypeInference
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TypeInferenceTest {

    @Test
    fun `Should match md5 hash`() {
        assertTrue(TypeInference.isMD5("1a79a4d60de6718e8e5b326e338ae533"))
    }

    @Test
    fun `Should fail to match non md5`() {
        assertFalse(TypeInference.isMD5("abcdefg"))
    }

    @Test
    fun `Should match ipv4`() {
        assertTrue(TypeInference.isIp("192.168.1.1"))
        assertTrue(TypeInference.isIp("14.161.43.154"))
    }

    @Test
    fun `Should match ipv6`() {
        assertTrue(TypeInference.isIp("2607:f0d0:1002:51::4"))
        assertTrue(TypeInference.isIp("2607:f0d0:1002:0051:0000:0000:0000:0004"))
    }

    @Test
    fun `Should fail to match hostname`() {
        assertFalse(TypeInference.isIp("localhost"))
    }

    @Test
    fun `Should match hostname`() {
        assertTrue(TypeInference.isDomain("devo.com"))
    }

    @Test
    fun `Should match hostname without tld`() {
        assertTrue(TypeInference.isDomain("localhost"))
    }
}
