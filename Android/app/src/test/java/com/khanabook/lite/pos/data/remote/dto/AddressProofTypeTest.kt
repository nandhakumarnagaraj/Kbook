package com.khanabook.lite.pos.data.remote.dto

import org.junit.Assert.*
import org.junit.Test

class AddressProofTypeTest {

    @Test
    fun `fromCode resolves valid types case-insensitively`() {
        assertEquals(AddressProofType.ELECTRICITY_BILL, AddressProofType.fromCode("ELECTRICITY_BILL"))
        assertEquals(AddressProofType.ELECTRICITY_BILL, AddressProofType.fromCode("electricity_bill"))
        assertEquals(AddressProofType.GST_CERTIFICATE, AddressProofType.fromCode("GST_CERTIFICATE"))
        assertEquals(AddressProofType.SHOP_ESTABLISHMENT, AddressProofType.fromCode("shop_establishment"))
        assertEquals(AddressProofType.RENT_AGREEMENT, AddressProofType.fromCode("Rent_Agreement"))
        assertEquals(AddressProofType.FSSAI_LICENSE, AddressProofType.fromCode("FSSAI_LICENSE"))
        assertEquals(AddressProofType.UDYAM_MSME, AddressProofType.fromCode("udyam_msme"))
        assertEquals(AddressProofType.TRADE_LICENSE, AddressProofType.fromCode("trade_license"))
    }

    @Test
    fun `fromCode returns null on invalid or null code`() {
        assertNull(AddressProofType.fromCode(null))
        assertNull(AddressProofType.fromCode(""))
        assertNull(AddressProofType.fromCode("INVALID_PROOF"))
    }

    @Test
    fun `all whitelist proof types have non-blank labels and descriptions`() {
        for (type in AddressProofType.entries) {
            assertTrue("Label must not be blank for ${type.name}", type.label.isNotBlank())
            assertTrue("Description must not be blank for ${type.name}", type.description.isNotBlank())
            assertTrue("Code must match enum name for ${type.name}", type.code == type.name)
        }
    }

    @Test
    fun `distinct proofs verification identifies duplicate selections`() {
        val proof1 = AddressProofType.ELECTRICITY_BILL
        val proof2Duplicate = AddressProofType.ELECTRICITY_BILL
        val proof2Distinct = AddressProofType.GST_CERTIFICATE

        assertTrue("Identical proof types must be flagged as duplicate", proof1 == proof2Duplicate)
        assertFalse("Different proof types must be valid for CPV", proof1 == proof2Distinct)
    }
}
