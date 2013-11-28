/**
   Copyright (c) 2007 and ongoing, University of Pittsburgh
   and the SemanticVectors AUTHORS.

   All rights reserved.

   Redistribution and use in source and binary forms, with or without
   modification, are permitted provided that the following conditions are
   met:

 * Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.

 * Redistributions in binary form must reproduce the above
   copyright notice, this list of conditions and the following
   disclaimer in the documentation and/or other materials provided
   with the distribution.

 * Neither the name of the University of Pittsburgh nor the names
   of its contributors may be used to endorse or promote products
   derived from this software without specific prior written
   permission.

   THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
   "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
   LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
   A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
   CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
   EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
   PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
   PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
   LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
   NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
   SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
**/

package pitt.util.vectors;

import java.awt.Dimension;
import java.awt.Graphics;

import javax.swing.JFrame;
import javax.swing.JPanel;

import pitt.search.semanticvectors.ObjectVector;
import pitt.search.semanticvectors.vectors.RealVector;

/**
   Class for plotting 2d vectors as a Swing component on your screen.

   Also contains a very grubby implementation of a printout in TeX
   readable format, which can be accessed by setting PRINT_TEX_OUTPUT=true
   internally.
 */
public class Plot2dVectors extends JPanel {
  ObjectVector[] vectors;
  static final int scale = 500;
  static final int pad = 50;
  static final int comp1 = 1;
  static final int comp2 = 2;
  static final int maxplot = 50;
  // Setting this to true will make the plotter output TeX source to
  // your console.
  public final boolean PRINT_TEX_OUTPUT = false;

  public Plot2dVectors (ObjectVector[] vectors) {
    System.err.println("Constructing plotter ...");
    this.vectors = vectors;
    this.setSize(new Dimension(scale + 2*pad, scale + 2*pad));
  }

  @Override
  public void paintComponent(Graphics g) {
    int c1, c2;
    float min1, max1, min2, max2;
    min1 = min2 = 100;
    max1 = max2 = -100;
    // Compute max and min.
    for (int i = 0; i < vectors.length; ++i) {
      RealVector realVector = (RealVector) vectors[i].getVector();
      float[] tmpVec = realVector.getCoordinates();
      if (tmpVec[comp1] < min1) min1 = tmpVec[comp1];
      if (tmpVec[comp2] < min2) min2 = tmpVec[comp2];
      if (tmpVec[comp1] > max1) max1 = tmpVec[comp1];
      if (tmpVec[comp2] > max2) max2 = tmpVec[comp2];
      if (i > maxplot) {
        break;
      }
    }

    System.err.println("Painting component ...");
    if (PRINT_TEX_OUTPUT) {
      int len = scale + pad;
      System.out.println("\\begin{figure}[t]");
      System.out.println("\\begin{center}");
      System.out.println("\\footnotesize");
      System.out.println("\\setlength{\\unitlength}{.55pt}");
      System.out.println();
      System.out.println("\\begin{picture}(" + len + "," + len + ")");
      System.out.println("\\put(0,0){\\framebox(" + len + "," + len + "){}}");
    }

    for (int i = 0; i < vectors.length; ++i) {
      RealVector realVector = (RealVector) vectors[i].getVector();
      float[] tmpVec = realVector.getCoordinates();
      c1 = (pad/2) + Math.round(scale*(tmpVec[comp1]-min1) / (max1-min1));
      c2 = (pad/2) + Math.round(scale*(tmpVec[comp2]-min2) / (max2-min2));

      g.drawString(vectors[i].getObject().toString(), c1, c2);
      if( i > maxplot ){
        break;
      }

      if (PRINT_TEX_OUTPUT) {
        System.out.println("\\put(" + c1 + "," + c2 + ")" + "{\\makebox(0,0){"
                           + vectors[i].getObject().toString() + "}}");

      }
    }

    if (PRINT_TEX_OUTPUT) {
      System.out.println("\\end{picture}");
      System.out.println("\\caption{ADD CAPTION}");
      System.out.println("\\label{ADD LABEL}");
      System.out.println("\\end{center}");
      System.out.println("\\end{figure}");
    }

    System.err.println("Finished painting component ...");
  }

  public void createAndShowGUI() {
    // Create and set up the window.
    JFrame frame = new JFrame("Term Vector Plotter");
    frame.setSize(new Dimension(scale+2*pad, scale+2*pad));
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    System.err.println("Trying to set content pane ...");
    frame.setContentPane(this);
    // Display the window.
    frame.setVisible(true);
  }
}
