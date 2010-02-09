/**
 *   @author Adrian Kuhn
 *   @author David Erni   
 *             
 *      Copyright (c) 2010 University of Bern
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ch.akuhn.edu.mit.tedlab;

public class SVDRec {
    public int d; /* Dimensionality (rank) */
    public DMat Ut; /*
     * Transpose of left singular vectors. (d by m) The vectors are
     * the rows of Ut.
     */
    public double[] S; /* Array of singular values. (length d) */
    public DMat Vt; /*
     * Transpose of right singular vectors. (d by n) The vectors are
     * the rows of Vt.
     */

    public SVDRec() {
    }

}