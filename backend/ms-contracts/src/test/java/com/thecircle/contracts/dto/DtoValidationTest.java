package com.thecircle.contracts.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One rejection case per validated field on the contract / signature / payment
 * DTOs, driven directly through the bean Validator.
 */
class DtoValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static boolean valid(Object dto) {
        return validator.validate(dto).isEmpty();
    }

    @Test
    void contractCreate_accepts_validInput() {
        assertTrue(valid(new ContractCreateRequest("item1", "10", "20", ContractType.SALE,
                new BigDecimal("100.00"), null, "Good condition", null)));
    }

    @Test
    void contractCreate_rejects_blankItemId() {
        assertFalse(valid(new ContractCreateRequest("", "10", "20", ContractType.SALE,
                null, null, null, null)));
    }

    @Test
    void contractCreate_rejects_negativeGuarantee() {
        assertFalse(valid(new ContractCreateRequest("item1", "10", "20", ContractType.RENT,
                null, new BigDecimal("-5.00"), null, null)));
    }

    @Test
    void contractCreate_rejects_conditionsWithAngleBrackets() {
        assertFalse(valid(new ContractCreateRequest("item1", "10", "20", ContractType.SALE,
                null, null, "<script>alert(1)</script>", null)));
    }

    @Test
    void paymentRequest_rejects_nonNumericCard() {
        assertFalse(valid(new PaymentRequestDto("abcd-efgh", "12/30", "123", "Ana")));
    }

    @Test
    void paymentRequest_rejects_badCvc() {
        assertFalse(valid(new PaymentRequestDto("4111111111111111", "12/30", "12", "Ana")));
    }

    @Test
    void paymentRequest_accepts_validCard() {
        assertTrue(valid(new PaymentRequestDto("4111 1111 1111 1111", "12/30", "123", "Ana")));
    }

    @Test
    void signConfirm_rejects_badSessionAndOtp() {
        SignConfirmDto bad = new SignConfirmDto();
        bad.setSessionId("not-a-uuid");
        bad.setOtp("12");
        assertFalse(valid(bad));

        SignConfirmDto ok = new SignConfirmDto();
        ok.setSessionId("11111111-2222-3333-4444-555555555555");
        ok.setOtp("123456");
        assertTrue(valid(ok));
    }

    @Test
    void signer_rejects_badEmailAndOversizedImage() {
        SignerDto bad = new SignerDto();
        bad.setEmail("nope");
        assertFalse(valid(bad));

        SignerDto bigImage = new SignerDto();
        bigImage.setSignatureImageBase64("A".repeat(3_000_000));
        assertFalse(valid(bigImage));
    }
}
