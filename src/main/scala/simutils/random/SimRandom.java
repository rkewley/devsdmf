//******************************************************************************
//
// File:    Random.java
// Package: edu.rit.util
// Unit:    Class edu.rit.util.Random
//
// This Java source file is copyright (C) 2015 by Alan Kaminsky. All rights
// reserved. For further information, contact the author, Alan Kaminsky, at
// ark@cs.rit.edu.
//
// This Java source file is part of the Parallel Java 2 Library ("PJ2"). PJ2 is
// free software; you can redistribute it and/or modify it under the terms of
// the GNU General Public License as published by the Free Software Foundation;
// either version 3 of the License, or (at your option) any later version.
//
// PJ2 is distributed in the hope that it will be useful, but WITHOUT ANY
// WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
// A PARTICULAR PURPOSE. See the GNU General Public License for more details.
//
// A copy of the GNU General Public License is provided in the file gpl.txt. You
// may also obtain a copy of the GNU General Public License on the World Wide
// Web at http://www.gnu.org/licenses/gpl.html.
//
//******************************************************************************

package simutils.random;

import java.io.IOException;
import java.math.BigInteger;

/**
 * Class Random provides a pseudorandom number generator (PRNG) designed for use
 * in parallel programming.
 * <P>
 * Class Random generates random numbers by hashing successive counter
 * values. The seed initializes the counter. The hash function is defined in W.
 * Press et al., <I>Numerical Recipes: The Art of Scientific Computing, Third
 * Edition</I> (Cambridge University Press, 2007), page 352. The hash function
 * applied to the counter value <I>i</I> is:
 * <P>
 * <I>x</I> := 3935559000370003845 * <I>i</I> + 2691343689449507681 (mod 2<SUP>64</SUP>)
 * <BR><I>x</I> := <I>x</I> xor (<I>x</I> right-shift 21)
 * <BR><I>x</I> := <I>x</I> xor (<I>x</I> left-shift 37)
 * <BR><I>x</I> := <I>x</I> xor (<I>x</I> right-shift 4)
 * <BR><I>x</I> := 4768777513237032717 * <I>x</I> (mod 2<SUP>64</SUP>)
 * <BR><I>x</I> := <I>x</I> xor (<I>x</I> left-shift 20)
 * <BR><I>x</I> := <I>x</I> xor (<I>x</I> right-shift 41)
 * <BR><I>x</I> := <I>x</I> xor (<I>x</I> left-shift 5)
 * <BR>Return <I>x</I>
 * <P>
 * (The shift and arithmetic operations are all performed on unsigned 64-bit
 * numbers.)
 * <P>
 * In addition to the usual methods for generating random numbers of various
 * types, class Random provides efficient <I>skip</I> operations. The {@link
 * #skip(long) skip()} method skips the PRNG ahead a certain amount without
 * actually generating the skipped-over random numbers. The {@link #skipTo(long)
 * skipTo()} method puts the PRNG back to its most recently seeded state, then
 * skips the PRNG ahead a certain amount.
 * <P>
 * An instance of class Random can be serialized; for example, to checkpoint the
 * state of the PRNG into a file and restore its state later.
 * This serialization has been removed to minimize debendencies.  RHK
 *
 * @author  Alan Kaminsky
 * @version 20-Mar-2015
 */
