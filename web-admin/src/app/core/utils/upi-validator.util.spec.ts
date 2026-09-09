import { validateUpiHandle, KNOWN_UPI_HANDLES } from './upi-validator.util';

describe('validateUpiHandle', () => {
  it('should validate official Google Pay and PhonePe handles', () => {
    const res1 = validateUpiHandle('nandhakumar2536-1@oksbi');
    expect(res1.isValid).toBeTrue();
    expect(res1.isKnownBank).toBeTrue();
    expect(res1.prefix).toBe('nandhakumar2536-1');
    expect(res1.suffix).toBe('oksbi');

    const res2 = validateUpiHandle('restaurant@okhdfcbank');
    expect(res2.isValid).toBeTrue();
    expect(res2.isKnownBank).toBeTrue();

    const res3 = validateUpiHandle('cafe@ybl');
    expect(res3.isValid).toBeTrue();
    expect(res3.isKnownBank).toBeTrue();

    const res4 = validateUpiHandle('shop@paytm');
    expect(res4.isValid).toBeTrue();
    expect(res4.isKnownBank).toBeTrue();
  });

  it('should normalize uppercase input and trim whitespace', () => {
    const res = validateUpiHandle('  NANDHA@OKSBI  ');
    expect(res.isValid).toBeTrue();
    expect(res.normalizedHandle).toBe('nandha@oksbi');
  });

  it('should suggest corrections for common handle typos', () => {
    const res = validateUpiHandle('nandha@okhdfc');
    expect(res.isValid).toBeTrue();
    expect(res.isKnownBank).toBeFalse();
    expect(res.suggestion).toBe('nandha@okhdfcbank');
  });

  it('should reject invalid formats', () => {
    expect(validateUpiHandle('').isValid).toBeFalse();
    expect(validateUpiHandle('   ').isValid).toBeFalse();
    expect(validateUpiHandle('nandhakumar').isValid).toBeFalse();
    expect(validateUpiHandle('a@oksbi').isValid).toBeFalse(); // Prefix too short
    expect(validateUpiHandle('nandha@').isValid).toBeFalse(); // Missing suffix
    expect(validateUpiHandle('nandha @oksbi').isValid).toBeFalse(); // Spaces
    expect(validateUpiHandle('nandha@ok@sbi').isValid).toBeFalse(); // Multiple @
  });
});
