import * as fc from 'fast-check';
import { isPasswordResetSubmissionValid } from './login-page.component';

// --- Extracted pure validation logic (mirrors LoginPageComponent password reset) ---

/**
 * Determines if a password reset submission is valid.
 * Mirrors the component logic: passwordForm uses Validators.minLength(6) on both fields,
 * and submitNewPassword() checks `passwordForm.invalid || passwordMismatch`.
 * passwordMismatch = confirmPassword.length > 0 && newPassword !== confirmPassword
 *
 * A reset is accepted when:
 * - Both passwords are at least 6 characters long (form validity)
 * - Both passwords match (no mismatch)
 */
function isPasswordResetValid(newPassword: string, confirmPassword: string): boolean {
  return isPasswordResetSubmissionValid(newPassword, confirmPassword);
}

// --- Generators ---

/** Generates a password string of at least 6 characters (valid length) */
const validPasswordArb: fc.Arbitrary<string> = fc.string({ minLength: 6, maxLength: 30 });

/** Generates a password string of fewer than 6 characters (too short) */
const shortPasswordArb: fc.Arbitrary<string> = fc.string({ minLength: 0, maxLength: 5 });

/** Generates any arbitrary password string (0 to 30 chars) */
const anyPasswordArb: fc.Arbitrary<string> = fc.string({ minLength: 0, maxLength: 30 });

// --- Tests ---

describe('Login Page Property Tests', () => {

  /**
   * **Validates: Requirements 13.6**
   *
   * Property 16: Password Reset Validation (frontend) - For any pair of password inputs,
   * the system SHALL accept the reset only when both passwords match AND are at least 6
   * characters long. Mismatched or short passwords SHALL be rejected.
   */
  describe('Property 16: Password Reset Validation (frontend)', () => {

    it('should accept reset when both passwords match and are at least 6 characters', () => {
      fc.assert(
        fc.property(
          validPasswordArb,
          (password: string) => {
            // Same password used for both fields — valid scenario
            expect(isPasswordResetValid(password, password)).toBe(true);
          }
        ),
        { numRuns: 20 }
      );
    });

    it('should reject reset when passwords do not match', () => {
      fc.assert(
        fc.property(
          validPasswordArb,
          validPasswordArb,
          (newPassword: string, confirmPassword: string) => {
            // Only test when they're actually different
            fc.pre(newPassword !== confirmPassword);
            expect(isPasswordResetValid(newPassword, confirmPassword)).toBe(false);
          }
        ),
        { numRuns: 20 }
      );
    });

    it('should reject reset when new password is shorter than 6 characters', () => {
      fc.assert(
        fc.property(
          shortPasswordArb,
          anyPasswordArb,
          (newPassword: string, confirmPassword: string) => {
            expect(isPasswordResetValid(newPassword, confirmPassword)).toBe(false);
          }
        ),
        { numRuns: 20 }
      );
    });

    it('should reject reset when confirm password is shorter than 6 characters', () => {
      fc.assert(
        fc.property(
          anyPasswordArb,
          shortPasswordArb,
          (newPassword: string, confirmPassword: string) => {
            expect(isPasswordResetValid(newPassword, confirmPassword)).toBe(false);
          }
        ),
        { numRuns: 20 }
      );
    });

    it('universal property: accepted if and only if both >= 6 chars AND matching', () => {
      fc.assert(
        fc.property(
          anyPasswordArb,
          anyPasswordArb,
          (newPassword: string, confirmPassword: string) => {
            const result = isPasswordResetValid(newPassword, confirmPassword);
            const shouldBeValid = newPassword.length >= 6
              && confirmPassword.length >= 6
              && newPassword === confirmPassword;

            expect(result).toBe(shouldBeValid);
          }
        ),
        { numRuns: 20 }
      );
    });
  });
});
