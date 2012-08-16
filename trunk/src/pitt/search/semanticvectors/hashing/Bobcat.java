package pitt.search.semanticvectors.hashing;

/**
 * This class has only two public static methods that return a hash code for a
 * given input string. One method returns the hash code as a hexadecimal string
 * and the other as a long.
 * <p>
 * The hash algorithm is called "Bobcat"; it is taken from an exercise in a book
 * about information security [1] by Mark Stamp. We would like to thank Mark for
 * allowing use to use and integrate the Bobcat algorithm in the Semantic
 * Vectors project.
 * <p>
 * By deterministically computing a hash code from a term's string
 * representation and using it as a seed value for Java's random number
 * generator, we can guarantee that the "random" vector for a particular term
 * can consistently be regenerated. The main two advantages are that term
 * vectors do not have to be cached, saving memory, and term vectors do not have
 * to be distributed in distributed experiments, saving bandwidth and increasing
 * flexibility and ease of use.
 * <p>
 * Bobcat was chosen for this purpose because of its speed and the fact that it
 * produces hash codes of 48 bits length, which matches the size of Java's
 * random number generator. It should be noted though that Bobcat is not
 * intended to be cryptographically safe, although, for example, it does not
 * produce any collisions for a Medline corpus with dozens of millions of terms.
 * <p>
 * <b>[1]</b> M. Stamp, Information security: principles and practice, 2nd ed. 
 * Hoboken, NJ: Wiley, 2011.
 */
public class Bobcat {

    // 2 S-boxes, each mapping 4 bits to 16 bits
    static final int sbox[][] = { { 0xd131, 0x0ba6, 0x98df, 0xb5ac, 0x2ffd, 0x72db, 0xd01a, 0xdfb7, 0xb8e1, 0xafed, 0x6a26, 0x7e96, 0xba7c, 0x9045, 0xf12c, 0x7f99 },
	                          { 0x24a1, 0x9947, 0xb391, 0x6cf7, 0x0801, 0xf2e2, 0x858e, 0xfc16, 0x6369, 0x20d8, 0x7157, 0x4e69, 0xa458, 0xfea3, 0xf493, 0x3d7e } };

    // Private constructor. This class is not meant to be instantiated.
    private Bobcat() {
    }

    /**
     * Computes the Bobcat hash value for a given string and returns the result
     * as a string containing a hexadecimal number.
     * 
     * @param text a String with the text to be hashed
     * @return a string with the hash as a hexadecimal number
     */
    public static String asString(String text) {
	int abc[] = computeBobcat(text);
	return String.format("%04x%04x%04x", abc[0], abc[1], abc[2]);
    }

    /**
     * Computes the Bobcat hash value for a given string and returns the result
     * as a long. Only the lower 48 bits are used.
     * 
     * @param text a String with the text to be hashed
     * @return a long with the hash value
     */
    public static long asLong(String text) {
	int abc[] = computeBobcat(text);
	String s = String.format("%16s%16s%16s", Integer.toBinaryString(abc[0]), Integer.toBinaryString(abc[1]), Integer.toBinaryString(abc[2])).replaceAll(" ", "0");
	return Long.parseLong(s, 2);
    }

    // Internal method to compute a bobcat hash from a string.
    private static int[] computeBobcat(String text) {
	int x[];
	int iLength = text.length();
	int iResidue = iLength % 8;

	if (iResidue != 0)
	    x = new int[iLength + (8 - iResidue)];
	else
	    x = new int[iLength];

	for (int i = 0; i < iLength; ++i)
	    x[i] = (int) (text.charAt(i));
	if (iResidue != 0)
	    for (int i = iLength; i < iLength + (8 - iResidue); ++i)
		x[i] = 0;

	// initial constants a,b,c
	int abc[] = { 0xface, 0xe961, 0x041d };

	// number of 128-bit blocks
	int len = x.length / 8;

	for (int i = 0; i < len; ++i) {
	    int block[] = { x[0 + i * 8], x[1 + i * 8], x[2 + i * 8], x[3 + i * 8], x[4 + i * 8], x[5 + i * 8], x[6 + i * 8], x[7 + i * 8] };
	    // bobcat() does one outer round
	    bobcat(abc, block);
	}

	return abc;
    }

    // compression function (one outer round)
    private static void bobcat(int abc[], int x[]) {
	int old[] = { abc[0], abc[1], abc[2] };

	F(abc, 5, x);
	keySchedule(x);
	F(abc, 7, x);
	keySchedule(x);
	F(abc, 9, x);
	feedforward(abc, old);
    }

    // inner round functions f_{m,i}
    private static void f(int abc[], int zero, int one, int two, int x, int m) {
	int aa, bb, cc;

	aa = abc[zero];
	bb = abc[one];
	cc = abc[two];

	cc = (cc ^ x) & 0xffff;
	aa = (aa - (sbox[0][cc & 0xf] ^ sbox[1][(cc >> 8) & 0xf])) & 0xffff;
	bb = (bb + (sbox[1][(cc >> 4) & 0xf] ^ sbox[0][(cc >> 12) & 0xf])) & 0xffff;
	bb = (bb * m) & 0xffff;

	abc[zero] = aa;
	abc[one] = bb;
	abc[two] = cc;
    }

    // outer round functions F_m
    private static void F(int abc[], int m, int x[]) {
	f(abc, 0, 1, 2, x[0], m);// a,b,c
	f(abc, 1, 2, 0, x[1], m);// b,c,a
	f(abc, 2, 0, 1, x[2], m);// c,a,b
	f(abc, 0, 1, 2, x[3], m);// a,b,c
	f(abc, 1, 2, 0, x[4], m);// b,c,a
	f(abc, 2, 0, 1, x[5], m);// c,a,b
	f(abc, 0, 1, 2, x[6], m);// a,b,c
	f(abc, 1, 2, 0, x[7], m);// b,c,a
    }

    // "key" schedule
    private static void keySchedule(int x[]) {
	x[0] = (x[0] - (x[7] ^ 0xa5a5a5a5)) & 0xffff;
	x[1] = (x[1] ^ x[0]) & 0xffff;
	x[2] = (x[2] + x[1]) & 0xffff;
	x[3] = (x[3] - (x[2] ^ ((~x[1]) << 5))) & 0xffff;
	x[4] = (x[4] ^ x[3]) & 0xffff;
	x[5] = (x[5] + x[4]) & 0xffff;
	x[6] = (x[6] - (x[5] ^ ((~x[4]) >> 6))) & 0xffff;
	x[7] = (x[7] ^ x[6]) & 0xffff;
	x[0] = (x[0] + x[7]) & 0xffff;
	x[1] = (x[1] - (x[0] ^ ((~x[7]) << 5))) & 0xffff;
	x[2] = (x[2] ^ x[1]) & 0xffff;
	x[3] = (x[3] + x[2]) & 0xffff;
	x[4] = (x[4] - (x[3] ^ ((~x[2]) >> 6))) & 0xffff;
	x[5] = (x[5] ^ x[4]) & 0xffff;
	x[6] = (x[6] + x[5]) & 0xffff;
	x[7] = (x[7] - (x[6] ^ 0x235689bd)) & 0xffff;
    }

    // add in the old values to produce new a,b,c
    private static void feedforward(int abc[], int old[]) {
	abc[0] = (abc[0] ^ old[0]) & 0xffff;
	abc[1] = (abc[1] - old[1]) & 0xffff;
	abc[2] = (abc[2] + old[2]) & 0xffff;
    }
}
