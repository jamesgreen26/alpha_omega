package g_mungus.alpha_omega.wrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WrapMathTest {

    private static final int[] PERIODS = {2, 3, 16, 192, 3072};

    @Test
    void canonIsInRangeAndCongruent() {
        for (int p : PERIODS) {
            for (int x = -3 * p - 7; x <= 3 * p + 7; x++) {
                int c = WrapMath.canon(x, p);
                assertTrue(c >= 0 && c < p);
                assertEquals(x, c + WrapMath.lap(x, p) * p);
            }
        }
    }

    @Test
    void minDeltaIsMinimalAndInHalfOpenRange() {
        for (int p : PERIODS) {
            for (int a = -2 * p; a <= 2 * p; a++) {
                for (int b = -p - 3; b <= p + 3; b += 5) {
                    int d = WrapMath.minDelta(a, b, p);
                    assertTrue(d > -p / 2.0 && d <= p / 2, () -> "out of range");
                    assertEquals(WrapMath.canon(a, p), WrapMath.canon(b + d, p));
                    assertEquals(b + d, WrapMath.nearestImage(a, b, p));
                }
            }
        }
    }

    @Test
    void nearestImageIsIdentityWithinHalfPeriod() {
        int p = 3072;
        for (int ref = -5000; ref <= 5000; ref += 37) {
            for (int off = -p / 2 + 1; off <= p / 2; off += 11) {
                assertEquals(ref + off, WrapMath.nearestImage(ref + off, ref, p));
                assertEquals(ref + off, WrapMath.nearestImage(ref + off + 7 * p, ref, p));
                assertEquals(ref + off, WrapMath.nearestImage(ref + off - 3 * p, ref, p));
            }
        }
    }

    @Test
    void doubleNearestImageMatchesIntVersion() {
        int p = 3072;
        for (int a = -7000; a <= 7000; a += 13) {
            for (int ref = -4000; ref <= 4000; ref += 401) {
                assertEquals(WrapMath.nearestImage(a, ref, p), WrapMath.nearestImage((double) a, ref, p), 1e-9);
            }
        }
    }

    @Test
    void doubleNearestImagePreservesFraction() {
        double p = 3072;
        assertEquals(3072.25, WrapMath.nearestImage(0.25, 3070.0, p), 1e-9);
        assertEquals(-0.75, WrapMath.nearestImage(3071.25, 1.0, p), 1e-9);
        assertEquals(12.5, WrapMath.nearestImage(12.5, 12.5, p), 1e-9);
    }
}
