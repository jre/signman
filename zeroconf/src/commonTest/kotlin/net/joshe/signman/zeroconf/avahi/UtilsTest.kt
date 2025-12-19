package net.joshe.signman.zeroconf.avahi

import net.joshe.signman.zeroconf.serviceTypeValid
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UtilsTest {
    @Test fun testServiceTypeValid1() = assertTrue { serviceTypeValid("_foo._bar") }
    @Test fun testServiceTypeValid2() = assertTrue { serviceTypeValid("_foo-bar._baz") }
    @Test fun testServiceTypeValid3() = assertTrue { serviceTypeValid("_a._b") }
    @Test fun testServiceTypeValid4() = assertTrue { serviceTypeValid("_\u0ca0\u0332\u0ca0._tcp") }
    @Test fun testServiceTypeInvalid1() = assertFalse { serviceTypeValid("") }
    @Test fun testServiceTypeInvalid2() = assertFalse { serviceTypeValid(".") }
    @Test fun testServiceTypeInvalid3() = assertFalse { serviceTypeValid("_._") }
    @Test fun testServiceTypeInvalid4() = assertFalse { serviceTypeValid("_foo._") }
    @Test fun testServiceTypeInvalid5() = assertFalse { serviceTypeValid("_._bar") }
    @Test fun testServiceTypeInvalid6() = assertFalse { serviceTypeValid("foo.bar") }
    @Test fun testServiceTypeInvalid7() = assertFalse { serviceTypeValid("foo._bar") }
    @Test fun testServiceTypeInvalid8() = assertFalse { serviceTypeValid("_foo.bar") }
    @Test fun testServiceTypeInvalid9() = assertFalse { serviceTypeValid("_foo.bar_") }
    @Test fun testServiceTypeInvalid10() = assertFalse { serviceTypeValid("foo_._bar") }
    @Test fun testServiceTypeInvalid11() = assertFalse { serviceTypeValid("_foo_bar._baz") }
    @Test fun testServiceTypeInvalid12() = assertFalse { serviceTypeValid("foo_bar._baz") }
    @Test fun testServiceTypeInvalid13() = assertFalse { serviceTypeValid("_foo._bar._baz") }
    @Test fun testServiceTypeInvalid14() = assertFalse { serviceTypeValid("_foo.bar._baz") }
}