public class SimRandom
	{

// Kludge to avert false sharing in multithreaded programs.

	// Padding fields.
	volatile long p0 = 1000L;
	volatile long p1 = 1001L;
	volatile long p2 = 1002L;
	volatile long p3 = 1003L;
	volatile long p4 = 1004L;
	volatile long p5 = 1005L;
	volatile long p6 = 1006L;
	volatile long p7 = 1007L;
	volatile long p8 = 1008L;
	volatile long p9 = 1009L;
	volatile long pa = 1010L;
	volatile long pb = 1011L;
	volatile long pc = 1012L;
	volatile long pd = 1013L;
	volatile long pe = 1014L;
	volatile long pf = 1015L;

	// Method to prevent the JDK from optimizing away the padding fields.
	long preventOptimization()
		{
		return p0 + p1 + p2 + p3 + p4 + p5 + p6 + p7 +
			p8 + p9 + pa + pb + pc + pd + pe + pf;
		}

// Hidden data members.

	// 1/2^64, as a float and as a double.
	private static double D_2_POW_NEG_64 = 1.0/18446744073709551616.0;
	private static float F_2_POW_NEG_64 = 1.0f/18446744073709551616.0f;
	private static double D_2_POW_64 = 18446744073709551616.0;

	// Initial seed and counter for this PRNG.
	private long seed;
	private long counter;

// Exported constructors.

	/**
	 * Construct a new PRNG with the given seed. Any seed value is allowed.
	 *
	 * @param  seed  Seed.
	 */
	public SimRandom
	(long seed)
		{
		setSeed (seed);
		p0 = preventOptimization();
		}

// Exported operations.

	/**
	 * Set this PRNG's seed. Any seed value is allowed.
	 *
	 * @param  seed  Seed.
	 */
	public void setSeed
		(long seed)
		{
		this.seed = hash (seed);
		this.counter = 0L;
		}

	/**
	 * Skip one position ahead in this PRNG's sequence.
	 */
	public void skip()
		{
		++ counter;
		}

	/**
	 * Skip the given number of positions ahead in this PRNG's sequence. If
	 * <TT>skip</TT> &lt; 0, the <TT>skip()</TT> method does nothing.
	 *
	 * @param  skip  Number of positions to skip.
	 */
	public void skip
		(long skip)
		{
		if (skip >= 0L)
			counter += skip;
		}

	/**
	 * Skip to the given position in this PRNG's sequence. If <TT>skip</TT> &lt;
	 * 0, the <TT>skipTo()</TT> method does nothing.
	 * <P>
	 * The <TT>skipTo()</TT> method, in effect, puts this PRNG back to its most
	 * recently seeded state, then skips this PRNG ahead by <TT>skip</TT>
	 * positions.
	 *
	 * @param  skip  Position to which to skip.
	 */
	public void skipTo
		(long skip)
		{
		if (skip >= 0L)
			counter = skip;
		}

	/**
	 * Return a Boolean value from the next pseudorandom value in this PRNG's
	 * sequence. With a probability of 0.5 <TT>true</TT> is returned, with a
	 * probability of 0.5 <TT>false</TT> is returned.
	 *
	 * @return  Boolean value.
	 */
	public boolean nextBoolean()
		{
		// Use the high-order (sign) bit of the 64-bit random value.
		return next() >= 0L;
		}

	/**
	 * Return a byte value from the next pseudorandom value in this PRNG's
	 * sequence. Each value in the range &minus;128 through 127 is returned with
	 * a probability of 1/2<SUP>8</SUP>.
	 *
	 * @return  Byte value in the range &minus;128 through 127 inclusive.
	 */
	public byte nextByte()
		{
		return (byte) next();
		}

	/**
	 * Return an unsigned byte value from the next pseudorandom value in this
	 * PRNG's sequence. Each value in the range 0 through 255 is returned with a
	 * probability of 1/2<SUP>8</SUP>.
	 *
	 * @return  Unsigned byte value (as an <TT>int</TT>) in the range 0 through
	 *          255 inclusive.
	 */
	public int nextUnsignedByte()
		{
		return (int) (next() & 0xFFL);
		}

	/**
	 * Return a character value from the next pseudorandom value in this PRNG's
	 * sequence. Each value in the range <TT>'&#92;u0000'</TT> through
	 * <TT>'&#92;uFFFF'</TT> is returned with a probability of 1/2<SUP>16</SUP>.
	 *
	 * @return  Character value in the range <TT>'&#92;u0000'</TT> through
	 *          <TT>'&#92;uFFFF'</TT> inclusive.
	 */
	public char nextCharacter()
		{
		return (char) next();
		}

	/**
	 * Return a short value from the next pseudorandom value in this PRNG's
	 * sequence. Each value in the range &minus;32768 through 32767 is returned
	 * with a probability of 1/2<SUP>16</SUP>.
	 *
	 * @return  Short value in the range &minus;32768 through 32767 inclusive.
	 */
	public short nextShort()
		{
		return (short) next();
		}

	/**
	 * Return an unsigned short value from the next pseudorandom value in this
	 * PRNG's sequence. Each value in the range 0 through 65535 is returned with
	 * a probability of 1/2<SUP>16</SUP>.
	 *
	 * @return  Unsigned short value (as an <TT>int</TT>) in the range 0 through
	 *          65535 inclusive.
	 */
	public int nextUnsignedShort()
		{
		return (int) (next() & 0xFFFFL);
		}

	/**
	 * Return an integer value from the next pseudorandom value in this PRNG's
	 * sequence. Each value in the range &minus;2147483648 through 2147483647 is
	 * returned with a probability of 1/2<SUP>32</SUP>.
	 *
	 * @return  Integer value in the range &minus;2147483648 through 2147483647
	 *          inclusive.
	 */
	public int nextInteger()
		{
		return (int) next();
		}

	/**
	 * Return an integer value in the given range from the next pseudorandom
	 * value in this PRNG's sequence. Each value in the range 0 through
	 * <I>N</I>&minus;1 is returned with a probability of 1/<I>N</I>.
	 *
	 * @param  n  Range of values to return.
	 *
	 * @return  Integer value in the range 0 through <I>N</I>&minus;1 inclusive.
	 *
	 * @exception  IllegalArgumentException
	 *     (unchecked exception) Thrown if <I>N</I> &le; 0.
	 */
	public int nextInt
		(int n)
		{
		if (n <= 0)
			throw new IllegalArgumentException (String.format
				("Random.nextInt(): n = %d illegal", n));
		return (int) Math.floor (nextDouble()*n);
		}

	/**
	 * Return a long value from the next pseudorandom value in this PRNG's
	 * sequence. Each value in the range &minus;9223372036854775808 through
	 * 9223372036854775807 is returned with a probability of 1/2<SUP>64</SUP>.
	 *
	 * @return  Long value in the range &minus;9223372036854775808 through 
	 *          9223372036854775807 inclusive.
	 */
	public long nextLong()
		{
		return next();
		}

	/**
	 * Return a single precision floating point value from the next
	 * pseudorandom value in this PRNG's sequence. The returned numbers have a
	 * uniform distribution in the range 0.0 (inclusive) to 1.0 (exclusive).
	 *
	 * @return  Float value.
	 */
	public float nextFloat()
		{
		// Next random number is in the range -2^63 .. +2^63 - 1.
		// Divide by 2^64 yielding a number in the range -0.5 .. +0.5.
		// Add 0.5 yielding a number in the range 0.0 .. 1.0.
		return (float)(next())*F_2_POW_NEG_64 + 0.5f;
		}

	/**
	 * Return a double precision floating point value from the next
	 * pseudorandom value in this PRNG's sequence. The returned numbers have a
	 * uniform distribution in the range 0.0 (inclusive) to 1.0 (exclusive).
	 *
	 * @return  Double value.
	 */
	public double nextDouble()
		{
		// Next random number is in the range -2^63 .. +2^63 - 1.
		// Divide by 2^64 yielding a number in the range -0.5 .. +0.5.
		// Add 0.5 yielding a number in the range 0.0 .. 1.0.
		return (double)(next())*D_2_POW_NEG_64 + 0.5;
		}

	/**
	 * Return a big integer value from the next pseudorandom values in this
	 * PRNG's sequence. The returned numbers have a uniform distribution in the
	 * range 0 (inclusive) through 2<SUP><I>n</I></SUP>&minus;1 (inclusive).
	 *
	 * @param  n  Bit size of big integer.
	 *
	 * @return  Big integer.
	 *
	 * @exception  IllegalArgumentException
	 *     (unchecked exception) Thrown if <I>n</I> &lt; 0.
	 */
	public BigInteger nextBigInteger
		(int n)
		{
		// Check preconditions.
		if (n < 0)
			throw new IllegalArgumentException (String.format
				("Random.nextBigInteger(): n = %d illegal", n));

		// Generate random bytes.
		int nbytes = (n + 7) >>> 3;
		byte[] buf = new byte [nbytes];
		boolean eqzero = true;
		long rnd = 0L;
		for (int i = 0; i < nbytes; ++ i)
			{
			if ((i & 7) == 0) rnd = next();
			buf[i] = (byte)rnd;
			rnd >>= 8;
			eqzero &= (buf[i] == 0);
			}

		// Early exit if result is zero.
		if (eqzero) return BigInteger.ZERO;

		// Mask off unneeded bits in most significant byte.
		int rembits = n & 7;
		if (rembits > 0)
			buf[0] &= (1 << rembits) - 1;

		// Return big integer.
		return new BigInteger (1, buf);
		}

		private double nextNextGaussian;
		private boolean haveNextNextGaussian = false;

		/**
		 * Returns the next pseudorandom, Gaussian ("normally") distributed
		 * {@code double} value with mean {@code 0.0} and standard
		 * deviation {@code 1.0} from this random number generator's sequence.
		 * <p>
		 * The general contract of {@code nextGaussian} is that one
		 * {@code double} value, chosen from (approximately) the usual
		 * normal distribution with mean {@code 0.0} and standard deviation
		 * {@code 1.0}, is pseudorandomly generated and returned.
		 *
		 * <p>The method {@code nextGaussian} is implemented by class
		 * {@code Random} as if by a threadsafe version of the following:
		 *  <pre> {@code
		 * private double nextNextGaussian;
		 * private boolean haveNextNextGaussian = false;
		 *
		 * public double nextGaussian() {
		 *   if (haveNextNextGaussian) {
		 *     haveNextNextGaussian = false;
		 *     return nextNextGaussian;
		 *   } else {
		 *     double v1, v2, s;
		 *     do {
		 *       v1 = 2 * nextDouble() - 1;   // between -1.0 and 1.0
		 *       v2 = 2 * nextDouble() - 1;   // between -1.0 and 1.0
		 *       s = v1 * v1 + v2 * v2;
		 *     } while (s >= 1 || s == 0);
		 *     double multiplier = StrictMath.sqrt(-2 * StrictMath.log(s)/s);
		 *     nextNextGaussian = v2 * multiplier;
		 *     haveNextNextGaussian = true;
		 *     return v1 * multiplier;
		 *   }
		 * }}</pre>
		 * This uses the <i>polar method</i> of G. E. P. Box, M. E. Muller, and
		 * G. Marsaglia, as described by Donald E. Knuth in <i>The Art of
		 * Computer Programming</i>, Volume 3: <i>Seminumerical Algorithms</i>,
		 * section 3.4.1, subsection C, algorithm P. Note that it generates two
		 * independent values at the cost of only one call to {@code StrictMath.log}
		 * and one call to {@code StrictMath.sqrt}.
		 *
		 * @return the next pseudorandom, Gaussian ("normally") distributed
		 *         {@code double} value with mean {@code 0.0} and
		 *         standard deviation {@code 1.0} from this random number
		 *         generator's sequence
		 */
		synchronized public double nextGaussian() {
			// See Knuth, ACP, Section 3.4.1 Algorithm C.
			if (haveNextNextGaussian) {
				haveNextNextGaussian = false;
				return nextNextGaussian;
			} else {
				double v1, v2, s;
				do {
					v1 = 2 * nextDouble() - 1; // between -1 and 1
					v2 = 2 * nextDouble() - 1; // between -1 and 1
					s = v1 * v1 + v2 * v2;
				} while (s >= 1 || s == 0);
				double multiplier = StrictMath.sqrt(-2 * StrictMath.log(s)/s);
				nextNextGaussian = v2 * multiplier;
				haveNextNextGaussian = true;
				return v1 * multiplier;
			}
		}


// Hidden operations.

	/**
	 * Return the next 64-bit pseudorandom value in this PRNG's sequence.
	 *
	 * @return  Pseudorandom value.
	 */
	private long next()
		{
		++ counter;
		return hash (seed + counter);
		}

	/**
	 * Return the hash of the given value.
	 */
	private static long hash
		(long x)
		{
		x = 3935559000370003845L * x + 2691343689449507681L;
		x = x ^ (x >>> 21);
		x = x ^ (x << 37);
		x = x ^ (x >>> 4);
		x = 4768777513237032717L * x;
		x = x ^ (x << 20);
		x = x ^ (x >>> 41);
		x = x ^ (x << 5);
		return x;
		}

	}
