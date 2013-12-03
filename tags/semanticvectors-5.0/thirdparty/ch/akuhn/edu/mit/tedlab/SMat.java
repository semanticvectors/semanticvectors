/**
 *  @author Adrian Kuhn
 *  @author David Erni   
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

public class SMat {

    public int rows;
    public int cols;
    public int vals; /* Total non-zero entries. */
    public int[] pointr; /* For each col (plus 1), index of first non-zero entry. */
    public int[] rowind; /* For each nz entry, the row index. */
    public double[] value; /* For each nz entry, the value. */

    public SMat(int rows, int cols, int vals) {
        this.rows = rows;
        this.cols = cols;
        this.vals = vals;
        this.pointr = new int[cols + 1];
        this.rowind = new int[vals];
        this.value = new double[vals];
    }
}